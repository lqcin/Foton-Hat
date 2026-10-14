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
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.ExtractedTextRequest
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.lang.ref.WeakReference

/**
 * FOTON klavyesi: Robot Kontrol Sistemi'ndeki aktif metin alanina
 * FOTONMAP'ten secilen Boru Kesit No'yu yazar.
 *
 * Akis:
 * 1) Kullanici Robot'ta Boru Kesit No alanina dokunur.
 * 2) FOTON klavyesinde HARITADAN HAT SEC'e basar.
 * 3) MainActivity acilir, hat secilir ve ONAYLA denir.
 * 4) Secim bekleyen deger olarak kaydedilir, harita kapanir.
 * 5) Robot tekrar one gelince InputConnection uzerinden deger yazilir.
 */
class FotonInputMethodService : InputMethodService() {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingText: String? = null
    private var retryToken = 0

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

        val title = TextView(this).apply {
            text = "FOTON KLAVYE"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(title)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(keyButton("HARİTADAN HAT SEÇ") {
            openMapPicker()
        }, LinearLayout.LayoutParams(0, dp(54), 1f))

        row.addView(keyButton("⌫") {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }, LinearLayout.LayoutParams(dp(72), dp(54)))

        row.addView(keyButton("NORMAL KLAVYE") {
            val switched = runCatching { switchToNextInputMethod(false) }.getOrDefault(false)
            if (!switched) {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showInputMethodPicker()
            }
        }, LinearLayout.LayoutParams(dp(145), dp(54)))

        root.addView(row)

        val hint = TextView(this).apply {
            text = "Boru Kesit No alanındayken 'Haritadan Hat Seç' düğmesine bas."
            setTextColor(Color.rgb(177, 198, 220))
            textSize = 12f
            setPadding(0, dp(4), 0, 0)
        }
        root.addView(hint)
        return root
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (pendingText == null) pendingText = ImeSelectionStore.peek(this)
        scheduleCommit()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        if (pendingText == null) pendingText = ImeSelectionStore.peek(this)
        scheduleCommit()
    }

    fun queueSection(sectionNo: String) {
        if (sectionNo.isBlank()) return
        pendingText = sectionNo
        ImeSelectionStore.save(this, sectionNo)
        retryToken++
        val token = retryToken
        // Harita activity'si kapanip Robot tekrar one geldikten sonra dene.
        handler.postDelayed({ tryCommit(token) }, 180)
        handler.postDelayed({ tryCommit(token) }, 450)
        handler.postDelayed({ tryCommit(token) }, 900)
        handler.postDelayed({ tryCommit(token) }, 1500)
        handler.postDelayed({ fallbackIfStillPending(token) }, 2300)
    }

    private fun scheduleCommit() {
        val text = pendingText ?: return
        if (text.isBlank()) return
        retryToken++
        val token = retryToken
        handler.postDelayed({ tryCommit(token) }, 100)
        handler.postDelayed({ tryCommit(token) }, 350)
        handler.postDelayed({ tryCommit(token) }, 700)
    }

    private fun tryCommit(token: Int) {
        if (token != retryToken) return
        val text = pendingText ?: ImeSelectionStore.peek(this) ?: return
        val ic = currentInputConnection ?: return

        val ok = runCatching {
            ic.beginBatchEdit()
            // Alan doluysa eski Boru Kesit No'nun ustune yaz.
            val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
            val currentLength = extracted?.text?.length ?: 0
            if (currentLength > 0) {
                val selected = ic.setSelection(0, currentLength)
                if (!selected) ic.performContextMenuAction(android.R.id.selectAll)
            } else {
                ic.performContextMenuAction(android.R.id.selectAll)
            }
            val result = ic.commitText(text, 1)
            ic.endBatchEdit()
            result
        }.getOrDefault(false)

        if (ok) {
            pendingText = null
            ImeSelectionStore.clear(this)
            retryToken++
            Toast.makeText(this, "$text yazıldı", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fallbackIfStillPending(token: Int) {
        if (token != retryToken) return
        val text = pendingText ?: return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Boru Kesit No", text))
        pendingText = null
        ImeSelectionStore.clear(this)
        retryToken++
        Toast.makeText(this, "Otomatik yazılamadı; $text panoya kopyalandı", Toast.LENGTH_LONG).show()
    }

    private fun openMapPicker() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
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
