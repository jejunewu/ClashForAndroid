package com.github.kr328.clash.design.component

import android.content.Context
import android.graphics.Color
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.getPixels
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.resolveThemedResourceId

class ProxyViewConfig(val context: Context, var singleLine: Boolean) {
    private val colorSurface = context.resolveThemedColor(R.attr.colorSurface)
    private val regionColorCache = mutableMapOf<String, Int>()
    private val darkTheme = relativeLuminance(colorSurface) < 0.5f

    val clickableBackground =
        context.resolveThemedResourceId(android.R.attr.selectableItemBackground)

    val selectedControl = context.resolveThemedColor(R.attr.colorOnPrimary)
    val selectedBackground = context.resolveThemedColor(R.attr.colorPrimary)
    val selectedAccent = context.resolveThemedColor(R.attr.colorPrimary)
    val selectedRegionControl =
        if (darkTheme) context.resolveThemedColor(R.attr.colorOnSurface) else selectedAccent

    val unselectedControl = context.resolveThemedColor(R.attr.colorOnSurface)
    val unselectedBackground: Int
        get() = if (singleLine) Color.TRANSPARENT else colorSurface

    val layoutPadding = context.getPixels(R.dimen.proxy_layout_padding).toFloat()
    val contentPadding = context.getPixels(R.dimen.proxy_content_padding).toFloat()
    val textMargin = context.getPixels(R.dimen.proxy_text_margin)
    val textSize = context.getPixels(R.dimen.proxy_text_size).toFloat()

    val shadow = Color.argb(
        0x15,
        Color.red(Color.DKGRAY),
        Color.green(Color.DKGRAY),
        Color.blue(Color.DKGRAY),
    )

    val cardRadius = context.getPixels(R.dimen.proxy_card_radius).toFloat()
    var cardOffset = context.getPixels(R.dimen.proxy_card_offset).toFloat()

    val selectionIndicatorWidth = 3f * context.resources.displayMetrics.density
    val selectionIndicatorInset = 8f * context.resources.displayMetrics.density

    fun regionBackground(region: String): Int {
        val key = region.ifBlank { "其他" }
        return regionColorCache.getOrPut(key) {
            if (key == "其他") {
                return@getOrPut if (darkTheme) 0xFF333333.toInt() else 0xFFF3F3F3.toInt()
            }

            val hue = ((key.hashCode() % 360) + 360) % 360
            val saturation = if (darkTheme) 0.33f else 0.13f
            val value = if (darkTheme) 0.32f else 0.995f

            Color.HSVToColor(floatArrayOf(hue.toFloat(), saturation, value))
        }
    }

    private fun relativeLuminance(color: Int): Float {
        return (0.2126f * Color.red(color) +
                0.7152f * Color.green(color) +
                0.0722f * Color.blue(color)) / 255f
    }
}
