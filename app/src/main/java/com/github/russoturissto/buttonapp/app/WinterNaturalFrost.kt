package com.github.russoturissto.buttonapp.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Organic, per-surface hoarfrost, rather than tiled identical stamps.
 *
 * Every wrapper owns a distinct seed. The seed is reused for redraws, so
 * scrolling, invalidation and screenshots don't cause the crystals to jump.
 * Growth is kept in a narrow border strip, away from controls and values.
 * No network, permissions, external fonts, or animation are involved.
 */
internal object WinterNaturalFrost {
    private const val REFERENCE_INTENSITY = WinterThemePolicy.FROST_INTENSITY

    fun render(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        dark: Boolean,
        surface: WinterSurface,
        alpha: Int,
        patternSeed: Long
    ) {
        if (width < 20f * density || height < 20f * density) return
        // Width and height participate in the pattern but never the time.
        val seed = patternSeed xor
            (java.lang.Float.floatToIntBits(width).toLong() shl 16) xor
            java.lang.Float.floatToIntBits(height).toLong() xor
            surface.ordinal.toLong()
        val random = Random(seed.toInt() xor (seed ushr 32).toInt())
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val powder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val intensity = (alpha / 255f) * REFERENCE_INTENSITY

        fun ice(visibility: Float, warmer: Boolean = false): Int {
            val value = (visibility * intensity).toInt().coerceIn(0, 255)
            return if (dark) {
                if (warmer) Color.argb(value, 230, 250, 252)
                else Color.argb(value, 191, 237, 251)
            } else {
                if (warmer) Color.argb(value, 241, 251, 252)
                else Color.argb(value, 88, 172, 193)
            }
        }

        fun filament(x1: Float, y1: Float, x2: Float, y2: Float,
                     size: Float, strength: Float) {
            // Thin, irregular pale core with a very faint diffuse shell.
            stroke.color = ice(strength * .22f)
            stroke.strokeWidth = size * 2.3f
            canvas.drawLine(x1, y1, x2, y2, stroke)
            stroke.color = ice(strength, true)
            stroke.strokeWidth = size
            canvas.drawLine(x1, y1, x2, y2, stroke)
        }

        fun grow(x: Float, y: Float, angle: Double, length: Float,
                 order: Int, brightness: Float) {
            if (order <= 0 || length < .85f * density) return
            val tipX = x + cos(angle).toFloat() * length
            val tipY = y + sin(angle).toFloat() * length
            filament(x, y, tipX, tipY,
                (if (order >= 3) .60f else .36f) * density, brightness)
            if (order == 1) return

            // Natural crystals develop secondary needles unevenly; branches
            // are never distributed at uniform intervals or mirrored exactly.
            val needles = if (order == 4) 4 else if (order == 3) 3 else 2
            repeat(needles) { n ->
                val along = (.19f + n * .19f + random.nextFloat() * .08f)
                    .coerceAtMost(.88f)
                val bx = x + (tipX - x) * along
                val by = y + (tipY - y) * along
                val spread = (.47 + random.nextDouble() * .65)
                val side = if ((n + random.nextInt(2)) % 2 == 0) -1 else 1
                val twig = length * (.22f + random.nextFloat() * .15f)
                grow(bx, by, angle + side * spread, twig,
                    order - 1, brightness * (.62f + random.nextFloat() * .19f))
                if (order >= 3 && random.nextFloat() > .38f) {
                    grow(bx, by, angle - side * spread * .85, twig * .70f,
                        order - 2, brightness * .47f)
                }
            }
        }

        // Frost begins with delicate grains and fans out from local patches.
        // Concentrate powder at the physical perimeter, not over the content.
        val band = when (surface) {
            WinterSurface.SCREEN -> 10f
            WinterSurface.NAVIGATION -> 8f
            WinterSurface.PIN_KEY -> 3f
            WinterSurface.DIALOG -> 11f
            WinterSurface.CARD -> 10f
        } * density
        val perimeter = 2f * (width + height)
        val grains = (perimeter / (3.8f * density)).toInt().coerceIn(28, 540)
        repeat(grains) {
            val side = random.nextInt(4)
            val x = when (side) {
                0, 1 -> random.nextFloat() * width
                2 -> random.nextFloat() * band
                else -> width - random.nextFloat() * band
            }
            val y = when (side) {
                0 -> random.nextFloat() * band
                1 -> height - random.nextFloat() * band
                else -> random.nextFloat() * height
            }
            val distance = when (side) {
                0 -> y
                1 -> height - y
                2 -> x
                else -> width - x
            }
            val fade = (1f - distance / band).coerceIn(0f, 1f)
            val grain = (.12f + random.nextFloat() * .43f) * density
            powder.color = ice((18f + random.nextFloat() * 98f) * fade)
            canvas.drawCircle(x, y, grain, powder)
        }

        if (surface == WinterSurface.PIN_KEY) return

        val isScreen = surface == WinterSurface.SCREEN
        val base = if (isScreen) 21f else if (surface == WinterSurface.DIALOG) 19f
            else if (surface == WinterSurface.NAVIGATION) 12f else 16f
        val growLength = base * density
        // Corner growths are purposefully different in size and direction.
        val cornerInset = (if (isScreen) 4.5f else 6.3f) * density
        val corners = arrayOf(
            Triple(cornerInset, cornerInset, PI / 4),
            Triple(width - cornerInset, cornerInset, PI * 3 / 4),
            Triple(cornerInset, height - cornerInset, -PI / 4),
            Triple(width - cornerInset, height - cornerInset, -PI * 3 / 4)
        )
        corners.forEachIndexed { i, corner ->
            val count = 2 + random.nextInt(2)
            repeat(count) {
                val startAngle = corner.third + (random.nextDouble() - .5) * 1.20
                val length = growLength * (.58f + random.nextFloat() * .50f)
                grow(corner.first, corner.second, startAngle, length,
                    if (i == 0 && surface == WinterSurface.CARD) 4 else 3,
                    145f + random.nextFloat() * 67f)
            }
        }

        // Independent crystal colonies on the top and bottom, no repeated
        // equally spaced vertical marks from rc3.
        val horizontal = (width / (100f * density)).toInt().coerceIn(1, 5)
        repeat(horizontal) {
            val x = (width * (.10f + random.nextFloat() * .80f))
            val top = random.nextBoolean()
            val y = if (top) 2.4f * density else height - 2.4f * density
            val angle = if (top) PI / 2 else -PI / 2
            grow(x, y, angle + (random.nextDouble() - .5) * 1.35,
                growLength * (.43f + random.nextFloat() * .45f), 3,
                85f + random.nextFloat() * 88f)
        }
        if (height > 92f * density) {
            val vertical = if (isScreen) 5 else 2
            repeat(vertical) {
                val left = random.nextBoolean()
                val x = if (left) 2.2f * density else width - 2.2f * density
                val y = height * (.10f + random.nextFloat() * .80f)
                val angle = if (left) 0.0 else PI
                grow(x, y, angle + (random.nextDouble() - .5) * 1.0,
                    growLength * (.38f + random.nextFloat() * .34f),
                    2, 72f + random.nextFloat() * 67f)
            }
        }
    }
}
