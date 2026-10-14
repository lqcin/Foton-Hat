package com.foton.dxfhelper

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/**
 * FOTON Klavye v0.5
 *
 * Amaç:
 * - Normal Türkçe Q klavye olarak kullanılabilsin.
 * - Haritadan hat seçimi aynı klavyenin içinden açılsın.
 * - Haritada seçilen son kesit klavyenin üstünde büyük ve görünür dursun.
 * - Kullanıcı "A41-A42 YAZ" butonuna dokunduğunda kesit numarası,
 *   normal FOTON tuşları çalıştığı gibi karakter karakter yazılsın.
 */
class FotonInputMethodService : InputMethodService() {

    private val handler = Handler(Looper.getMainLooper())

    private var lastSectionLabel: TextView? = null
    private var lastSectionButton: Button? = null
    private var shiftButton: Button? = null

    private val letterButtons =
        mutableListOf<Pair<Button, String>>()

    private var caps = true

    private val tr =
        Locale("tr", "TR")

    override fun onEvaluateFullscreenMode(): Boolean =
        false

    override fun onCreateInputView(): android.view.View {
        letterButtons.clear()

        val root =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(8),
                    dp(6),
                    dp(8),
                    dp(8)
                )

                background =
                    roundedBg(
                        C_BG,
                        0f
                    )
            }

        addHeader(root)
        addLastSectionCard(root)
        addActionRow(root)

        addKeyRow(
            root,
            listOf(
                "1","2","3","4","5","6",
                "7","8","9","0","-","/"
            ),
            letters = false
        )

        addKeyRow(
            root,
            listOf(
                "Q","W","E","R","T","Y",
                "U","I","O","P","Ğ","Ü"
            ),
            letters = true
        )

        addKeyRow(
            root,
            listOf(
                "A","S","D","F","G","H",
                "J","K","L","Ş","İ"
            ),
            letters = true
        )

        addKeyRow(
            root,
            listOf(
                "Z","X","C","V","B","N",
                "M","Ö","Ç",".",","
            ),
            letters = true
        )

        addBottomRow(root)

        refreshLastSectionUi()
        updateLetterCase()

        return root
    }

    override fun onStartInputView(
        info: android.view.inputmethod.EditorInfo?,
        restarting: Boolean
    ) {
        super.onStartInputView(
            info,
            restarting
        )

        refreshLastSectionUi()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        refreshLastSectionUi()
    }

    private fun addHeader(
        root: LinearLayout
    ) {
        val header =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    dp(4),
                    0,
                    dp(4),
                    0
                )
            }

        header.addView(
            TextView(this).apply {
                text = "FOTON"

                setTextColor(
                    C_TEXT
                )

                textSize = 16f

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            },
            LinearLayout.LayoutParams(
                0,
                dp(32),
                1f
            )
        )

        header.addView(
            TextView(this).apply {
                text =
                    "Hat Seçici Klavye"

                setTextColor(
                    C_MUTED
                )

                textSize = 12f

                gravity =
                    Gravity.END or
                    Gravity.CENTER_VERTICAL
            },
            LinearLayout.LayoutParams(
                0,
                dp(32),
                1f
            )
        )

        header.addView(
            Button(this).apply {
                text = "⌄ GİZLE"
                isAllCaps = false
                textSize = 11f
                setTextColor(C_TEXT)
                minHeight = 0
                minimumHeight = 0
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(8), 0, dp(8), 0)
                background = roundedBg(C_PANEL_ALT, dpF(8f))
                setOnClickListener {
                    performHapticFeedback(
                        HapticFeedbackConstants.KEYBOARD_TAP
                    )
                    requestHideSelf(0)
                }
            },
            LinearLayout.LayoutParams(
                dp(82),
                dp(30)
            ).apply {
                setMargins(dp(6), 0, 0, 0)
            }
        )

        root.addView(header)
    }

    private fun addLastSectionCard(
        root: LinearLayout
    ) {
        val card =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    dp(10),
                    dp(5),
                    dp(7),
                    dp(5)
                )

                background =
                    roundedBg(
                        C_PANEL,
                        dpF(12f)
                    )
            }

        val textBox =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
            }

        textBox.addView(
            TextView(this).apply {
                text =
                    "SON SEÇİLEN HAT"

                setTextColor(
                    C_MUTED
                )

                textSize = 10f

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )
            }
        )

        lastSectionLabel =
            TextView(this).apply {
                text =
                    "Henüz seçim yok"

                setTextColor(
                    C_MUTED
                )

                textSize = 20f

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        textBox.addView(
            lastSectionLabel
        )

        card.addView(
            textBox,
            LinearLayout.LayoutParams(
                0,
                dp(52),
                1f
            )
        )

        lastSectionButton =
            Button(this).apply {
                text = "HAT YOK"

                isAllCaps = false

                textSize = 15f

                setTextColor(
                    Color.WHITE
                )

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                minHeight = 0
                minimumHeight = 0
                minWidth = 0
                minimumWidth = 0

                setPadding(
                    dp(10),
                    0,
                    dp(10),
                    0
                )

                background =
                    roundedBg(
                        C_GREEN,
                        dpF(10f)
                    )

                setOnClickListener {
                    performHapticFeedback(
                        HapticFeedbackConstants.KEYBOARD_TAP
                    )

                    val section =
                        ImeSelectionStore
                            .getLastSection(
                                this@FotonInputMethodService
                            )

                    if (
                        section.isNullOrBlank()
                    ) {
                        Toast.makeText(
                            this@FotonInputMethodService,
                            "Önce haritadan bir hat seç.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        typeSectionLikeNormalKeys(
                            section
                        )
                    }
                }
            }

        card.addView(
            lastSectionButton,
            LinearLayout.LayoutParams(
                dp(180),
                dp(48)
            ).apply {
                setMargins(
                    dp(6),
                    0,
                    0,
                    0
                )
            }
        )

        root.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(
                    0,
                    0,
                    0,
                    dp(5)
                )
            }
        )
    }

    private fun addActionRow(
        root: LinearLayout
    ) {
        val row =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        row.addView(
            actionButton(
                "HARİTADAN HAT SEÇ",
                C_BLUE
            ) {
                openMapPicker()
            },
            weightedParams(
                1.6f,
                47
            )
        )

        row.addView(
            actionButton(
                "TEMİZLE",
                C_PANEL_ALT
            ) {
                clearEditorText()
            },
            weightedParams(
                0.72f,
                47
            )
        )

        row.addView(
            actionButton(
                "KLAVYE",
                C_PANEL_ALT
            ) {
                (
                    getSystemService(
                        Context.INPUT_METHOD_SERVICE
                    )
                    as InputMethodManager
                ).showInputMethodPicker()
            },
            weightedParams(
                0.72f,
                47
            )
        )

        root.addView(row)
    }

    private fun addKeyRow(
        root: LinearLayout,
        labels: List<String>,
        letters: Boolean
    ) {
        val row =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        labels.forEach { raw ->
            val button =
                keyButton(
                    raw,
                    C_KEY,
                    16f
                ) {
                    val value =
                        if (letters) {
                            if (caps) {
                                raw.uppercase(tr)
                            } else {
                                raw.lowercase(tr)
                            }
                        } else {
                            raw
                        }

                    commit(value)
                }

            if (letters) {
                letterButtons +=
                    button to raw
            }

            row.addView(
                button,
                weightedParams(
                    1f,
                    44
                )
            )
        }

        root.addView(row)
    }

    private fun addBottomRow(
        root: LinearLayout
    ) {
        val row =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        shiftButton =
            keyButton(
                "⇧ ABC",
                C_PANEL_ALT,
                14f
            ) {
                caps = !caps
                updateLetterCase()
            }.also {
                row.addView(
                    it,
                    weightedParams(
                        0.85f,
                        48
                    )
                )
            }

        row.addView(
            keyButton(
                "BOŞLUK",
                C_PANEL_ALT,
                14f
            ) {
                commit(" ")
            },
            weightedParams(
                2.5f,
                48
            )
        )

        val back =
            keyButton(
                "⌫",
                C_PANEL_ALT,
                20f
            ) {
                currentInputConnection
                    ?.deleteSurroundingText(
                        1,
                        0
                    )
            }.apply {
                setOnLongClickListener {
                    currentInputConnection
                        ?.deleteSurroundingText(
                            10,
                            0
                        )

                    true
                }
            }

        row.addView(
            back,
            weightedParams(
                0.8f,
                48
            )
        )

        row.addView(
            keyButton(
                "ENTER",
                C_BLUE_DARK,
                13f
            ) {
                sendEnter()
            },
            weightedParams(
                0.95f,
                48
            )
        )

        root.addView(row)
    }

    private fun refreshLastSectionUi() {
        val last =
            ImeSelectionStore
                .getLastSection(this)

        if (
            last.isNullOrBlank()
        ) {
            lastSectionLabel?.text =
                "Henüz seçim yok"

            lastSectionLabel
                ?.setTextColor(
                    C_MUTED
                )

            lastSectionButton?.text =
                "HAT YOK"

            lastSectionButton?.isEnabled =
                false

            lastSectionButton?.alpha =
                0.45f
        } else {
            lastSectionLabel?.text =
                last

            lastSectionLabel
                ?.setTextColor(
                    C_GREEN_LIGHT
                )

            lastSectionButton?.text =
                "$last  YAZ"

            lastSectionButton?.isEnabled =
                true

            lastSectionButton?.alpha =
                1f
        }
    }

    private fun openMapPicker() {
        ImeSelectionStore
            .beginSelectionRequest(
                this
            )

        val intent =
            Intent(
                this,
                MainActivity::class.java
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )

                putExtra(
                    MainActivity.EXTRA_FROM_IME,
                    true
                )
            }

        startActivity(intent)
    }

    /**
     * Robot'ta elle FOTON Klavye ile yazının çalıştığı doğrulandı.
     * Bu nedenle son seçilen kesit de aynı commitText yoluyla,
     * karakter karakter ve küçük gecikmeyle yazılır.
     */
    private fun typeSectionLikeNormalKeys(
        section: String
    ) {
        val ic =
            currentInputConnection

        if (ic == null) {
            Toast.makeText(
                this,
                "Aktif yazı alanı bulunamadı.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        clearCurrentConnectionText(ic)

        section
            .toCharArray()
            .forEachIndexed {
                index,
                char ->

                handler.postDelayed(
                    {
                        ic.commitText(
                            char.toString(),
                            1
                        )
                    },
                    index * 45L
                )
            }

        Toast.makeText(
            this,
            "$section yazılıyor",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun clearCurrentConnectionText(
        ic: android.view.inputmethod.InputConnection
    ) {
        runCatching {
            val extracted =
                ic.getExtractedText(
                    ExtractedTextRequest(),
                    0
                )

            val length =
                extracted?.text?.length
                    ?: 0

            if (
                length > 0 &&
                ic.setSelection(
                    0,
                    length
                )
            ) {
                ic.commitText(
                    "",
                    1
                )
            } else {
                val before =
                    ic.getTextBeforeCursor(
                        4096,
                        0
                    )?.length ?: 0

                val after =
                    ic.getTextAfterCursor(
                        4096,
                        0
                    )?.length ?: 0

                if (
                    before > 0 ||
                    after > 0
                ) {
                    ic.deleteSurroundingText(
                        before,
                        after
                    )
                }

                // runCatching bloğunun son ifadesi artık açıkça Unit.
                // Kotlin böylece yukarıdaki if'i değer döndüren expression
                // olarak yorumlayıp "else" istemiyor.
                Unit
            }
        }
    }

    private fun updateLetterCase() {
        letterButtons.forEach {
            (button, raw) ->

            button.text =
                if (caps) {
                    raw.uppercase(tr)
                } else {
                    raw.lowercase(tr)
                }
        }

        shiftButton?.text =
            if (caps) {
                "⇧ ABC"
            } else {
                "⇧ abc"
            }
    }

    private fun commit(
        value: String
    ) {
        currentInputConnection
            ?.commitText(
                value,
                1
            )
    }

    private fun sendEnter() {
        val ic =
            currentInputConnection
                ?: return

        val sent =
            runCatching {
                val down =
                    ic.sendKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN,
                            KeyEvent.KEYCODE_ENTER
                        )
                    )

                val up =
                    ic.sendKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_UP,
                            KeyEvent.KEYCODE_ENTER
                        )
                    )

                down || up
            }.getOrDefault(false)

        if (!sent) {
            ic.commitText(
                "\n",
                1
            )
        }
    }

    private fun clearEditorText() {
        val ic =
            currentInputConnection
                ?: return

        clearCurrentConnectionText(
            ic
        )
    }

    private fun actionButton(
        label: String,
        color: Int,
        click: () -> Unit
    ): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 13f

            setTypeface(
                typeface,
                Typeface.BOLD
            )

            setTextColor(
                Color.WHITE
            )

            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0

            setPadding(
                dp(5),
                0,
                dp(5),
                0
            )

            background =
                roundedBg(
                    color,
                    dpF(9f)
                )

            setOnClickListener {
                performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP
                )

                click()
            }
        }

    private fun keyButton(
        label: String,
        color: Int,
        sizeSp: Float,
        click: () -> Unit
    ): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = sizeSp

            setTextColor(
                C_TEXT
            )

            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0

            setPadding(
                dp(1),
                0,
                dp(1),
                0
            )

            background =
                roundedBg(
                    color,
                    dpF(8f)
                )

            setOnClickListener {
                performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP
                )

                click()
            }
        }

    private fun weightedParams(
        weight: Float,
        height: Int
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            0,
            dp(height),
            weight
        ).apply {
            setMargins(
                dp(2),
                dp(2),
                dp(2),
                dp(2)
            )
        }

    private fun roundedBg(
        color: Int,
        radius: Float
    ): GradientDrawable =
        GradientDrawable().apply {
            shape =
                GradientDrawable.RECTANGLE

            setColor(color)

            cornerRadius =
                radius
        }

    private fun dp(
        value: Int
    ): Int =
        (
            value *
            resources
                .displayMetrics
                .density
        ).toInt()

    private fun dpF(
        value: Float
    ): Float =
        value *
        resources
            .displayMetrics
            .density

    companion object {
        private val C_BG =
            Color.rgb(
                10,
                18,
                28
            )

        private val C_PANEL =
            Color.rgb(
                22,
                36,
                52
            )

        private val C_PANEL_ALT =
            Color.rgb(
                40,
                57,
                76
            )

        private val C_KEY =
            Color.rgb(
                31,
                46,
                63
            )

        private val C_BLUE =
            Color.rgb(
                37,
                99,
                235
            )

        private val C_BLUE_DARK =
            Color.rgb(
                30,
                75,
                155
            )

        private val C_GREEN =
            Color.rgb(
                21,
                139,
                89
            )

        private val C_GREEN_LIGHT =
            Color.rgb(
                105,
                231,
                166
            )

        private val C_TEXT =
            Color.rgb(
                241,
                246,
                251
            )

        private val C_MUTED =
            Color.rgb(
                150,
                170,
                191
            )
    }
}
