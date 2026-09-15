package com.garage.yardsurvey

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Tiny programmatic-UI toolkit. No layout XML, no AndroidX. High-contrast palette for outdoor use. */
object Ui {
    const val BG = 0xFFF7F7F2.toInt()
    const val CARD = 0xFFFFFFFF.toInt()
    const val FG = 0xFF111111.toInt()
    const val MUTED = 0xFF5F6368.toInt()
    const val ACCENT = 0xFF1B5E20.toInt()
    const val GOOD = 0xFF2E7D32.toInt()
    const val WARN = 0xFFE65100.toInt()
    const val BAD = 0xFFC62828.toInt()
    const val BLUE = 0xFF1565C0.toInt()
    const val LINE = 0xFFD0D0C8.toInt()

    fun dp(c: Context, v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.resources.displayMetrics).toInt()

    fun text(c: Context, s: CharSequence = "", sp: Float = 15f, color: Int = FG, bold: Boolean = false, mono: Boolean = false): TextView =
        TextView(c).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            if (mono) typeface = Typeface.MONOSPACE
        }

    fun button(c: Context, s: String, onClick: () -> Unit): Button = Button(c).apply {
        text = s
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    fun edit(c: Context, hint: String, initial: String = "", numeric: Boolean = false): EditText = EditText(c).apply {
        this.hint = hint
        setText(initial)
        inputType = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                    else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        isSingleLine = true
    }

    fun column(c: Context, pad: Int = 0, init: LinearLayout.() -> Unit = {}): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad, pad, pad)
        init()
    }

    fun row(c: Context, init: LinearLayout.() -> Unit = {}): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        init()
    }

    fun card(c: Context, init: LinearLayout.() -> Unit = {}): LinearLayout = column(c, dp(c, 10f)).apply {
        setBackgroundColor(CARD)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(c, 8f), dp(c, 6f), dp(c, 8f), 0)
        layoutParams = lp
        init()
    }

    fun divider(c: Context): View = View(c).apply {
        setBackgroundColor(LINE)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1f))
    }

    fun match(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    fun weight(w: Float): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w)

    fun fill(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
}
