package com.transsion.rgbtester.data.local

import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

/**
 * System Information Reader for RGB Back Cover Debugging
 *
 * Comprehensive system diagnostics for debugging back cover lighting issues
 * on custom ROMs. Requires root access for full functionality.
 *
 * Checks:
 * - Device info & variant detection
 * - Init services (.rc files)
 * - HAL services (android.hardware.lights, etc.)
 * - Kernel drivers & modules
 * - Shared libraries (.so files)
 * - SELinux status & policies
 * - I2C bus enumeration
 * - Device tree/overlay info
 * - Sysfs paths analysis
 * - Stock ROM comparison
 * - Vendor partition status
 * - Binder services
 * - HIDL/AIDL interfaces
 */
class SystemInfoReader {

    companion object {
        private const val TAG = "SystemInfoReader"
        private const val LED_CLASS_PATH = "/sys/class/leds"
        private const val I2C_BUS_PATH = "/sys/bus/i2c/devices"
        private const val DEVICETREE_PATH = "/sys/firmware/devicetree"

        // Stock ROM properties for comparison
        private val STOCK_PROPS = mapOf(
            "ro.product.manufacturer" to "INFINIX",
            "ro.product.brand" to "Infinix",
            "ro.build.ota.version" to "X6873-H6117DEFGH-",
            "ro.vendor.product.device" to "X6873"
        )
    }

    // Device Information
    data class DeviceInfo(
        val model: String,
        val manufacturer: String,
        val platform: String,
        val androidVersion: String,
        val kernelVersion: String,
        val buildId: String,
        val buildType: String,
        val buildFingerprint: String,
        val romType: RomType,
        val stockPropsMatch: Boolean,
        val vendorFingerprint: String,
        val otaVersion: String
    )

    enum class RomType {
        STOCK, CUSTOM, UNKNOWN
    }

    // LED Hardware Information
    data class LEDInfo(
        val variant: String,
        val fwVersion: String,
        val ledType: String,
        val controller: String,
        val rgbDriver: String,
        val pdlcController: String,
        val sysfsPaths: List<String>,
        val modules: List<String>,
        val i2cDevices: List<I2CDevice>,
        val dtCompatible: String,
        val ledAttributes: Map<String, String>,
        val powerState: String
    )

    data class I2CDevice(
        val path: String,
        val name: String,
        val address: String,
        val driver: String,
        val modalias: String
    )

    // Init Services Status
    data class InitServicesInfo(
        val services: List<ServiceStatus>,
        val rcFilesFound: List<RcFileInfo>,
        val tcLedEnabled: Boolean,
        val lightHalEnabled: Boolean,
        val serviceAnalysis: String
    )

    data class ServiceStatus(
        val name: String,
        val status: String, // "running", "stopped", "not_found"
        val pid: Int?,
        val rcFile: String?
    )

    data class RcFileInfo(
        val path: String,
        val serviceName: String,
        val content: String
    )

    // HAL Services Status
    data class HALInfo(
        val lightHal: HALServiceStatus,
        val vibratorHal: HALServiceStatus,
        val transsionHal: HALServiceStatus,
        val allHals: List<HALServiceStatus>,
        val hidlInterfaces: List<HIDLInterface>,
        val aidlInterfaces: List<AIDLInterface>
    )

    data class HALServiceStatus(
        val name: String,
        val running: Boolean,
        val pid: Int?,
        val interfaceName: String
    )

    data class HIDLInterface(
        val name: String,
        val available: Boolean,
        val version: String
    )

    data class AIDLInterface(
        val name: String,
        val available: Boolean,
        val version: Int
    )

    // Driver Information
    data class DriverInfo(
        val modules: List<ModuleInfo>,
        val hk32Driver: DriverStatus,
        val aw20144Driver: DriverStatus,
        val aw862Driver: DriverStatus,
        val gpioStatus: String,
        val i2cStatus: String,
        val kernelConfig: Map<String, String>,
        val cmdlineParams: Map<String, String>
    )

    data class ModuleInfo(
        val name: String,
        val size: Int,
        val usedBy: Int,
        val dependencies: String,
        val state: String
    )

    data class DriverStatus(
        val loaded: Boolean,
        val path: String?,
        val version: String?,
        val params: Map<String, String>,
        val initstate: String?
    )

    // Libraries Information
    data class LibrariesInfo(
        val systemLibs: List<LibInfo>,
        val vendorLibs: List<LibInfo>,
        val ledLibsFound: List<LibInfo>,
        val missingLibs: List<String>,
        val vendorPartitionStatus: String
    )

    data class LibInfo(
        val name: String,
        val path: String,
        val size: Long,
        val checksum: String?
    )

    // SELinux Information
    data class SELinuxInfo(
        val mode: String, // "Enforcing", "Permissive", "Disabled"
        val policyVersion: String,
        val booleans: Map<String, Boolean>,
        val ledRelatedContexts: List<String>,
        val denials: List<SELinuxDenial>,
        val policyFileExists: Boolean
    )

    data class SELinuxDenial(
        val timestamp: String,
        val scontext: String,
        val tcontext: String,
        val tclass: String,
        val permission: String
    )

    // Device Tree Information
    data class DeviceTreeInfo(
        val compatible: String,
        val model: String,
        val ledNode: String?,
        val i2cNodes: List<String>,
        val ledPinctrl: String?,
        val overlays: List<OverlayInfo>,
        val ledDtsContent: String?
    )

    data class OverlayInfo(
        val name: String,
        val applied: Boolean,
        val path: String
    )

    // Vendor & Partition Info
    data class VendorInfo(
        val vendorPartitionMounted: Boolean,
        val vendorPartitionType: String,
        val vendorProps: Map<String, String>,
        val odmPartitionMounted: Boolean,
        val productPartitionMounted: Boolean,
        val missingVendorFiles: List<String>
    )

    // Kernel Information
    data class KernelInfo(
        val version: String,
        val cmdline: String,
        val config: Map<String, String>,
        val modulesLoaded: Boolean,
        val initramfsType: String
    )

    // Complete System Diagnostics
    data class SystemDiagnostics(
        val deviceInfo: DeviceInfo,
        val ledInfo: LEDInfo,
        val initServices: InitServicesInfo,
        val halInfo: HALInfo,
        val driverInfo: DriverInfo,
        val librariesInfo: LibrariesInfo,
        val selinuxInfo: SELinuxInfo,
        val deviceTreeInfo: DeviceTreeInfo,
        val vendorInfo: VendorInfo,
        val kernelInfo: KernelInfo
    )

    /**
     * Execute root shell command
     */
    private fun executeRoot(vararg commands: String): ShellResult {
        val process: Process
        val outputLines = mutableListOf<String>()
        val errorLines = mutableListOf<String>()
        var exitCode = -1

        try {
            process = Runtime.getRuntime().exec("su")

            DataOutputStream(process.outputStream).use { outputStream ->
                for (command in commands) {
                    outputStream.writeBytes("$command\n")
                    outputStream.flush()
                }
                outputStream.writeBytes("exit\n")
                outputStream.flush()
            }

            Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            outputLines.add(line!!)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading stdout", e)
                }
            }.start()

            Thread {
                try {
                    BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            errorLines.add(line!!)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading stderr", e)
                }
            }.start()

            exitCode = process.waitFor()
            Thread.sleep(50)

        } catch (e: Exception) {
            Log.e(TAG, "Error executing root commands", e)
            errorLines.add(e.message ?: "Unknown error")
        }

        return ShellResult(
            success = exitCode == 0 && errorLines.isEmpty(),
            exitCode = exitCode,
            output = outputLines,
            error = errorLines
        )
    }

    data class ShellResult(
        val success: Boolean,
        val exitCode: Int,
        val output: List<String>,
        val error: List<String>
    ) {
        val outputText: String get() = output.joinToString("\n")
    }

    // ==================== Device Information ====================

    fun getDeviceInfo(): DeviceInfo {
        val manufacturer = getSystemProperty("ro.product.manufacturer") ?: Build.MANUFACTURER
        val brand = getSystemProperty("ro.product.brand") ?: ""
        val buildType = getSystemProperty("ro.build.type") ?: "unknown"
        val fingerprint = getSystemProperty("ro.build.fingerprint") ?: Build.FINGERPRINT
        val vendorFingerprint = getSystemProperty("ro.vendor.build.fingerprint") ?: ""
        val otaVersion = getSystemProperty("ro.build.ota.version") ?: ""

        val romType = detectRomType(manufacturer, brand, buildType, fingerprint)
        val stockPropsMatch = checkStockProps()

        return DeviceInfo(
            model = getSystemProperty("ro.product.model") ?: Build.MODEL,
            manufacturer = manufacturer,
            platform = getSystemProperty("ro.board.platform") ?: getSystemProperty("ro.hardware") ?: "Unknown",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            kernelVersion = getKernelVersion(),
            buildId = Build.DISPLAY ?: Build.ID,
            buildType = buildType,
            buildFingerprint = fingerprint,
            romType = romType,
            stockPropsMatch = stockPropsMatch,
            vendorFingerprint = vendorFingerprint,
            otaVersion = otaVersion
        )
    }

    private fun checkStockProps(): Boolean {
        var matchCount = 0
        STOCK_PROPS.forEach { (key, expectedPrefix) ->
            val actual = getSystemProperty(key) ?: ""
            if (actual.startsWith(expectedPrefix, ignoreCase = true)) {
                matchCount++
            }
        }
        return matchCount >= 2
    }

    private fun detectRomType(manufacturer: String, brand: String, buildType: String, fingerprint: String): RomType {
        val lowerFingerprint = fingerprint.lowercase()
        val lowerManufacturer = manufacturer.lowercase()
        val lowerBrand = brand.lowercase()

        // Check for custom ROM indicators
        val customRomIndicators = listOf("lineage", "pixel", "crdroid", "aosp", "custom", "userdebug", "eng",
            "evolution", "pixelos", "cherish", "derived", "projectelixir", "arrow", "clover", "banana")
        val stockRomIndicators = listOf("transsion", "infinix", "tecno", "itel", "release-keys", "X6873")

        val hasCustomIndicator = customRomIndicators.any { lowerFingerprint.contains(it) || buildType.contains(it) }
        val hasStockIndicator = stockRomIndicators.any {
            lowerFingerprint.contains(it) || lowerManufacturer.contains(it) || lowerBrand.contains(it)
        }

        return when {
            hasCustomIndicator && !hasStockIndicator -> RomType.CUSTOM
            hasStockIndicator -> RomType.STOCK
            else -> RomType.UNKNOWN
        }
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
            File("/proc/version").readText().trim().take(150)
        } catch (e: Exception) {
            System.getProperty("os.version") ?: "Unknown"
        }
    }

    // ==================== LED Hardware Information ====================

    fun getLEDInfo(): LEDInfo {
        val sysfsPaths = findLEDPaths()
        val modules = findLoadedModules()
        val i2cDevices = enumerateI2CDevices()
        val dtCompatible = getDeviceTreeCompatible()
        val ledAttributes = getLEDAttributes(sysfsPaths)
        val powerState = getLEDPowerState()

        val fwVersion = readFirmwareVersion(sysfsPaths)
        val ledType = detectLEDType(fwVersion)
        val variant = detectVariant(fwVersion)

        return LEDInfo(
            variant = variant,
            fwVersion = fwVersion,
            ledType = ledType,
            controller = detectLEDController(sysfsPaths, modules, i2cDevices),
            rgbDriver = detectRGBDriver(modules, sysfsPaths, i2cDevices),
            pdlcController = detectPDLCController(modules),
            sysfsPaths = sysfsPaths,
            modules = modules.map { it.name },
            i2cDevices = i2cDevices,
            dtCompatible = dtCompatible,
            ledAttributes = ledAttributes,
            powerState = powerState
        )
    }

    private fun getLEDAttributes(sysfsPaths: List<String>): Map<String, String> {
        val attributes = mutableMapOf<String, String>()

        sysfsPaths.forEach { path ->
            val attrFiles = listOf(
                "brightness", "max_brightness", "trigger", "delay_on", "delay_off",
                "color", "multi_intensity", "hw_pattern", "pattern", "breathing",
                "effect", "effect_duration", "effect_color", "fwversion", "led_type",
                "led_mode", "power_state", "enabled", "reg_value", "i2c_addr"
            )

            attrFiles.forEach { attr ->
                val result = executeRoot("cat $path/$attr 2>/dev/null")
                if (result.success && result.output.isNotEmpty()) {
                    val key = "${path.substringAfterLast("/")}_$attr"
                    attributes[key] = result.outputText.trim().take(100)
                }
            }
        }

        return attributes
    }

    private fun getLEDPowerState(): String {
        // Check regulator status
        val regResult = executeRoot("cat /sys/class/regulator/*/microvolts 2>/dev/null; cat /sys/class/regulator/*/status 2>/dev/null")
        if (regResult.success && regResult.output.isNotEmpty()) {
            return "Regulators: ${regResult.output.size} found"
        }

        // Check GPIO state for LED
        val gpioResult = executeRoot("cat /sys/kernel/debug/gpio 2>/dev/null | grep -i 'led\\|backcover\\|hk32'")
        if (gpioResult.success && gpioResult.output.isNotEmpty()) {
            return "GPIO: ${gpioResult.outputText.take(100).replace("\n", " ")}"
        }

        return "Unknown"
    }

    private fun findLEDPaths(): List<String> {
        val paths = mutableListOf<String>()

        // Check /sys/class/leds
        try {
            val ledsDir = File(LED_CLASS_PATH)
            if (ledsDir.exists() && ledsDir.isDirectory) {
                ledsDir.listFiles()?.forEach { ledDir ->
                    val name = ledDir.name.lowercase()
                    if (name.contains("backcover") || name.contains("hk32") ||
                        name.contains("aw20144") || name.contains("tc_led") ||
                        name.contains("red") || name.contains("green") ||
                        name.contains("blue") || name.contains("rgb") ||
                        name.contains("white") || name.contains("led")) {
                        paths.add(ledDir.absolutePath)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error reading /sys/class/leds: ${e.message}")
        }

        // Check I2C devices for HK32F0301
        val result = executeRoot("find /sys/bus/i2c -name 'fwversion' -o -name 'name' 2>/dev/null")
        result.output.forEach { path ->
            try {
                val parent = File(path).parentFile
                if (parent != null && parent.exists()) {
                    val nameFile = File(parent, "name")
                    if (nameFile.exists()) {
                        val name = nameFile.readText().trim()
                        if (name.contains("hk32", ignoreCase = true) ||
                            name.contains("aw20144", ignoreCase = true) ||
                            name.contains("tc_led", ignoreCase = true)) {
                            paths.add(parent.absolutePath)
                        }
                    }
                    val fwFile = File(parent, "fwversion")
                    if (fwFile.exists()) {
                        paths.add(parent.absolutePath)
                    }
                }
            } catch (e: Exception) {
                // Continue
            }
        }

        // Check platform devices
        val platformResult = executeRoot("find /sys/devices/platform -name '*led*' -o -name '*backcover*' 2>/dev/null")
        platformResult.output.forEach { path ->
            if (File(path).isDirectory) {
                paths.add(path)
            }
        }

        return paths.distinct()
    }

    private fun readFirmwareVersion(sysfsPaths: List<String>): String {
        for (path in sysfsPaths) {
            val result = executeRoot("cat $path/fwversion 2>/dev/null")
            if (result.success && result.output.isNotEmpty()) {
                val content = result.outputText.trim()
                if (content.isNotEmpty()) {
                    return try {
                        "0x${content.toIntOrNull(16)?.toString(16) ?: content}"
                    } catch (e: Exception) {
                        content
                    }
                }
            }
        }
        return "Not detected"
    }

    private fun detectLEDType(fwVersion: String): String {
        return when {
            fwVersion.contains("0x0e", ignoreCase = true) -> "White LED Only (Hardware)"
            fwVersion.contains("0x0d", ignoreCase = true) -> "RGB LED v1 (Hardware)"
            fwVersion.contains("0x08", ignoreCase = true) -> "RGB LED v2 (Hardware)"
            fwVersion != "Not detected" -> "Unknown ($fwVersion)"
            else -> "Unknown - Check I2C"
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

    private fun findLoadedModules(): List<ModuleInfo> {
        val modules = mutableListOf<ModuleInfo>()

        try {
            val modulesFile = File("/proc/modules")
            if (modulesFile.exists()) {
                modulesFile.readLines().forEach { line ->
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        val name = parts[0]
                        if (name.contains("led", ignoreCase = true) ||
                            name.contains("hk32", ignoreCase = true) ||
                            name.contains("aw20144", ignoreCase = true) ||
                            name.contains("aw862", ignoreCase = true) ||
                            name.contains("tc_led", ignoreCase = true) ||
                            name.contains("pdlc", ignoreCase = true) ||
                            name.contains("i2c", ignoreCase = true) ||
                            name.contains("gpio", ignoreCase = true) ||
                            name.contains("pwm", ignoreCase = true)) {
                            modules.add(ModuleInfo(
                                name = name,
                                size = parts[1].toIntOrNull() ?: 0,
                                usedBy = parts[2].toIntOrNull() ?: 0,
                                dependencies = if (parts.size > 3) parts[3] else "",
                                state = if (parts.size > 4) parts[4] else "live"
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error reading /proc/modules: ${e.message}")
        }

        return modules
    }

    private fun enumerateI2CDevices(): List<I2CDevice> {
        val devices = mutableListOf<I2CDevice>()

        val result = executeRoot("ls -1 /sys/bus/i2c/devices/ 2>/dev/null")
        result.output.forEach { deviceDir ->
            if (deviceDir.matches(Regex("\\d+-\\d+"))) {
                val path = "/sys/bus/i2c/devices/$deviceDir"
                val nameResult = executeRoot("cat $path/name 2>/dev/null")
                val name = nameResult.outputText.trim()

                // Extract address from device directory name
                val address = deviceDir.substringAfterLast("-")

                // Check for driver
                val driverResult = executeRoot("ls -l $path/driver 2>/dev/null")
                val driver = if (driverResult.success && driverResult.output.isNotEmpty()) {
                    driverResult.output.firstOrNull()?.substringAfterLast("/") ?: "none"
                } else {
                    "none"
                }

                // Get modalias
                val modaliasResult = executeRoot("cat $path/modalias 2>/dev/null")
                val modalias = modaliasResult.outputText.trim()

                // Filter LED-related I2C devices or common addresses
                if (name.contains("hk32", ignoreCase = true) ||
                    name.contains("aw20144", ignoreCase = true) ||
                    name.contains("aw862", ignoreCase = true) ||
                    name.contains("tc_led", ignoreCase = true) ||
                    name.contains("led", ignoreCase = true) ||
                    address == "0050" || // HK32F0301 typical address
                    address == "003c" || // AW20144 typical address
                    address == "005a") { // AW862 typical address
                    devices.add(I2CDevice(
                        path = path,
                        name = name.ifEmpty { "Unknown" },
                        address = "0x${address.toIntOrNull(16)?.toString(16) ?: address}",
                        driver = driver,
                        modalias = modalias
                    ))
                }
            }
        }

        return devices
    }

    private fun detectLEDController(sysfsPaths: List<String>, modules: List<ModuleInfo>, i2cDevices: List<I2CDevice>): String {
        // Check I2C devices first
        val hk32I2C = i2cDevices.find { it.name.contains("hk32", ignoreCase = true) || it.address == "0x50" }
        if (hk32I2C != null) {
            return "HK32F0301 MCU @ I2C ${hk32I2C.address} [${hk32I2C.driver}]"
        }

        // Check modules
        val hk32Module = modules.find { it.name.contains("hk32f0301", ignoreCase = true) }
        if (hk32Module != null) {
            return "HK32F0301 MCU (module loaded)"
        }

        // Check sysfs
        if (sysfsPaths.any { it.contains("hk32", ignoreCase = true) }) {
            return "HK32F0301 MCU (sysfs detected)"
        }

        return "Not detected"
    }

    private fun detectRGBDriver(modules: List<ModuleInfo>, sysfsPaths: List<String>, i2cDevices: List<I2CDevice>): String {
        // Check I2C
        val aw20144I2C = i2cDevices.find { it.name.contains("aw20144", ignoreCase = true) || it.address == "0x3c" }
        if (aw20144I2C != null) {
            return "AW20144 @ I2C ${aw20144I2C.address} [${aw20144I2C.driver}]"
        }

        // Check modules
        val aw20144Module = modules.find { it.name.contains("aw20144", ignoreCase = true) }
        if (aw20144Module != null) {
            return "AW20144 RGB Driver (module loaded)"
        }

        // Check sysfs
        if (sysfsPaths.any { it.contains("aw20144", ignoreCase = true) }) {
            return "AW20144 RGB Driver (sysfs detected)"
        }

        return "Not detected"
    }

    private fun detectPDLCController(modules: List<ModuleInfo>): String {
        val aw862Module = modules.find { it.name.contains("aw862", ignoreCase = true) }
        if (aw862Module != null) {
            return "AW8622x PDLC Controller"
        }

        // Check if PDLC sysfs exists
        val result = executeRoot("ls /sys/class/pdlc* /sys/devices/platform/*/pdlc* 2>/dev/null")
        if (result.success && result.output.isNotEmpty()) {
            return "PDLC Controller (sysfs detected)"
        }

        return "Not detected"
    }

    private fun getDeviceTreeCompatible(): String {
        val result = executeRoot("cat /sys/firmware/devicetree/base/compatible 2>/dev/null")
        return if (result.success && result.output.isNotEmpty()) {
            result.outputText.replace("\u0000", ", ").trim()
        } else {
            "Not available"
        }
    }

    // ==================== Init Services Information ====================

    fun getInitServicesInfo(): InitServicesInfo {
        val services = mutableListOf<ServiceStatus>()
        val rcFilesFound = mutableListOf<RcFileInfo>()

        // Check for relevant init.rc files
        val rcPaths = listOf(
            "/system/etc/init/",
            "/vendor/etc/init/",
            "/odm/etc/init/",
            "/product/etc/init/",
            "/system_ext/etc/init/"
        )

        val ledRelatedRcs = listOf("tc_led", "light", "transsion", "led", "backcover", "hk32", "rgb")

        rcPaths.forEach { rcPath ->
            val result = executeRoot("ls -1 $rcPath*.rc 2>/dev/null")
            result.output.forEach { rcFile ->
                if (ledRelatedRcs.any { rcFile.contains(it, ignoreCase = true) }) {
                    // Get file content
                    val contentResult = executeRoot("cat $rcFile 2>/dev/null")
                    rcFilesFound.add(RcFileInfo(
                        path = rcFile,
                        serviceName = rcFile.substringAfterLast("/").removeSuffix(".rc"),
                        content = contentResult.outputText.take(2000)
                    ))
                }
            }
        }

        // Check specific services
        val servicesToCheck = listOf(
            "tc_led",
            "tc-led",
            "tcled",
            "lights-mtk-default",
            "android.hardware.lights",
            "android.hardware.light",
            "transsion-led",
            "transsion-light",
            "backcover-led",
            "vendor.light",
            "vendor.lights",
            "hk32-led",
            "rgb-led",
            "led-service"
        )

        servicesToCheck.forEach { serviceName ->
            val status = checkInitService(serviceName)
            services.add(status)
        }

        // Check if tc_led and light HAL are enabled
        val tcLedEnabled = services.any { it.name.contains("tc_led") && it.status == "running" }
        val lightHalEnabled = services.any { it.name.contains("light") && it.status == "running" }

        // Generate analysis
        val analysis = generateServiceAnalysis(services, rcFilesFound, tcLedEnabled, lightHalEnabled)

        return InitServicesInfo(
            services = services,
            rcFilesFound = rcFilesFound,
            tcLedEnabled = tcLedEnabled,
            lightHalEnabled = lightHalEnabled,
            serviceAnalysis = analysis
        )
    }

    private fun generateServiceAnalysis(
        services: List<ServiceStatus>,
        rcFiles: List<RcFileInfo>,
        tcLedEnabled: Boolean,
        lightHalEnabled: Boolean
    ): String {
        val analysis = StringBuilder()

        if (!tcLedEnabled) {
            analysis.append("• tc_led service not running - ")
            val tcLedRc = rcFiles.find { it.path.contains("tc_led", ignoreCase = true) }
            if (tcLedRc != null) {
                analysis.append("RC file exists but service not started\n")
            } else {
                analysis.append("RC file missing - copy from stock ROM\n")
            }
        }

        if (!lightHalEnabled) {
            analysis.append("• Light HAL not running - LED controls won't work\n")
        }

        // Check for missing critical services
        val criticalServices = listOf("tc_led", "android.hardware.lights")
        criticalServices.forEach { svc ->
            val found = services.find { it.name.contains(svc, ignoreCase = true) }
            if (found == null || found.status == "not_found") {
                analysis.append("• $svc service not found\n")
            }
        }

        return analysis.toString().trim()
    }

    private fun checkInitService(serviceName: String): ServiceStatus {
        // Check via getprop
        val propResult = executeRoot("getprop init.svc.$serviceName")
        val status = propResult.outputText.trim()

        var rcFile: String? = null

        if (status == "running") {
            // Get PID
            val pidResult = executeRoot("pidof $serviceName")
            val pid = pidResult.outputText.trim().split(" ").firstOrNull()?.toIntOrNull()

            // Find RC file
            val rcResult = executeRoot("grep -r \"service $serviceName\" /system/etc/init/*.rc /vendor/etc/init/*.rc 2>/dev/null | head -1")
            if (rcResult.success && rcResult.output.isNotEmpty()) {
                rcFile = rcResult.outputText.substringBefore(":")
            }

            return ServiceStatus(serviceName, "running", pid, rcFile)
        } else if (status == "stopped") {
            return ServiceStatus(serviceName, "stopped", null, rcFile)
        }

        // Try service list
        val listResult = executeRoot("service list | grep -i $serviceName")
        if (listResult.success && listResult.output.isNotEmpty()) {
            return ServiceStatus(serviceName, "registered", null, rcFile)
        }

        return ServiceStatus(serviceName, "not_found", null, rcFile)
    }

    // ==================== HAL Services Information ====================

    fun getHALInfo(): HALInfo {
        val allHals = mutableListOf<HALServiceStatus>()

        // Check Light HAL
        val lightHal = checkHALService("android.hardware.lights", "ILights/default")

        // Check Vibrator HAL (sometimes used for LED effects)
        val vibratorHal = checkHALService("android.hardware.vibrator", "IVibrator/default")

        // Check Transsion-specific HALs
        val transsionHal = checkHALService("vendor.transsion.led", "ITranssionLed/default")

        allHals.add(lightHal)
        allHals.add(vibratorHal)
        allHals.add(transsionHal)

        // Check for additional HALs via service list
        val halResult = executeRoot("service list | grep -i 'light\\|led\\|transsion\\|backlight'")
        halResult.output.forEach { line ->
            val match = Regex("(.+):\\s*\\[(.+)\\]").find(line)
            if (match != null) {
                val name = match.groupValues[1].trim()
                if (!allHals.any { it.name == name }) {
                    allHals.add(HALServiceStatus(
                        name = name,
                        running = true,
                        pid = null,
                        interfaceName = match.groupValues[2].trim()
                    ))
                }
            }
        }

        // Check HIDL interfaces
        val hidlInterfaces = checkHIDLInterfaces()

        // Check AIDL interfaces
        val aidlInterfaces = checkAIDLInterfaces()

        return HALInfo(
            lightHal = lightHal,
            vibratorHal = vibratorHal,
            transsionHal = transsionHal,
            allHals = allHals,
            hidlInterfaces = hidlInterfaces,
            aidlInterfaces = aidlInterfaces
        )
    }

    private fun checkHIDLInterfaces(): List<HIDLInterface> {
        val interfaces = mutableListOf<HIDLInterface>()

        val hidlServices = listOf(
            "android.hardware.light@2.0::ILight",
            "android.hardware.light@2.1::ILight",
            "android.hardware.lights@1.0::ILights",
            "vendor.transsion.led@1.0::ITranssionLed"
        )

        hidlServices.forEach { service ->
            val result = executeRoot("service list | grep '$service'")
            val available = result.success && result.output.isNotEmpty()

            val version = when {
                service.contains("@2.1") -> "2.1"
                service.contains("@2.0") -> "2.0"
                service.contains("@1.0") -> "1.0"
                else -> "unknown"
            }

            interfaces.add(HIDLInterface(
                name = service.substringAfter("::"),
                available = available,
                version = version
            ))
        }

        return interfaces
    }

    private fun checkAIDLInterfaces(): List<AIDLInterface> {
        val interfaces = mutableListOf<AIDLInterface>()

        // Check for AIDL lights service
        val result = executeRoot("service list | grep 'android.hardware.lights'")
        if (result.success && result.output.isNotEmpty()) {
            interfaces.add(AIDLInterface(
                name = "android.hardware.lights.ILights",
                available = true,
                version = 1
            ))
        }

        return interfaces
    }

    private fun checkHALService(name: String, interfaceName: String): HALServiceStatus {
        // Check via service manager
        val result = executeRoot("service list | grep -i '$name'")
        val running = result.success && result.output.isNotEmpty()

        // Get PID if running
        var pid: Int? = null
        if (running) {
            val pidResult = executeRoot("pidof $name")
            pid = pidResult.outputText.trim().split(" ").firstOrNull()?.toIntOrNull()
        }

        // Check if interface exists
        val interfaceResult = executeRoot("service list | grep -i '$interfaceName'")
        val actualInterface = if (interfaceResult.success && interfaceResult.output.isNotEmpty()) {
            interfaceResult.outputText.trim()
        } else {
            interfaceName
        }

        return HALServiceStatus(
            name = name,
            running = running,
            pid = pid,
            interfaceName = actualInterface
        )
    }

    // ==================== Driver Information ====================

    fun getDriverInfo(): DriverInfo {
        val modules = findLoadedModules()

        // Check HK32F0301 driver status
        val hk32Driver = checkDriverStatus("hk32f0301_led", modules)

        // Check AW20144 driver status
        val aw20144Driver = checkDriverStatus("aw20144_led", modules)

        // Check AW862 PDLC driver status
        val aw862Driver = checkDriverStatus("aw862", modules)

        // Check GPIO status for LED
        val gpioResult = executeRoot("cat /sys/kernel/debug/gpio 2>/dev/null | grep -i 'led\\|backcover\\|hk32'")
        val gpioStatus = if (gpioResult.success && gpioResult.output.isNotEmpty()) {
            gpioResult.outputText.take(200)
        } else {
            "Not available (need debugfs)"
        }

        // Check I2C bus status
        val i2cResult = executeRoot("cat /sys/bus/i2c/devices/*/name 2>/dev/null")
        val i2cStatus = if (i2cResult.success) {
            "${i2cResult.output.size} I2C devices found"
        } else {
            "Not accessible"
        }

        // Get kernel config
        val kernelConfig = getKernelConfig()

        // Get cmdline params
        val cmdlineParams = getCmdlineParams()

        return DriverInfo(
            modules = modules,
            hk32Driver = hk32Driver,
            aw20144Driver = aw20144Driver,
            aw862Driver = aw862Driver,
            gpioStatus = gpioStatus,
            i2cStatus = i2cStatus,
            kernelConfig = kernelConfig,
            cmdlineParams = cmdlineParams
        )
    }

    private fun getKernelConfig(): Map<String, String> {
        val config = mutableMapOf<String, String>()

        val ledConfigKeys = listOf(
            "CONFIG_LEDS_CLASS",
            "CONFIG_LEDS_TRIGGERS",
            "CONFIG_LEDS_TRIGGER_TIMER",
            "CONFIG_LEDS_TRIGGER_HEARTBEAT",
            "CONFIG_LEDS_TRIGGER_BREATHING",
            "CONFIG_I2C",
            "CONFIG_I2C_CHARDEV",
            "CONFIG_GPIO_SYSFS",
            "CONFIG_PWM",
            "CONFIG_HK32F0301_LED",
            "CONFIG_AW20144_LED"
        )

        // Try /proc/config.gz
        val configGzResult = executeRoot("zcat /proc/config.gz 2>/dev/null | grep -E '${ledConfigKeys.joinToString("|")}'")
        if (configGzResult.success) {
            configGzResult.output.forEach { line ->
                val parts = line.split("=")
                if (parts.size == 2) {
                    config[parts[0].trim()] = parts[1].trim()
                }
            }
        }

        return config
    }

    private fun getCmdlineParams(): Map<String, String> {
        val params = mutableMapOf<String, String>()

        try {
            val cmdline = File("/proc/cmdline").readText()
            cmdline.split(" ").forEach { param ->
                val parts = param.split("=")
                if (parts.size == 2) {
                    params[parts[0]] = parts[1]
                } else if (parts.size == 1 && parts[0].isNotEmpty()) {
                    params[parts[0]] = "true"
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error reading cmdline: ${e.message}")
        }

        return params
    }

    private fun checkDriverStatus(driverName: String, modules: List<ModuleInfo>): DriverStatus {
        val module = modules.find { it.name.contains(driverName, ignoreCase = true) }

        // Find driver path
        val pathResult = executeRoot("find /sys/module -name '$driverName*' -type d 2>/dev/null")
        val path = pathResult.output.firstOrNull()

        // Get module parameters
        val params = mutableMapOf<String, String>()
        if (path != null) {
            val paramsResult = executeRoot("ls $path/parameters/ 2>/dev/null")
            paramsResult.output.forEach { param ->
                val valueResult = executeRoot("cat $path/parameters/$param 2>/dev/null")
                if (valueResult.success) {
                    params[param] = valueResult.outputText.trim()
                }
            }
        }

        // Get version if available
        val versionResult = executeRoot("cat $path/version 2>/dev/null")
        val version = if (versionResult.success) versionResult.outputText.trim() else null

        // Get initstate
        val initstateResult = executeRoot("cat $path/initstate 2>/dev/null")
        val initstate = if (initstateResult.success) initstateResult.outputText.trim() else null

        return DriverStatus(
            loaded = module != null,
            path = path,
            version = version,
            params = params,
            initstate = initstate
        )
    }

    // ==================== Libraries Information ====================

    fun getLibrariesInfo(): LibrariesInfo {
        val systemLibs = mutableListOf<LibInfo>()
        val vendorLibs = mutableListOf<LibInfo>()
        val ledLibsFound = mutableListOf<LibInfo>()
        val missingLibs = mutableListOf<String>()

        val ledLibPatterns = listOf("led", "light", "transsion", "tc_led", "backcover", "hk32", "aw20144")
        val expectedLibs = listOf(
            "liblights.mtk.so",
            "liblight.so",
            "vendor.transsion.led@1.0.so",
            "android.hardware.lights@1.0.so"
        )

        // Check system libraries
        val systemResult = executeRoot("find /system/lib* -name '*led*' -o -name '*light*' -o -name '*transsion*' 2>/dev/null")
        systemResult.output.forEach { libPath ->
            if (libPath.endsWith(".so")) {
                val name = libPath.substringAfterLast("/")
                val sizeResult = executeRoot("stat -c %s $libPath 2>/dev/null")
                val size = sizeResult.outputText.trim().toLongOrNull() ?: 0

                val libInfo = LibInfo(name, libPath, size, null)
                systemLibs.add(libInfo)

                if (ledLibPatterns.any { name.contains(it, ignoreCase = true) }) {
                    ledLibsFound.add(libInfo)
                }
            }
        }

        // Check vendor libraries
        val vendorResult = executeRoot("find /vendor/lib* -name '*led*' -o -name '*light*' -o -name '*transsion*' 2>/dev/null")
        vendorResult.output.forEach { libPath ->
            if (libPath.endsWith(".so")) {
                val name = libPath.substringAfterLast("/")
                val sizeResult = executeRoot("stat -c %s $libPath 2>/dev/null")
                val size = sizeResult.outputText.trim().toLongOrNull() ?: 0

                val libInfo = LibInfo(name, libPath, size, null)
                vendorLibs.add(libInfo)

                if (ledLibPatterns.any { name.contains(it, ignoreCase = true) }) {
                    ledLibsFound.add(libInfo)
                }
            }
        }

        // Check for missing expected libs
        expectedLibs.forEach { lib ->
            val found = ledLibsFound.any { it.name.contains(lib.removeSuffix(".so"), ignoreCase = true) }
            if (!found) {
                missingLibs.add(lib)
            }
        }

        // Check vendor partition status
        val vendorMountResult = executeRoot("mount | grep ' /vendor '")
        val vendorPartitionStatus = if (vendorMountResult.success && vendorMountResult.output.isNotEmpty()) {
            "Mounted (${vendorMountResult.outputText.substringAfter("type ").substringBefore(" ")})"
        } else {
            "Not mounted"
        }

        return LibrariesInfo(
            systemLibs = systemLibs,
            vendorLibs = vendorLibs,
            ledLibsFound = ledLibsFound,
            missingLibs = missingLibs,
            vendorPartitionStatus = vendorPartitionStatus
        )
    }

    // ==================== SELinux Information ====================

    fun getSELinuxInfo(): SELinuxInfo {
        // Get SELinux mode
        val modeResult = executeRoot("getenforce")
        val mode = modeResult.outputText.trim()

        // Get policy version
        val versionResult = executeRoot("cat /sys/fs/selinux/policyvers 2>/dev/null")
        val policyVersion = versionResult.outputText.trim()

        // Check LED-related SELinux booleans
        val booleans = mutableMapOf<String, Boolean>()
        val ledBooleans = listOf("light_hwservice", "hal_light_default", "sysfs_led")

        ledBooleans.forEach { bool ->
            val boolResult = executeRoot("getsebool $bool 2>/dev/null")
            if (boolResult.success && boolResult.output.isNotEmpty()) {
                val enabled = boolResult.outputText.contains("on", ignoreCase = true)
                booleans[bool] = enabled
            }
        }

        // Find LED-related SELinux contexts
        val contexts = mutableListOf<String>()

        // Check sysfs LED contexts
        val sysfsResult = executeRoot("ls -Z /sys/class/leds/ 2>/dev/null")
        sysfsResult.output.forEach { line ->
            if (line.contains("led", ignoreCase = true) ||
                line.contains("light", ignoreCase = true) ||
                line.contains("backcover", ignoreCase = true)) {
                contexts.add(line.trim())
            }
        }

        // Check device contexts
        val devResult = executeRoot("ls -Z /dev/ 2>/dev/null | grep -i 'led\\|light'")
        devResult.output.forEach { line ->
            contexts.add(line.trim())
        }

        // Check for recent SELinux denials related to LED
        val denials = mutableListOf<SELinuxDenial>()
        val denialResult = executeRoot("dmesg | grep -i 'avc.*denied.*led\\|avc.*denied.*light\\|avc.*denied.*sysfs' 2>/dev/null | tail -20")
        denialResult.output.forEach { line ->
            // Parse denial
            val scontextMatch = Regex("scontext=([^\\s]+)").find(line)
            val tcontextMatch = Regex("tcontext=([^\\s]+)").find(line)
            val tclassMatch = Regex("tclass=([^\\s]+)").find(line)
            val permMatch = Regex("\\{([^\\}]+)\\}").find(line)

            if (scontextMatch != null || tcontextMatch != null) {
                denials.add(SELinuxDenial(
                    timestamp = line.substringBefore("[").trim(),
                    scontext = scontextMatch?.groupValues?.get(1) ?: "",
                    tcontext = tcontextMatch?.groupValues?.get(1) ?: "",
                    tclass = tclassMatch?.groupValues?.get(1) ?: "",
                    permission = permMatch?.groupValues?.get(1) ?: ""
                ))
            }
        }

        // Check if policy file exists
        val policyResult = executeRoot("ls /sys/fs/selinux/policy 2>/dev/null")
        val policyFileExists = policyResult.success

        return SELinuxInfo(
            mode = mode,
            policyVersion = policyVersion,
            booleans = booleans,
            ledRelatedContexts = contexts,
            denials = denials,
            policyFileExists = policyFileExists
        )
    }

    // ==================== Device Tree Information ====================

    fun getDeviceTreeInfo(): DeviceTreeInfo {
        // Get compatible string
        val compatResult = executeRoot("cat /sys/firmware/devicetree/base/compatible 2>/dev/null")
        val compatible = compatResult.outputText.replace("\u0000", " ").trim()

        // Get model
        val modelResult = executeRoot("cat /sys/firmware/devicetree/base/model 2>/dev/null")
        val model = modelResult.outputText.trim()

        // Find LED node in device tree
        var ledNode: String? = null
        val ledFindResult = executeRoot("find /sys/firmware/devicetree -name '*led*' -o -name '*backcover*' -o -name '*hk32*' 2>/dev/null")
        if (ledFindResult.success && ledFindResult.output.isNotEmpty()) {
            ledNode = ledFindResult.output.firstOrNull()
        }

        // Find I2C nodes
        val i2cNodes = mutableListOf<String>()
        val i2cFindResult = executeRoot("find /sys/firmware/devicetree/base -name 'i2c*' -type d 2>/dev/null | head -10")
        i2cFindResult.output.forEach { node ->
            i2cNodes.add(node.substringAfterLast("/"))
        }

        // Find LED pinctrl
        var ledPinctrl: String? = null
        val pinctrlResult = executeRoot("cat /sys/firmware/devicetree/base/leds/pinctrl-names 2>/dev/null")
        if (pinctrlResult.success && pinctrlResult.output.isNotEmpty()) {
            ledPinctrl = pinctrlResult.outputText.replace("\u0000", ", ").trim()
        }

        // Check for overlays
        val overlays = mutableListOf<OverlayInfo>()
        val overlayResult = executeRoot("ls /sys/kernel/config/device-tree/overlays/ 2>/dev/null")
        overlayResult.output.forEach { overlayName ->
            if (overlayName.isNotEmpty()) {
                val statusResult = executeRoot("cat /sys/kernel/config/device-tree/overlays/$overlayName/status 2>/dev/null")
                overlays.add(OverlayInfo(
                    name = overlayName,
                    applied = statusResult.outputText.contains("applied", ignoreCase = true),
                    path = "/sys/kernel/config/device-tree/overlays/$overlayName"
                ))
            }
        }

        // Get LED DTS content if available
        var ledDtsContent: String? = null
        if (ledNode != null) {
            val dtsResult = executeRoot("ls -la $ledNode 2>/dev/null; cat $ledNode/compatible 2>/dev/null; cat $ledNode/status 2>/dev/null")
            if (dtsResult.success) {
                ledDtsContent = dtsResult.outputText.take(500)
            }
        }

        return DeviceTreeInfo(
            compatible = compatible.ifEmpty { "Not available" },
            model = model.ifEmpty { "Not available" },
            ledNode = ledNode,
            i2cNodes = i2cNodes,
            ledPinctrl = ledPinctrl,
            overlays = overlays,
            ledDtsContent = ledDtsContent
        )
    }

    // ==================== Vendor & Partition Info ====================

    fun getVendorInfo(): VendorInfo {
        // Check partition mounts
        val mountResult = executeRoot("mount | grep -E '/vendor |/odm |/product '")
        val mountLines = mountResult.output

        val vendorMounted = mountLines.any { it.contains(" /vendor ") }
        val odmMounted = mountLines.any { it.contains(" /odm ") }
        val productMounted = mountLines.any { it.contains(" /product ") }

        // Get vendor partition type
        val vendorTypeResult = executeRoot("mount | grep ' /vendor ' | awk '{print $5}'")
        val vendorPartitionType = vendorTypeResult.outputText.trim().ifEmpty { "unknown" }

        // Get vendor properties
        val vendorProps = mutableMapOf<String, String>()
        val vendorPropKeys = listOf(
            "ro.vendor.product.device",
            "ro.vendor.product.model",
            "ro.vendor.build.fingerprint",
            "ro.vendor.hw.led",
            "ro.vendor.led.type",
            "persist.vendor.led.enable"
        )

        vendorPropKeys.forEach { key ->
            val value = getSystemProperty(key)
            if (value != null) {
                vendorProps[key] = value
            }
        }

        // Check for missing vendor files
        val missingFiles = mutableListOf<String>()
        val expectedVendorFiles = listOf(
            "/vendor/etc/init/tc_led.rc",
            "/vendor/lib64/hw/android.hardware.lights@1.0-service.so",
            "/vendor/bin/hw/android.hardware.lights-service"
        )

        expectedVendorFiles.forEach { file ->
            val result = executeRoot("ls $file 2>/dev/null")
            if (!result.success) {
                missingFiles.add(file)
            }
        }

        return VendorInfo(
            vendorPartitionMounted = vendorMounted,
            vendorPartitionType = vendorPartitionType,
            vendorProps = vendorProps,
            odmPartitionMounted = odmMounted,
            productPartitionMounted = productMounted,
            missingVendorFiles = missingFiles
        )
    }

    // ==================== Kernel Information ====================

    fun getKernelInfo(): KernelInfo {
        val version = getKernelVersion()

        // Get cmdline
        val cmdline = try {
            File("/proc/cmdline").readText().trim()
        } catch (e: Exception) {
            "Not available"
        }

        // Get config
        val config = getKernelConfig()

        // Check if modules are loaded
        val modulesLoaded = findLoadedModules().isNotEmpty()

        // Check initramfs type
        val initramfsResult = executeRoot("ls -la /init* 2>/dev/null | head -5")
        val initramfsType = if (initramfsResult.success) {
            if (initramfsResult.output.any { it.contains("initramfs") }) "initramfs"
            else if (initramfsResult.output.any { it.contains("init") }) "bootimage"
            else "unknown"
        } else {
            "unknown"
        }

        return KernelInfo(
            version = version,
            cmdline = cmdline,
            config = config,
            modulesLoaded = modulesLoaded,
            initramfsType = initramfsType
        )
    }

    // ==================== Complete Diagnostics ====================

    fun getFullDiagnostics(): SystemDiagnostics {
        return SystemDiagnostics(
            deviceInfo = getDeviceInfo(),
            ledInfo = getLEDInfo(),
            initServices = getInitServicesInfo(),
            halInfo = getHALInfo(),
            driverInfo = getDriverInfo(),
            librariesInfo = getLibrariesInfo(),
            selinuxInfo = getSELinuxInfo(),
            deviceTreeInfo = getDeviceTreeInfo(),
            vendorInfo = getVendorInfo(),
            kernelInfo = getKernelInfo()
        )
    }
}
