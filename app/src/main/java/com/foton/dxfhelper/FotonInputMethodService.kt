package com.foton.dxfhelper

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * FOTON Klavye v0.4
 *
 * - Android'de varsayilan klavye olarak sabit kullanilabilir.
 * - Normal yazi yazabilir (Turkce Q).
 * - Haritadan hat secme FOTON klavyenin icindedir.
 * - Haritadan gelen secim kalici bekleyen deger olarak tutulur.
 * - Robot uygulamasi haritadan donuste editor odagini kaybetse bile secim silinmez.
 */
class FotonInputMethodService : InputMethodService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingText: String? = null
    private var retryToken = 0

    private var pendingLabel: TextView? = null
    private var writeButton: Button? = null
    private var shiftButton: Button? = null
    private val letterButtons = mutableListOf<Pair<Button, String>>()

    private var caps = true
    private val tr = Locale("tr", "TR")

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
        letterButtons.clear()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12, 24, 38))
            setPadding(dp(8), dp(5), dp(8), dp(6))
        }

        // Üst durum satırı
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(TextView(this).apply {
            text = "FOTON KLAVYE"
            setTextColor(Color.WHITE)
            textSize = 13f
        }, LinearLayout.LayoutParams(0, dp(26), 1f))

        pendingLabel = TextView(this).apply {
            setTextColor(Color.rgb(102, 230, 160))
            textSize = 13f
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        header.addView(pendingLabel, LinearLayout.LayoutParams(0, dp(26), 1f))
        root.addView(header)

        // FOTON'a özel aksiyonlar
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        actionRow.addView(actionButton("HARİTADAN HAT SEÇ") {
            openMapPicker()
        }, weightParams(1.65f, 50))

        writeButton = actionButton("SEÇİMİ YAZ") {
            loadPending()
            if (!tryCommitPending(showFailure = true)) {
                Toast.makeText(
                    this,
                    "Aktif alana yazılamadı. Alanı tekrar seçip yeniden dene.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }.also {
            actionRow.addView(it, weightParams(1.0f, 50))
        }

        actionRow.addView(actionButton("TEMİZLE") {
            clearEditorText()
        }, weightParams(0.72f, 50))

        actionRow.addView(actionButton("⌨") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showInputMethodPicker()
        }, LinearLayout.LayoutParams(dp(58), dp(50)))

        root.addView(actionRow)

        // Sayılar
        addKeyRow(
            root,
            listOf("1","2","3","4","5","6","7","8","9","0","-","/"),
            letters = false
        )

        // Türkçe Q
        addKeyRow(
            root,
            listOf("Q","W","E","R","T","Y","U","I","O","P","Ğ","Ü"),
            letters = true
        )
        addKeyRow(
            root,
            listOf("A","S","D","F","G","H","J","K","L","Ş","İ"),
            letters = true
        )
        addKeyRow(
            root,
            listOf("Z","X","C","V","B","N","M","Ö","Ç",".",","),
            letters = true
        )

        // Alt kontrol satırı
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        shiftButton = keyButton("⇧ ABC", dp(50), 15f) {
            caps = !caps
            updateLetterCase()
        }.also { bottom.addView(it, weightParams(0.75f, 50)) }

        bottom.addView(keyButton("BOŞLUK", dp(50), 15f) {
            commit(" ")
        }, weightParams(2.2f, 50))

        val back = keyButton("⌫", dp(50), 20f) {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }.apply {
            setOnLongClickListener {
                currentInputConnection?.deleteSurroundingText(10, 0)
                true
            }
        }
        bottom.addView(back, weightParams(0.70f, 50))

        bottom.addView(keyButton("ENTER", dp(50), 14f) {
            sendEnter()
        }, weightParams(0.85f, 50))

        root.addView(bottom)

        loadPending()
        refreshPendingUi()
        updateLetterCase()
        schedulePendingCommit()
        return root
    }

    override fun onStartInput(
        attribute: android.view.inputmethod.EditorInfo?,
        restarting: Boolean
    ) {
        super.onStartInput(attribute, restarting)
        loadPending()
        schedulePendingCommit()
    }

    override fun onStartInputView(
        info: android.view.inputmethod.EditorInfo?,
        restarting: Boolean
    ) {
        super.onStartInputView(info, restarting)
        loadPending()
        refreshPendingUi()
        schedulePendingCommit()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        loadPending()
        refreshPendingUi()
        schedulePendingCommit()
    }

    fun notifySelectionReady(sectionNo: String) {
        if (sectionNo.isBlank()) return

        pendingText = sectionNo
        ImeSelectionStore.save(this, sectionNo)
        refreshPendingUi()

        retryToken++
        val token = retryToken

        // Robot activity yeniden öne gelirken birkaç kez dene.
        listOf(250L, 550L, 950L, 1500L, 2400L, 3600L).forEach { delay ->
            handler.postDelayed({ tryCommit(token) }, delay)
        }

        handler.postDelayed({
            runCatching { requestShowSelf(0) }
        }, 400L)
    }

    private fun openMapPicker() {
        // v0.3.3 köprüsü: Intent extra kaybolsa bile istek kalıcıdır.
        ImeSelectionStore.beginSelectionRequest(this)
        refreshPendingUi()

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

    private fun addKeyRow(
        root: LinearLayout,
        labels: List<String>,
        letters: Boolean
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        labels.forEach { raw ->
            val button = keyButton(raw, dp(46), 17f) {
                val text = if (letters) {
                    if (caps) raw.uppercase(tr) else raw.lowercase(tr)
                } else raw
                commit(text)
            }
            if (letters) letterButtons += button to raw
            row.addView(button, weightParams(1f, 46))
        }

        root.addView(row)
    }

    private fun updateLetterCase() {
        letterButtons.forEach { (button, raw) ->
            button.text = if (caps) raw.uppercase(tr) else raw.lowercase(tr)
        }
        shiftButton?.text = if (caps) "⇧ ABC" else "⇧ abc"
    }

    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun sendEnter() {
        val ic = currentInputConnection ?: return
        val sent = runCatching {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            true
        }.getOrDefault(false)

        if (!sent) ic.commitText("\n", 1)
    }

    private fun clearEditorText() {
        val ic = currentInputConnection ?: return
        runCatching {
            ic.beginBatchEdit()
            val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
            val length = extracted?.text?.length ?: 0

            if (length > 0 && ic.setSelection(0, length)) {
                ic.commitText("", 1)
            } else {
                val before = ic.getTextBeforeCursor(4096, 0)?.length ?: 0
                val after = ic.getTextAfterCursor(4096, 0)?.length ?: 0
                if (before > 0 || after > 0) {
                    ic.deleteSurroundingText(before, after)
                }
            }
            ic.endBatchEdit()
        }
    }

    private fun loadPending() {
        val stored = ImeSelectionStore.peek(this)
        pendingText = stored
        refreshPendingUi()
    }

    private fun refreshPendingUi() {
        val text = pendingText ?: ImeSelectionStore.peek(this)
        pendingLabel?.text = when {
            !text.isNullOrBlank() -> "Bekleyen: $text"
            ImeSelectionStore.isSelectionRequestActive(this) -> "Haritadan seçim bekleniyor"
            else -> "Hazır"
        }
        writeButton?.isEnabled = !text.isNullOrBlank()
    }

    private fun schedulePendingCommit() {
        val text = pendingText ?: ImeSelectionStore.peek(this) ?: return
        if (text.isBlank()) return

        retryToken++
        val token = retryToken
        listOf(100L, 300L, 650L, 1100L).forEach { delay ->
            handler.postDelayed({ tryCommit(token) }, delay)
        }
    }

    private fun tryCommit(token: Int) {
        if (token != retryToken) return
        tryCommitPending(showFailure = false)
    }

    /**
     * Bekleyen FOTONMAP secimini mevcut aktif editorun TAMAMI yerine yazar.
     * Basarisiz olursa secim silinmez.
     */
    private fun tryCommitPending(showFailure: Boolean): Boolean {
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

            var selectedAll = false
            val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
            val currentLength = extracted?.text?.length ?: 0
            if (currentLength > 0) {
                selectedAll = ic.setSelection(0, currentLength)
            }

            if (!selectedAll) {
                val before = ic.getTextBeforeCursor(4096, 0)?.length ?: 0
                val after = ic.getTextAfterCursor(4096, 0)?.length ?: 0
                if (before > 0 || after > 0) {
                    ic.deleteSurroundingText(before, after)
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
                "Yazılamadı; seçim kaybolmadı. Alanı tekrar seçip 'SEÇİMİ YAZ' de.",
                Toast.LENGTH_LONG
            ).show()
        }
        return false
    }

    private fun actionButton(text: String, click: () -> Unit): Button =
        keyButton(text, dp(50), 14f, click).apply {
            setBackgroundColor(Color.rgb(29, 90, 166))
        }

    private fun keyButton(
        text: String,
        height: Int,
        sizeSp: Float,
        click: () -> Unit
    ) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = sizeSp
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(33, 58, 86))
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(2), 0, dp(2), 0)
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            click()
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            height
        ).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
    }

    private fun weightParams(weight: Float, height: Int) =
        LinearLayout.LayoutParams(0, dp(height), weight).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    companion object {
        private var ref: WeakReference<FotonInputMethodService>? = null
        val instance: FotonInputMethodService? get() = ref?.get()
    }
}
