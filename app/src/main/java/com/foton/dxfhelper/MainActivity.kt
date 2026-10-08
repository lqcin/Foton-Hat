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
import android.view.Gravity
import android.view.View
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
    private lateinit var dxfView: DxfView
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
    private var completed = mutableSetOf<String>()
    private lateinit var completionStore: CompletionStore
    private val executor = Executors.newSingleThreadExecutor()

    private val openDxf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            getSharedPreferences("dxf_main", MODE_PRIVATE).edit()
                .putString("active_uri", uri.toString())
                .apply()
            loadDxf(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        completionStore = CompletionStore(this)
        buildUi()
        handleIntent(intent)

        val saved = getSharedPreferences("dxf_main", MODE_PRIVATE).getString("active_uri", null)
        if (saved != null) loadDxf(Uri.parse(saved))
        else status.text = "Önce proje DXF dosyasını seç."
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
        if (intent?.getBooleanExtra(EXTRA_FROM_ROBOT, false) == true) {
            status.text = "Robot uygulamasındaki Boru Kesit No alanından geldin. Hattı seç ve ONAYLA."
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(11, 20, 32))
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val selectButton = button("DXF SEÇ") { openDxf.launch(arrayOf("*/*")) }
        val accessibilityButton = button("ROBOT ERİŞİMİ") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        val resetView = button("GÖRÜNÜMÜ TOPARLA") { dxfView.resetView() }
        top.addView(selectButton)
        top.addView(accessibilityButton)
        top.addView(resetView)

        fileLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(dp(14), 0, dp(10), 0)
            text = "DXF seçilmedi"
        }
        top.addView(fileLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        countLabel = TextView(this).apply {
            setTextColor(Color.rgb(80, 220, 140)); textSize = 14f
            text = "0 / 0 tamamlandı"
        }
        top.addView(countLabel)
        root.addView(top)

        status = TextView(this).apply {
            setTextColor(Color.rgb(188, 207, 226)); textSize = 13f
            setPadding(0, dp(5), 0, dp(5))
        }
        root.addView(status)

        dxfView = DxfView(this).apply {
            onSegmentSelected = { _, info ->
                currentInfo = info
                refreshSelection()
            }
        }
        root.addView(dxfView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        selectionLabel = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 16f
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

    private fun loadDxf(uri: Uri) {
        activeUri = uri
        activeName = displayName(uri)
        fileLabel.text = activeName
        status.text = "DXF okunuyor..."
        currentInfo = null
        refreshSelection()

        executor.execute {
            val result = runCatching {
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "DXF açılamadı" }
                    DxfParser.parse(input)
                }
            }
            runOnUiThread {
                result.onSuccess { drawing ->
                    completed = completionStore.load(uri)
                    dxfView.drawing = drawing
                    dxfView.completedIds = completed
                    countLabel.text = "${completed.size} / ${drawing.segments.size} tamamlandı"
                    status.text = "${drawing.segments.size} hat, ${drawing.circles.size} baca, ${drawing.texts.size} yazı yüklendi. Hatta dokun."
                }.onFailure { err ->
                    status.text = "DXF okunamadı: ${err.message}"
                    Toast.makeText(this, "DXF okunamadı", Toast.LENGTH_LONG).show()
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
                .setTitle("Baca adı bulunamadı")
                .setMessage("Hattın bir ucundaki baca adı otomatik bulunamadı. Bu hattı tamamlandı işaretlemek ister misin?")
                .setNegativeButton("İptal", null)
                .setPositiveButton("Yine de işaretle") { _, _ -> saveAndTransfer(uri, info) }
                .show()
        } else saveAndTransfer(uri, info)
    }

    private fun saveAndTransfer(uri: Uri, info: SelectedLineInfo) {
        completed.add(info.segment.id)
        completionStore.save(uri, completed)
        dxfView.completedIds = completed
        countLabel.text = "${completed.size} / ${dxfView.drawing?.segments?.size ?: 0} tamamlandı"
        undoButton.isEnabled = true

        val payload = RobotSelection(
            sectionNo = info.sectionNo,
            startManhole = info.startName,
            endManhole = info.endName,
            lengthMeters = info.lengthMeters
        )
        val service = RobotAssistAccessibilityService.instance
        if (service != null && service.hasRobotTarget()) {
            service.sendSelectionToRobot(payload)
            status.text = "${info.sectionNo} tamamlandı. Robot uygulamasına aktarılıyor..."
        } else {
            copyFallback(payload)
            status.text = "${info.sectionNo} tamamlandı. Robot erişimi aktif değil; kesit no panoya kopyalandı."
        }
    }

    private fun toggleCompleted(done: Boolean) {
        val uri = activeUri ?: return
        val id = currentInfo?.segment?.id ?: return
        if (done) completed.add(id) else completed.remove(id)
        completionStore.save(uri, completed)
        dxfView.completedIds = completed
        countLabel.text = "${completed.size} / ${dxfView.drawing?.segments?.size ?: 0} tamamlandı"
        refreshSelection()
    }

    private fun copyFallback(payload: RobotSelection) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Boru Kesit No", payload.sectionNo))
        Toast.makeText(this, "${payload.sectionNo} panoya kopyalandı", Toast.LENGTH_SHORT).show()
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: "proje.dxf"
        }
        return uri.lastPathSegment ?: "proje.dxf"
    }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
        minHeight = dp(44)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_FROM_ROBOT = "from_robot"
    }
}
