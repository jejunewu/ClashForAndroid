package com.github.kr328.clash.design.component

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.design.model.ProxyState
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.max

class ProxyViewState(
    val config: ProxyViewConfig,
    val proxy: Proxy,
    private val parent: ProxyState,
    private val link: ProxyState?
) {
    val paint = Paint()
    val rect = Rect()
    val path = Path()

    val oneLine: Boolean = proxy.speedTested
    var title: String = ""
    var subtitle: String = ""
    var delayText: String = ""
    var background: Int = if (oneLine) {
        Color.TRANSPARENT
    } else {
        config.unselectedBackground
    }
    var controls: Int = config.unselectedControl
    var regionGroupFirst: Boolean = true
    var regionGroupLast: Boolean = true

    val regionKey: String
        get() = proxy.region.ifBlank { "其他" }
    val regionBorder: Int
        get() = config.regionBorderColor(regionKey)

    private var delay: Int = Int.MIN_VALUE
    private var selected: Boolean = false
    private var parentNow: String = ""
    private var linkNow: String? = null

    private var lastFrameTime = System.currentTimeMillis()

    private val autoTitle: String = if (oneLine) {
        buildString {
            append(proxy.title.ifEmpty { proxy.name })
            append("  ·  ")
            append(proxy.region.ifEmpty { "其他" })
            append("   ↓")
            append(formatSpeed(proxy.download))
            append("   ↑")
            append(formatSpeed(proxy.upload))
        }
    } else {
        ""
    }

    val isSelected: Boolean
        get() = selected

    fun update(snap: Boolean): Boolean {
        val frameTime = System.currentTimeMillis()
        var invalidate = false

        if (oneLine) {
            title = autoTitle
            subtitle = ""
        } else if (proxy.type.group) {
            title = proxy.name

            if (link == null) {
                subtitle = proxy.type.name
            } else {
                if (linkNow !== link.now) {
                    linkNow = link.now

                    subtitle = "%s(%s)".format(
                        proxy.type.name,
                        link.now.ifEmpty { "*" }
                    )
                }
            }
        } else {
            title = proxy.title
            subtitle = proxy.subtitle
        }

        if (delay != proxy.delay) {
            delay = proxy.delay
            delayText = if (proxy.delay in 0..Short.MAX_VALUE) {
                if (proxy.speedTested) "${proxy.delay}ms" else proxy.delay.toString()
            } else if (proxy.speedTested) {
                "--"
            } else {
                ""
            }
        }

        if (parentNow !== parent.now) {
            parentNow = parent.now
            selected = proxy.name == parent.now
        }

        controls = when {
            selected -> config.selectedControl
            else -> config.unselectedControl
        }

        if (snap) {
            background = targetBackground()
        } else {
            val target = targetBackground()

            if (background != target) {
                val sa = Color.alpha(background)
                val sr = Color.red(background)
                val sg = Color.green(background)
                val sb = Color.blue(background)

                val ta = Color.alpha(target)
                val tr = Color.red(target)
                val tg = Color.green(target)
                val tb = Color.blue(target)

                val da = ta - sa
                val dr = tr - sr
                val dg = tg - sg
                val db = tb - sb

                val max = max(
                    da.absoluteValue,
                    max(
                        dr.absoluteValue,
                        max(
                            dg.absoluteValue,
                            db.absoluteValue
                        )
                    )
                )

                val frameOffset = frameTime - lastFrameTime

                val colorOffset = (frameOffset / max.toFloat().coerceAtLeast(0.001f))
                    .coerceIn(0.0f, 1.0f)

                background = if (colorOffset > 0.999f) {
                    target
                } else {
                    Color.argb(
                        (sa + da * colorOffset).toInt(),
                        (sr + dr * colorOffset).toInt(),
                        (sg + dg * colorOffset).toInt(),
                        (sb + db * colorOffset).toInt()
                    )
                }

                invalidate = true
            }
        }

        lastFrameTime = frameTime

        return invalidate
    }

    private fun targetBackground(): Int {
        return when {
            selected -> config.selectedBackground
            oneLine -> Color.TRANSPARENT
            else -> config.unselectedBackground
        }
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        if (bytesPerSecond < 0) {
            return "--"
        }

        val value: Double
        val unit: String

        when {
            bytesPerSecond >= 1024 * 1024 -> {
                value = bytesPerSecond / 1024.0 / 1024.0
                unit = "MB/s"
            }
            bytesPerSecond >= 1024 -> {
                value = bytesPerSecond / 1024.0
                unit = "KB/s"
            }
            else -> {
                value = bytesPerSecond.toDouble()
                unit = "B/s"
            }
        }

        return if (value >= 100) {
            String.format(Locale.ROOT, "%.0f%s", value, unit)
        } else {
            String.format(Locale.ROOT, "%.1f%s", value, unit)
        }
    }
}
