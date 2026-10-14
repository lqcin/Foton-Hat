package com.foton.dxfhelper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var mapView: DxfView
    private lateinit var status: TextView
    private lateinit var fileLabel: TextView
    private lateinit var selectionLabel: TextView
    private lateinit var reverseButton: Button
    private lateinit var confirmButton: Button
    private lateinit var undoButton: Button
    private lateinit var countLabel: TextView

    private var activeUri: Uri? = null
    private var activeName: String = ""
    private var currentInfo: SelectedLineInfo? = null
    private var activeDrawing: DxfDrawing? = null
    private var completed = mutableSetOf<String>()
    private lateinit var completionStore: CompletionStore
    private var launchedFromIme: Boolean = false
    private val executor = Executors.newSingleThreadExecutor()

    private val openMap = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            getSharedPreferences("fotonmap_main", MODE_PRIVATE).edit()
                .putString("active_uri", uri.toString())
                .apply()
            loadFotonMap(uri)
        }
    }

    private val createPdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) exportPdf(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        completionStore = CompletionStore(this)
        buildUi()
        handleIntent(intent)

        val saved = getSharedPreferences("fotonmap_main", MODE_PRIVATE).getString("active_uri", null)
        if (saved != null) loadFotonMap(Uri.parse(saved))
        else status.text = "Önce AutoCAD'den üretilmiş .fotonmap dosyasını seç."
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        val requestedByIntent = intent?.getBooleanExtra(EXTRA_FROM_IME, false) == true
        val requestedByStore = ImeSelectionStore.isSelectionRequestActive(this)

        if (requestedByIntent || requestedByStore) {
            launchedFromIme = true
            status.text = "FOTON Klavye için hat seçimi. Hattı seç ve ONAYLA; Boru Kesit No Robot alanına yazılacak."
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(11, 20, 32))
            setPadding(dp(10), dp(7), dp(10), dp(7))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(button("FOTONMAP AÇ") { openMap.launch(arrayOf("*/*")) })
        top.addView(button("PDF OLUŞTUR") { requestPdf() })
        top.addView(button("KLAVYEYİ ETKİNLEŞTİR") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        top.addView(button("KLAVYE SEÇ") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        top.addView(button("−") { mapView.zoomOut() })
        top.addView(button("+") { mapView.zoomIn() })
        top.addView(button("SIĞDIR") { mapView.resetView() })

        fileLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(12), 0, dp(8), 0)
            text = "FOTONMAP seçilmedi"
        }
        top.addView(fileLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        countLabel = TextView(this).apply {
            setTextColor(Color.rgb(80, 220, 140))
            textSize = 14f
            text = "0 / 0 tamamlandı"
        }
        top.addView(countLabel)
        root.addView(top)

        status = TextView(this).apply {
            setTextColor(Color.rgb(188, 207, 226))
            textSize = 13f
            setPadding(0, dp(5), 0, dp(5))
        }
        root.addView(status)

        mapView = DxfView(this).apply {
            onSegmentSelected = { _, info ->
                currentInfo = info
                refreshSelection()
            }
        }
        root.addView(mapView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        selectionLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            text = "Bir hatta dokun"
        }
        bottom.addView(selectionLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        reverseButton = button("TERS ÇEVİR") {
            currentInfo = currentInfo?.reversedCopy()
            refreshSelection()
        }.apply { isEnabled = false }
        undoButton = button("İŞARETİ KALDIR") { toggleCompleted(false) }.apply { isEnabled = false }
        confirmButton = button("ONAYLA") { confirmSelection() }.apply { isEnabled = false }
        bottom.addView(reverseButton)
        bottom.addView(undoButton)
        bottom.addView(confirmButton)
        root.addView(bottom)

        setContentView(root)
    }

    private fun loadFotonMap(uri: Uri) {
        activeUri = uri
        activeName = displayName(uri)
        fileLabel.text = activeName
        status.text = "FOTONMAP okunuyor..."
        currentInfo = null
        activeDrawing = null
        refreshSelection()

        executor.execute {
            val result = runCatching {
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "FOTONMAP açılamadı" }
                    FotonMapParser.parse(input)
                }
            }
            runOnUiThread {
                result.onSuccess { drawing ->
                    activeDrawing = drawing
                    completed = completionStore.load(uri)
                    // Haritada artık bulunmayan eski id'leri temizle.
                    val valid = drawing.segments.mapTo(hashSetOf()) { it.id }
                    completed.retainAll(valid)
                    completionStore.save(uri, completed)
                    mapView.drawing = drawing
                    mapView.completedIds = completed
                    updateCount()
                    val named = drawing.segments.count { it.fromName.isNotBlank() && it.toName.isNotBlank() }
                    status.text = "${drawing.segments.size} hat yüklendi • $named hattın iki baca adı hazır • hatta dokun."
                }.onFailure { err ->
                    status.text = "FOTONMAP okunamadı: ${err.message}"
                    Toast.makeText(this, "FOTONMAP okunamadı", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun refreshSelection() {
        val info = currentInfo
        if (info == null) {
            selectionLabel.text = "Bir hatta dokun"
            reverseButton.isEnabled = false
            confirmButton.isEnabled = false
            undoButton.isEnabled = false
            return
        }
        val length = String.format(Locale.US, "%.2f", info.lengthMeters).replace('.', ',')
        selectionLabel.text = "${info.sectionNo}   •   $length m"
        reverseButton.isEnabled = true
        confirmButton.isEnabled = true
        undoButton.isEnabled = completed.contains(info.segment.id)
    }

    private fun confirmSelection() {
        val uri = activeUri ?: return
        val info = currentInfo ?: return
        if (info.startName == "?" || info.endName == "?") {
            AlertDialog.Builder(this)
                .setTitle("Baca adı eksik")
                .setMessage("FOTONMAP bu hattın iki baca adından en az birini içermiyor. Yine de tamamlandı işaretlensin mi?")
                .setNegativeButton("İptal", null)
                .setPositiveButton("İşaretle") { _, _ -> saveAndTransfer(uri, info) }
                .show()
        } else saveAndTransfer(uri, info)
    }

    private fun saveAndTransfer(uri: Uri, info: SelectedLineInfo) {
        completed.add(info.segment.id)
        completionStore.save(uri, completed)
        mapView.completedIds = completed
        updateCount()
        undoButton.isEnabled = true

        val payload = RobotSelection(
            sectionNo = info.sectionNo,
            startManhole = info.startName,
            endManhole = info.endName,
            lengthMeters = info.lengthMeters
        )
        val returnToIme = launchedFromIme || ImeSelectionStore.isSelectionRequestActive(this)

        if (returnToIme) {
            // Seçimi kalıcı olarak sakla. Intent extra kaybolsa bile
            // selection_request_active sayesinde bu blok kesin calisir.
            // Harita açıldığında Robot'un InputConnection'i
            // geçici olarak düşebilir; bu nedenle değeri asla zaman aşımıyla silmiyoruz.
            ImeSelectionStore.save(this, payload.sectionNo)
            status.text = "${info.sectionNo} seçildi. Robot alanına dönülüyor..."
            finish()
            overridePendingTransition(0, 0)

            // Activity kapandıktan sonra IME'ye haber ver. Robot alanı hâlâ odaktaysa
            // otomatik yazar; değilse seçim klavyede bekler.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                FotonInputMethodService.instance?.notifySelectionReady(payload.sectionNo)
            }, 250)
        } else {
            copyFallback(payload)
            status.text = "${info.sectionNo} yeşil işaretlendi. Kesit no panoya kopyalandı."
        }
    }

    private fun toggleCompleted(done: Boolean) {
        val uri = activeUri ?: return
        val id = currentInfo?.segment?.id ?: return
        if (done) completed.add(id) else completed.remove(id)
        completionStore.save(uri, completed)
        mapView.completedIds = completed
        updateCount()
        refreshSelection()
    }

    private fun updateCount() {
        countLabel.text = "${completed.size} / ${activeDrawing?.segments?.size ?: 0} tamamlandı"
    }

    private fun requestPdf() {
        if (activeDrawing == null) {
            Toast.makeText(this, "Önce FOTONMAP aç", Toast.LENGTH_SHORT).show()
            return
        }
        val clean = activeName.substringBeforeLast('.').ifBlank { "FOTONMAP" }
            .replace(Regex("[^A-Za-z0-9ÇĞİÖŞÜçğıöşü_-]+"), "_")
        createPdf.launch("${clean}_durum_plani.pdf")
    }

    private fun exportPdf(uri: Uri) {
        val drawing = activeDrawing ?: return
        val snapshot = completed.toSet()
        status.text = "PDF hazırlanıyor..."
        executor.execute {
            val result = runCatching {
                contentResolver.openOutputStream(uri).use { output ->
                    requireNotNull(output) { "PDF dosyası açılamadı" }
                    MapPdfExporter.write(output, drawing, snapshot, activeName)
                }
            }
            runOnUiThread {
                result.onSuccess {
                    status.text = "PDF oluşturuldu: ${activeName.substringBeforeLast('.')} durum planı"
                    Toast.makeText(this, "PDF oluşturuldu", Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    status.text = "PDF oluşturulamadı: ${err.message}"
                    Toast.makeText(this, "PDF oluşturulamadı", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun copyFallback(payload: RobotSelection) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Boru Kesit No", payload.sectionNo))
        Toast.makeText(this, "${payload.sectionNo} panoya kopyalandı", Toast.LENGTH_SHORT).show()
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: "proje.fotonmap"
        }
        return uri.lastPathSegment ?: "proje.fotonmap"
    }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
        minHeight = dp(44)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_FROM_IME = "from_ime"
        // Eski accessibility sinifi repoda kalsa bile derleme uyumlu kalsin.
        const val EXTRA_FROM_ROBOT = "from_robot_legacy"
    }
}
