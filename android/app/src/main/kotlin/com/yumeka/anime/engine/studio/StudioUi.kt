package com.yumeka.anime.engine.studio

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/** Paleta do Studio (alinhada com res/values/colors.xml). */
object SC {
    val BG = 0xFF0B0B12.toInt()
    val PANEL = 0xFF12121B.toInt()
    val CARD = 0xFF1C1C28.toInt()
    val CARD2 = 0xFF262636.toInt()
    val BORDER = 0xFF2A2A3A.toInt()
    val TEXT = 0xFFEEEEF5.toInt()
    val TEXT2 = 0xFF9090B0.toInt()
    val MUTED = 0xFF50506A.toInt()
    val RED = 0xFFC9142B.toInt()
    val PINK = 0xFFFF6080.toInt()
    val PURPLE = 0xFF9B6DFF.toInt()
    val GREEN = 0xFF4ADE80.toInt()
    val AMBER = 0xFFFBBF24.toInt()
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun roundBg(color: Int, radius: Float, strokeW: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
    setColor(color); cornerRadius = radius
    if (strokeW > 0) setStroke(strokeW, strokeColor)
}

fun gradBg(radius: Float, vararg colors: Int) = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = radius }

fun ripple(bg: android.graphics.drawable.Drawable) = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, null)

fun Context.label(text: String, sizeSp: Float = 13f, color: Int = SC.TEXT, bold: Boolean = false) = TextView(this).apply {
    this.text = text; textSize = sizeSp; setTextColor(color)
    if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}

/** Botao pilula. */
fun Context.pill(text: String, active: Boolean = false, accent: Int = SC.PINK, onClick: (View) -> Unit): TextView = TextView(this).apply {
    this.text = text; textSize = 13f; gravity = Gravity.CENTER
    setTextColor(if (active) Color.WHITE else SC.TEXT)
    typeface = Typeface.create(Typeface.DEFAULT, if (active) Typeface.BOLD else Typeface.NORMAL)
    val r = dp(18).toFloat()
    background = ripple(if (active) gradBg(r, accent, SC.RED) else roundBg(SC.CARD, r, dp(1), SC.BORDER))
    setPadding(dp(14), dp(8), dp(14), dp(8))
    minHeight = dp(36)
    isClickable = true
    setOnClickListener(onClick)
}

/** Botao de icone quadrado (icone + legenda). */
fun Context.toolButton(icon: String, caption: String?, active: Boolean, size: Int = 56, onClick: (View) -> Unit): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        val r = dp(14).toFloat()
        background = ripple(if (active) gradBg(r, SC.PINK, SC.RED) else roundBg(SC.CARD, r, dp(1), SC.BORDER))
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
        addView(label(icon, if (caption == null) 20f else 18f).apply { gravity = Gravity.CENTER })
        if (caption != null) addView(label(caption, 9f, if (active) Color.WHITE else SC.TEXT2).apply { gravity = Gravity.CENTER; maxLines = 1 })
        isClickable = true
        contentDescription = caption ?: icon
        setOnClickListener(onClick)
    }

fun Context.swatch(color: Int, selected: Boolean, onClick: () -> Unit): View = View(this).apply {
    val s = dp(30)
    layoutParams = LinearLayout.LayoutParams(s, s).apply { setMargins(dp(3), 0, dp(3), 0) }
    background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(color)
        setStroke(if (selected) dp(3) else dp(1), if (selected) Color.WHITE else SC.BORDER)
    }
    setOnClickListener { onClick() }
}

fun Context.sectionTitle(text: String) = label(text.uppercase(), 11f, SC.TEXT2, true).apply {
    letterSpacing = .12f
    setPadding(0, dp(14), 0, dp(6))
}

/** Linha "rotulo + valor + slider". [min]/[max] em unidades reais. */
fun Context.slider(name: String, value: Float, min: Float, max: Float, fmt: (Float) -> String = { "%.0f".format(it) }, onChange: (Float) -> Unit): View {
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
    val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    val valText = label(fmt(value), 12f, SC.PINK, true)
    head.addView(label(name, 12f, SC.TEXT2), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    head.addView(valText)
    box.addView(head)
    val sb = SeekBar(this).apply {
        this.max = 1000
        progress = (((value - min) / (max - min)) * 1000).toInt().coerceIn(0, 1000)
        progressTintList = ColorStateList.valueOf(SC.PINK)
        thumbTintList = ColorStateList.valueOf(Color.WHITE)
        progressBackgroundTintList = ColorStateList.valueOf(SC.BORDER)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                val v = min + (max - min) * p / 1000f
                valText.text = fmt(v); onChange(v)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }
    box.addView(sb)
    return box
}

fun Context.hRow(vararg views: View, spacing: Int = 6): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    views.forEach { v ->
        addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(3), dp(spacing), dp(3)) })
    }
}

/** Layout que quebra linha automaticamente (chips). */
class FlowLayout(context: Context) : ViewGroup(context) {
    private val gap = context.dp(6)
    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val maxW = MeasureSpec.getSize(wSpec)
        var x = 0; var y = 0; var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            c.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            if (x + c.measuredWidth > maxW && x > 0) { x = 0; y += rowH + gap; rowH = 0 }
            x += c.measuredWidth + gap; rowH = maxOf(rowH, c.measuredHeight)
        }
        setMeasuredDimension(maxW, y + rowH)
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l
        var x = 0; var y = 0; var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (x + c.measuredWidth > maxW && x > 0) { x = 0; y += rowH + gap; rowH = 0 }
            c.layout(x, y, x + c.measuredWidth, y + c.measuredHeight)
            x += c.measuredWidth + gap; rowH = maxOf(rowH, c.measuredHeight)
        }
    }
}

fun Context.flow(vararg views: View) = FlowLayout(this).apply { views.forEach { addView(it) } }
