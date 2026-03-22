package com.transsion.rgbtester.data.model

import android.graphics.Color
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * RGB Color Model
 *
 * Represents an RGB color with brightness control
 */
@Parcelize
data class RGBColor(
    val red: Int = 0,      // 0-255
    val green: Int = 0,    // 0-255
    val blue: Int = 0,     // 0-255
    val brightness: Int = 100  // 0-100 percentage
) : Parcelable {

    val hexColor: String
        get() = String.format("#%02X%02X%02X", red, green, blue)

    val androidColor: Int
        get() = Color.rgb(red, green, blue)

    val brightnessMultiplier: Float
        get() = brightness / 100f

    /**
     * Get color values with brightness applied
     */
    fun getAdjustedColor(): RGBColor {
        return RGBColor(
            red = (red * brightnessMultiplier).toInt().coerceIn(0, 255),
            green = (green * brightnessMultiplier).toInt().coerceIn(0, 255),
            blue = (blue * brightnessMultiplier).toInt().coerceIn(0, 255),
            brightness = 100
        )
    }

    companion object {
        // Preset colors
        val RED = RGBColor(255, 0, 0)
        val GREEN = RGBColor(0, 255, 0)
        val BLUE = RGBColor(0, 0, 255)
        val WHITE = RGBColor(255, 255, 255)
        val YELLOW = RGBColor(255, 255, 0)
        val CYAN = RGBColor(0, 255, 255)
        val MAGENTA = RGBColor(255, 0, 255)
        val ORANGE = RGBColor(255, 165, 0)
        val PURPLE = RGBColor(128, 0, 128)
        val PINK = RGBColor(255, 192, 203)
        val OFF = RGBColor(0, 0, 0)

        /**
         * Create from Android Color int
         */
        fun fromColor(color: Int): RGBColor {
            return RGBColor(
                red = Color.red(color),
                green = Color.green(color),
                blue = Color.blue(color)
            )
        }

        /**
         * Create from Int (alias for fromColor)
         */
        fun fromInt(color: Int): RGBColor = fromColor(color)

        /**
         * Create from hex string
         */
        fun fromHex(hex: String): RGBColor? {
            return try {
                val color = Color.parseColor(hex)
                fromColor(color)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * LED Effect Mode
 */
enum class LEDEffect(val displayName: String, val triggerValue: String) {
    OFF("Off", "none"),
    STATIC("Static", "none"),
    BREATHING("Breathing", "breathing"),
    HEARTBEAT("Heartbeat", "heartbeat"),
    FLASH("Flash", "flash"),
    RAINBOW("Rainbow", "rainbow"),
    MUSIC("Music", "music"),
    TIMER("Timer", "timer"),
    TRANSIENT("Transient", "transient");

    companion object {
        fun fromTrigger(trigger: String): LEDEffect {
            return entries.find { it.triggerValue == trigger } ?: OFF
        }
    }
}

/**
 * LED State Model
 */
@Parcelize
data class LEDState(
    val color: RGBColor = RGBColor.OFF,
    val effect: LEDEffect = LEDEffect.STATIC,
    val speed: Int = 50,        // 0-100 for effect speed
    val isOn: Boolean = false
) : Parcelable

/**
 * LED Device Information
 */
@Parcelize
data class LEDDevice(
    val name: String,
    val path: String,
    val maxBrightness: Int = 255,
    val availableTriggers: List<String> = emptyList(),
    val currentTrigger: String = "none",
    val currentBrightness: Int = 0,
    val isAvailable: Boolean = false
) : Parcelable {

    val displayName: String
        get() = name.substringAfterLast("/")

    val hasRGBSupport: Boolean
        get() = name.contains("red", ignoreCase = true) ||
                name.contains("green", ignoreCase = true) ||
                name.contains("blue", ignoreCase = true) ||
                name.contains("rgb", ignoreCase = true)
}

/**
 * RGB Device Group (R, G, B channels)
 */
data class RGBDeviceGroup(
    val name: String,
    val redPath: LEDDevice?,
    val greenPath: LEDDevice?,
    val bluePath: LEDDevice?,
    val singlePath: LEDDevice?
) {
    val isRGBSeparate: Boolean
        get() = redPath != null && greenPath != null && bluePath != null

    val isSingleRGB: Boolean
        get() = singlePath != null

    val isAvailable: Boolean
        get() = isRGBSeparate || isSingleRGB
}

/**
 * Effect Configuration
 */
@Parcelize
data class EffectConfig(
    val effect: LEDEffect,
    val color: RGBColor,
    val speed: Int = 50,           // 0-100
    val repeatCount: Int = -1,     // -1 for infinite
    val delayOn: Int = 500,        // ms
    val delayOff: Int = 500        // ms
) : Parcelable

/**
 * Custom Effect Pattern Step
 */
@Parcelize
data class EffectStep(
    val color: RGBColor,
    val durationMs: Long,
    val transitionMs: Long = 0
) : Parcelable

/**
 * Custom Effect Pattern
 */
@Parcelize
data class CustomPattern(
    val name: String,
    val steps: List<EffectStep>,
    val repeat: Boolean = true
) : Parcelable

/**
 * Test Result
 */
data class TestResult(
    val success: Boolean,
    val path: String,
    val operation: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
