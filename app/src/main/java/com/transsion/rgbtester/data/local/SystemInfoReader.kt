package com.transsion.rgbtester.data.local

import android.os.Build
import android.util.Log
import java.io.File

/**
 * System Information Reader
 * 
 * Reads device and LED information from system files without requiring root access.
 * Uses /proc and /sys filesystems that are readable by normal apps.
 */
class SystemInfoReader {

    companion object {
        private const val TAG = "SystemInfoReader"
        private const val LED_CLASS_PATH = "/sys/class/leds"
    }

    data class DeviceInfo(
        val model: String,
        val manufacturer: String,
        val platform: String,
        val androidVersion: String,
        val kernelVersion: String,
        val buildId: String
    )

    data class LEDInfo(
        val variant: String,
        val fwVersion: String,
        val ledType: String,
        val controller: String,
        val rgbDriver: String,
        val pdlcController: String,
        val sysfsPaths: List<String>,
        val modules: List<String>
    )

    data class ServiceStatus(
        val lightHal: String,
        val tcLedService: String
    )

    /**
     * Get basic device information (no root required)
     */
    fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            model = getSystemProperty("ro.product.model") ?: Build.MODEL,
            manufacturer = getSystemProperty("ro.product.manufacturer") ?: Build.MANUFACTURER,
            platform = getSystemProperty("ro.board.platform") ?: getSystemProperty("ro.hardware") ?: "Unknown",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            kernelVersion = getKernelVersion(),
            buildId = Build.DISPLAY ?: Build.ID
        )
    }

    /**
     * Get LED information (attempts to read without root)
     */
    fun getLEDInfo(): LEDInfo {
        val sysfsPaths = findLEDPaths()
        val modules = findLoadedModules()
        
        // Try to detect variant from fwversion
        val fwVersion = readFirmwareVersion(sysfsPaths)
        val ledType = detectLEDType(fwVersion)
        val variant = detectVariant(fwVersion)
        
        return LEDInfo(
            variant = variant,
            fwVersion = fwVersion,
            ledType = ledType,
            controller = detectLEDController(sysfsPaths, modules),
            rgbDriver = detectRGBDriver(modules, sysfsPaths),
            pdlcController = detectPDLCController(modules),
            sysfsPaths = sysfsPaths,
            modules = modules
        )
    }

    /**
     * Get service status (may require root for full info)
     */
    fun getServiceStatus(): ServiceStatus {
        return ServiceStatus(
            lightHal = checkServiceStatus("lights-mtk-default", "android.hardware.lights"),
            tcLedService = checkServiceStatus("tc_led", "transsion-led")
        )
    }

    private fun getSystemProperty(key: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("getprop $key")
            val result = process.inputStream.bufferedReader().readText().trim()
            if (result.isNotEmpty()) result else null
        } catch (e: Exception) {
            Log.d(TAG, "Failed to get prop $key: ${e.message}")
            null
        }
    }

    private fun getKernelVersion(): String {
        return try {
            File("/proc/version").readText().trim().take(80)
        } catch (e: Exception) {
            System.getProperty("os.version") ?: "Unknown"
        }
    }

    private fun findLEDPaths(): List<String> {
        val paths = mutableListOf<String>()
        
        // Check /sys/class/leds
        try {
            val ledsDir = File(LED_CLASS_PATH)
            if (ledsDir.exists() && ledsDir.isDirectory) {
                ledsDir.listFiles()?.forEach { ledDir ->
                    val name = ledDir.name
                    if (name.contains("backcover", ignoreCase = true) ||
                        name.contains("hk32", ignoreCase = true) ||
                        name.contains("aw20144", ignoreCase = true) ||
                        name.contains("tc_led", ignoreCase = true) ||
                        name.contains("red", ignoreCase = true) ||
                        name.contains("green", ignoreCase = true) ||
                        name.contains("blue", ignoreCase = true)) {
                        paths.add(ledDir.absolutePath)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error reading /sys/class/leds: ${e.message}")
        }

        // Check I2C devices for HK32F0301
        try {
            val i2cDir = File("/sys/bus/i2c/devices")
            if (i2cDir.exists() && i2cDir.isDirectory) {
                i2cDir.listFiles()?.forEach { deviceDir ->
                    // Check for fwversion file
                    val fwFile = File(deviceDir, "fwversion")
                    if (fwFile.exists()) {
                        paths.add(deviceDir.absolutePath)
                    }
                    // Check name file
                    val nameFile = File(deviceDir, "name")
                    if (nameFile.exists()) {
                        val name = nameFile.readText().trim()
                        if (name.contains("hk32", ignoreCase = true) ||
                            name.contains("aw20144", ignoreCase = true)) {
                            paths.add(deviceDir.absolutePath)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error reading I2C devices: ${e.message}")
        }

        return paths.distinct()
    }

    private fun readFirmwareVersion(sysfsPaths: List<String>): String {
        for (path in sysfsPaths) {
            try {
                val fwFile = File(path, "fwversion")
                if (fwFile.exists()) {
                    val content = fwFile.readText().trim()
                    if (content.isNotEmpty()) {
                        return try {
                            "0x${content.toIntOrNull(16)?.toString(16) ?: content}"
                        } catch (e: Exception) {
                            content
                        }
                    }
                }
            } catch (e: Exception) {
                // Continue to next path
            }
        }
        return "Not detected"
    }

    private fun detectLEDType(fwVersion: String): String {
        return when {
            fwVersion.contains("0x0e", ignoreCase = true) -> "White LED Only"
            fwVersion.contains("0x0d", ignoreCase = true) -> "RGB LED (v1)"
            fwVersion.contains("0x08", ignoreCase = true) -> "RGB LED (v2)"
            fwVersion != "Not detected" -> "Unknown ($fwVersion)"
            else -> "Unknown"
        }
    }

    private fun detectVariant(fwVersion: String): String {
        return when {
            fwVersion.contains("0x0e", ignoreCase = true) -> "White LED Variant"
            fwVersion.contains("0x0d", ignoreCase = true) -> "RGB Variant v1"
            fwVersion.contains("0x08", ignoreCase = true) -> "RGB Variant v2"
            else -> "Unknown Variant"
        }
    }

    private fun findLoadedModules(): List<String> {
        val modules = mutableListOf<String>()
        
        try {
            val modulesFile = File("/proc/modules")
            if (modulesFile.exists()) {
                modulesFile.readLines().forEach { line ->
                    val moduleName = line.split(" ").firstOrNull() ?: ""
                    if (moduleName.contains("led", ignoreCase = true) ||
                        moduleName.contains("hk32", ignoreCase = true) ||
                        moduleName.contains("aw20144", ignoreCase = true) ||
                        moduleName.contains("tc_led", ignoreCase = true)) {
                        modules.add(moduleName)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error reading /proc/modules: ${e.message}")
        }

        return modules
    }

    private fun detectLEDController(sysfsPaths: List<String>, modules: List<String>): String {
        return when {
            modules.any { it.contains("hk32f0301", ignoreCase = true) } -> "HK32F0301 MCU"
            sysfsPaths.any { it.contains("hk32", ignoreCase = true) } -> "HK32F0301 MCU (detected)"
            modules.any { it.contains("aw20144", ignoreCase = true) } -> "AW20144"
            else -> "Not detected"
        }
    }

    private fun detectRGBDriver(modules: List<String>, sysfsPaths: List<String>): String {
        return when {
            modules.any { it.contains("aw20144", ignoreCase = true) } -> "AW20144 RGB LED Driver"
            sysfsPaths.any { it.contains("aw20144", ignoreCase = true) } -> "AW20144 RGB LED Driver (detected)"
            else -> "Not detected"
        }
    }

    private fun detectPDLCController(modules: List<String>): String {
        return when {
            modules.any { it.contains("aw862", ignoreCase = true) } -> "AW8622x PDLC Controller"
            else -> "Not detected"
        }
    }

    private fun checkServiceStatus(vararg serviceNames: String): String {
        try {
            val process = Runtime.getRuntime().exec("service list")
            val output = process.inputStream.bufferedReader().readText()
            
            for (name in serviceNames) {
                if (output.contains(name, ignoreCase = true)) {
                    return "Running"
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error checking service status: ${e.message}")
        }
        
        // Try checking init service status
        try {
            for (name in serviceNames) {
                val process = Runtime.getRuntime().exec("getprop init.svc.$name")
                val result = process.inputStream.bufferedReader().readText().trim()
                if (result == "running") {
                    return "Running"
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error checking init service: ${e.message}")
        }
        
        return "Unknown"
    }
}
