package com.foton.dxfhelper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.lang.ref.WeakReference

/**
 * FOTON klavyesi.
 *
 * Kritik kural:
 * Harita Activity'si acilinca Robot Kontrol Sistemi'ndeki aktif editorun
 * InputConnection'i gecici olarak kaybolabilir. Bu nedenle secilen hat
 * SharedPreferences'ta "pending" olarak tutulur ve ASLA zaman asimiyla silinmez.
 *
 * Akis:
 * 1) Robot'ta Boru Kesit No alanina dokun.
 * 2) FOTON Klavye -> HARITADAN HAT SEC.
 * 3) Hatti sec -> ONAYLA.
 * 4) Robot'a donulunce alan odagi korunmussa otomatik yazilir.
 * 5) Odak korunmadiysa Boru Kesit No alanina tekrar dokun.
 *    FOTON Klavye bekleyen secimi otomatik yazar.
 * 6) Gerekirse "SECIMI YAZ" dugmesine bas.
 */
class FotonInputMethodService : InputMethodService() {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingText: String? = null
    private var retryToken = 0
    private var pendingLabel: TextView? = null
    private var writeButton: Button? = null

    override fun onCreate() {
        super.onCreate()
        ref = WeakReference(this)
        pendingText = ImeSelectionStore.peek(this)
    }

    override fun onDestroy() {
        if (ref?.get() === this) ref = null
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 27, 42))
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "FOTON KLAVYE"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        pendingLabel = TextView(this).apply {
            setTextColor(Color.rgb(102, 230, 160))
            textSize = 14f
            gravity = Gravity.END
        }
        top.addView(pendingLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(top)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(keyButton("HARİTADAN HAT SEÇ") {
            openMapPicker()
        }, LinearLayout.LayoutParams(0, dp(54), 1.45f))

        writeButton = keyButton("SEÇİMİ YAZ") {
            loadPending()
            if (!tryCommitNow(showFailure = true)) {
                Toast.makeText(
                    this,
                    "Boru Kesit No alanına tekrar dokunup 'SEÇİMİ YAZ' de.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }.also {
            row.addView(it, LinearLayout.LayoutParams(0, dp(54), 1.0f))
        }

        row.addView(keyButton("⌫") {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }, LinearLayout.LayoutParams(dp(66), dp(54)))

        row.addView(keyButton("NORMAL KLAVYE") {
            val switched = runCatching { switchToNextInputMethod(false) }.getOrDefault(false)
            if (!switched) {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showInputMethodPicker()
            }
        }, LinearLayout.LayoutParams(dp(135), dp(54)))

        root.addView(row)

        val hint = TextView(this).apply {
            text = "Hat seçildikten sonra otomatik yazılmazsa Boru Kesit No alanına tekrar dokun. Seçim kaybolmaz."
            setTextColor(Color.rgb(177, 198, 220))
            textSize = 12f
            setPadding(0, dp(4), 0, 0)
        }
        root.addView(hint)

        loadPending()
        refreshPendingUi()
        scheduleCommit()
        return root
    }

    override fun onStartInput(
        attribute: android.view.inputmethod.EditorInfo?,
        restarting: Boolean
    ) {
        super.onStartInput(attribute, restarting)
        loadPending()
        scheduleCommit()
    }

    override fun onStartInputView(
        info: android.view.inputmethod.EditorInfo?,
        restarting: Boolean
    ) {
        super.onStartInputView(info, restarting)
        loadPending()
        refreshPendingUi()
        scheduleCommit()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        loadPending()
        refreshPendingUi()
        scheduleCommit()
    }

    /**
     * Harita secimi tamamlandiginda cagrilir.
     * Deger kalici store'a yazilir. Robot editoru hemen geri gelmezse
     * pending olarak beklemeye devam eder.
     */
    fun notifySelectionReady(sectionNo: String) {
        if (sectionNo.isBlank()) return
        pendingText = sectionNo
        ImeSelectionStore.save(this, sectionNo)
        refreshPendingUi()

        // Robot activity'sinin yeniden one gelmesi icin zaman tani.
        retryToken++
        val token = retryToken
        listOf(300L, 650L, 1100L, 1800L, 2800L, 4200L).forEach { delay ->
            handler.postDelayed({ tryCommit(token) }, delay)
        }

        // Klavye penceresi kapanmissa tekrar gostermeyi dene.
        handler.postDelayed({
            runCatching { requestShowSelf(0) }
        }, 500L)

        // Secimi SILME. Basarisizsa kullanici alana tekrar dokununca yazilacak.
        handler.postDelayed({
            if (token == retryToken && pendingText != null) {
                refreshPendingUi()
            }
        }, 5000L)
    }

    private fun loadPending() {
        val stored = ImeSelectionStore.peek(this)
        if (!stored.isNullOrBlank()) pendingText = stored
        refreshPendingUi()
    }

    private fun refreshPendingUi() {
        val text = pendingText ?: ImeSelectionStore.peek(this)
        pendingLabel?.text = if (text.isNullOrBlank()) "Seçim yok" else "Bekleyen: $text"
        writeButton?.isEnabled = !text.isNullOrBlank()
    }

    private fun scheduleCommit() {
        val text = pendingText ?: ImeSelectionStore.peek(this) ?: return
        if (text.isBlank()) return

        retryToken++
        val token = retryToken
        listOf(80L, 250L, 550L, 950L, 1600L).forEach { delay ->
            handler.postDelayed({ tryCommit(token) }, delay)
        }
    }

    private fun tryCommit(token: Int) {
        if (token != retryToken) return
        tryCommitNow(showFailure = false)
    }

    /**
     * Aktif editor varsa mevcut metni secip / silip FOTON secimini yazar.
     * Basarili olursa pending temizlenir. Basarisiz olursa pending KORUNUR.
     */
    private fun tryCommitNow(showFailure: Boolean): Boolean {
        val text = pendingText ?: ImeSelectionStore.peek(this) ?: return false
        if (text.isBlank()) return false

        val ic = currentInputConnection
        if (ic == null) {
            if (showFailure) {
                Toast.makeText(this, "Aktif yazı alanı bulunamadı.", Toast.LENGTH_SHORT).show()
            }
            return false
        }

        val ok = runCatching {
            ic.beginBatchEdit()

            var replaced = false

            // 1) Editor tum metni raporluyorsa en temiz yol: tumunu sec.
            val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
            val currentLength = extracted?.text?.length ?: 0
            if (currentLength > 0) {
                replaced = ic.setSelection(0, currentLength)
            }

            // 2) setSelection desteklenmiyorsa cursor etrafindaki metni sil.
            if (!replaced) {
                val before = ic.getTextBeforeCursor(2048, 0)?.length ?: 0
                val after = ic.getTextAfterCursor(2048, 0)?.length ?: 0
                if (before > 0 || after > 0) {
                    ic.deleteSurroundingText(before, after)
                } else {
                    // Custom editorlarda bu bazen calisir.
                    ic.performContextMenuAction(android.R.id.selectAll)
                }
            }

            val result = ic.commitText(text, 1)
            ic.endBatchEdit()
            result
        }.getOrDefault(false)

        if (ok) {
            pendingText = null
            ImeSelectionStore.clear(this)
            retryToken++
            refreshPendingUi()
            Toast.makeText(this, "$text yazıldı", Toast.LENGTH_SHORT).show()
            return true
        }

        if (showFailure) {
            Toast.makeText(
                this,
                "Otomatik yazılamadı. Seçim beklemede; alanı tekrar dokunup yeniden dene.",
                Toast.LENGTH_LONG
            ).show()
        }
        return false
    }

    private fun openMapPicker() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(MainActivity.EXTRA_FROM_IME, true)
        }
        startActivity(intent)
    }

    private fun keyButton(text: String, click: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(35, 91, 158))
        setOnClickListener { click() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private var ref: WeakReference<FotonInputMethodService>? = null
        val instance: FotonInputMethodService? get() = ref?.get()
    }
}
