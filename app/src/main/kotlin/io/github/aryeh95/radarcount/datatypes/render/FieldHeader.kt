package io.github.aryeh95.radarcount.datatypes.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import io.github.aryeh95.radarcount.datatypes.FieldFont
import io.hammerhead.karooext.models.ViewConfig
import kotlin.math.max

/** A single field's header drawn into a bitmap, and the label size it took, for the on-device test. */
class HeaderImage(val image: FieldImage, val label: String, val labelSp: Float, val karooSp: Float)

/**
 * The header the single fields draw themselves in place of the Karoo's
 * strip, which wraps a long name onto a second line at half width
 * ("VEHICLES / PER HOUR") and so takes room from the value. Drawn like the
 * Karoo's own header, as measured on the device (and as Barberfish draws
 * its fields' headers): the data type's icon in the header icon green,
 * the name in capitals in the Karoo's header font and size, both centred
 * in a band 22 dp tall. The name always stays on one line: where it does
 * not fit at the Karoo's size it is drawn only as much smaller as it needs.
 *
 * Drawn into the bitmap rather than set on a TextView: a TextView's line
 * box differs between the Karoo 2's Android 8 and the Karoo 3's Android 12,
 * and aligning, wrapping or sizing one through RemoteViews is partly
 * unavailable on Android 8, while measured glyphs land the same on both.
 */
object FieldHeader {

    /** The Karoo's header band, at least this tall. */
    private const val BAND_DP = 22f
    /** Between the tile edge and the icon or name: the field's 4 dp side padding (2 dp in narrow slots) and the header's own 1 dp. */
    const val SIDE_DP = 4f
    const val SIDE_NARROW_DP = 2f
    const val INSET_DP = 1f
    /** Before the name, on its start side. */
    const val LABEL_MARGIN_DP = 1f

    /**
     * The Karoo's header label size for a tile, in sp, by its grid span. In
     * a half-width tile of a five-row page the Karoo follows the rider's
     * Label Size setting, which also sets the value size it reports: 41 sp
     * at Large, 46 or 47 at Small.
     */
    fun karooSp(config: ViewConfig): Float {
        val (cols, rows) = config.gridSize
        return when {
            cols == 60 && rows >= 15 -> 19.2f
            cols == 60 && rows >= 12 -> 17.6f
            cols == 30 && rows >= 15 -> 17.6f
            cols == 30 -> if (config.textSize <= 43) 17.6f else 15.5f
            cols == 20 && rows >= 12 -> 12f
            cols == 15 && rows >= 12 -> 11f
            else -> 15.5f
        }
    }

    /** Height of the header band in px for [config], which the value area starts below. */
    fun heightPx(config: ViewConfig, density: Float, text: FieldText = FieldText()): Int {
        val line = text.lineBox(FieldFont.LABEL, karooSp(config) * density)
        return max((BAND_DP * density).toInt(), line.descent - line.ascent)
    }

    /**
     * Icon [iconRes] tinted [iconColor] and [label] in [color], across the
     * full width of a tile of [config]'s size, placed per its alignment as
     * the Karoo places them: right-aligned names with the icon at the left,
     * left-aligned names with it at the right, centred names centred in the
     * room right of the icon.
     */
    // RADAR INACTIVE icon and other HEADER icons:
    fun draw(context: Context, label: String, iconRes: Int, config: ViewConfig, density: Float, color: Int, iconColor: Int, showIcons: Boolean): HeaderImage {
        val text = FieldText()
        val upper = label.uppercase()
        val w = config.viewSize.first.coerceAtLeast(1)
        val h = heightPx(config, density, text)
        val karooSp = karooSp(config)

        // The icon is as tall as the header text in dp.
        val side = ((if (config.gridSize.first <= 20) SIDE_NARROW_DP else SIDE_DP) * density).toInt() +
                layoutPx(INSET_DP, density)
        val iconPx = (karooSp * density).toInt()
        val gapPx = (max(2, (karooSp * 0.2f).toInt()) * density).toInt()
        val marginPx = layoutPx(LABEL_MARGIN_DP, density)

        // Reserve space for the icon only when it is shown.
        val iconSpace = if (showIcons) iconPx + gapPx else 0
        val room = (w - 2 * side - iconSpace - marginPx).toFloat()

        // Fit the label onto one line.
        var sizePx = karooSp * density
        val full = text.width(upper, FieldFont.LABEL, sizePx)
        if (room > 0 && full > room) {
            sizePx *= room / full
            while (sizePx > 1f && text.width(upper, FieldFont.LABEL, sizePx) > room) {
                sizePx -= 0.01f * density
            }
        }

        val labelWidth = text.width(upper, FieldFont.LABEL, sizePx)
        val (x, align, iconLeft) = when (config.alignment) {

            ViewConfig.Alignment.RIGHT -> {
                val textRight = (w - side).toFloat()
                val iconPosition =
                    (textRight - labelWidth - gapPx - iconPx).toInt()

                Triple(textRight, Paint.Align.RIGHT, iconPosition)
            }

            ViewConfig.Alignment.LEFT -> {
                val iconPosition = side + marginPx
                val textLeft = iconPosition +
                        if (showIcons) iconPx + gapPx else 0

                Triple(textLeft.toFloat(), Paint.Align.LEFT, iconPosition)
            }

            ViewConfig.Alignment.CENTER -> {
                val groupWidth = labelWidth +
                        if (showIcons) (iconPx + gapPx).toFloat() else 0f

                // Centre the icon and label together.
                val contentLeft = side + marginPx / 2f
                val contentRight = w - side - marginPx / 2f
                val groupLeft = contentLeft +
                        (contentRight - contentLeft - groupWidth) / 2f

                val iconPosition = groupLeft.toInt()
                val textLeft = groupLeft +
                        if (showIcons) (iconPx + gapPx).toFloat() else 0f

                Triple(
                    textLeft + labelWidth / 2f,
                    Paint.Align.CENTER,
                    iconPosition
                )
            }
        }

        val line = text.lineBox(FieldFont.LABEL, sizePx)
        val baseline = (h - (line.descent - line.ascent)) / 2f - line.ascent
        val iconTop = (h - iconPx) / 2

        val bitmap = FieldBitmaps.newBitmap(w, h)
        val canvas = Canvas(bitmap)
        val bounds = mutableListOf<Rect>()

        if (showIcons) {
            val icon = checkNotNull(context.getDrawable(iconRes)).mutate()
            icon.setTint(iconColor)
            icon.setBounds(iconLeft, iconTop, iconLeft + iconPx, iconTop + iconPx)
            icon.draw(canvas)
            bounds.add(Rect(icon.bounds))
        }

        val name = text.draw(
            canvas, upper, FieldFont.LABEL, sizePx, x, baseline, align, color
        )
        bounds.add(name)

        return HeaderImage(
            FieldImage(bitmap, bounds),
            upper,
            sizePx / density,
            karooSp
        )
    }

    /** [dp] as Android sizes it in a layout: rounded to whole px. */
    fun layoutPx(dp: Float, density: Float): Int = (dp * density + 0.5f).toInt()
}
