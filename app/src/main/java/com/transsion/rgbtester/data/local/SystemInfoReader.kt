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
 */
class SystemInfoReader {

    companion object {
        private const val TAG = "SystemInfoReader"
        private const val LED_CLASS_PATH = "/sys/class/leds"
        private const val I2C_BUS_PATH = "/sys/bus/i2c/devices"
        private const val DEVICETREE_PATH = "/sys/firmware/devicetree"
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
        val romType: RomType
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
        val dtCompatible: String
    )

    data class I2CDevice(
        val path: String,
        val name: String,
        val address: String,
        val driver: String
    )

    // Init Services Status
    data class InitServicesInfo(
        val services: List<ServiceStatus>,
        val rcFilesFound: List<String>,
        val tcLedEnabled: Boolean,
        val lightHalEnabled: Boolean
    )

    data class ServiceStatus(
        val name: String,
        val status: String, // "running", "stopped", "not_found"
        val pid: Int?
    )

    // HAL Services Status
    data class HALInfo(
        val lightHal: HALServiceStatus,
        val vibratorHal: HALServiceStatus,
        val transsionHal: HALServiceStatus,
        val allHals: List<HALServiceStatus>
    )

    data class HALServiceStatus(
        val name: String,
        val running: Boolean,
        val pid: Int?,
        val interfaceName: String
    )

    // Driver Information
    data class DriverInfo(
        val modules: List<ModuleInfo>,
        val hk32Driver: DriverStatus,
        val aw20144Driver: DriverStatus,
        aw862Driver: DriverStatus,
        val gpioStatus: String,
        val i2cStatus: String
    )

    data class ModuleInfo(
        val name: String,
        val size: Int,
        val usedBy: Int,
        val dependencies: String
    )

    data class DriverStatus(
        val loaded: Boolean,
        val path: String?,
        val version: String?,
        val params: Map<String, String>
    )

    // Libraries Information
    data class LibrariesInfo(
        val systemLibs: List<LibInfo>,
        val vendorLibs: List<LibInfo>,
        val ledLibsFound: List<LibInfo>
    )

    data class LibInfo(
        val name: String,
        val path: String,
        val size: Long
    )

    // SELinux Information
    data class SELinuxInfo(
        val mode: String, // "Enforcing", "Permissive", "Disabled"
        val policyVersion: String,
        val booleans: Map<String, Boolean>,
        val ledRelatedContexts: List<String>
    )

    // Device Tree Information
    data class DeviceTreeInfo(
        val compatible: String,
        val model: String,
        val ledNode: String?,
        val i2cNodes: List<String>,
        val ledPinctrl: String?
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
        val deviceTreeInfo: DeviceTreeInfo
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

        val romType = detectRomType(manufacturer, brand, buildType, fingerprint)

        return DeviceInfo(
            model = getSystemProperty("ro.product.model") ?: Build.MODEL,
            manufacturer = manufacturer,
            platform = getSystemProperty("ro.board.platform") ?: getSystemProperty("ro.hardware") ?: "Unknown",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            kernelVersion = getKernelVersion(),
            buildId = Build.DISPLAY ?: Build.ID,
            buildType = buildType,
            buildFingerprint = fingerprint,
            romType = romType
        )
    }

    private fun detectRomType(manufacturer: String, brand: String, buildType: String, fingerprint: String): RomType {
        val lowerFingerprint = fingerprint.lowercase()
        val lowerManufacturer = manufacturer.lowercase()
        val lowerBrand = brand.lowercase()

        // Check for custom ROM indicators
        val customRomIndicators = listOf("lineage", "pixel", "crdroid", "aosp", "custom", "userdebug", "eng")
        val stockRomIndicators = listOf("transsion", "infinix", "tecno", "itel", "release-keys")

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
            File("/proc/version").readText().trim().take(100)
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
            dtCompatible = dtCompatible
        )
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
                        name.contains("white")) {
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
                            name.contains("i2c", ignoreCase = true)) {
                            modules.add(ModuleInfo(
                                name = name,
                                size = parts[1].toIntOrNull() ?: 0,
                                usedBy = parts[2].toIntOrNull() ?: 0,
                                dependencies = if (parts.size > 3) parts[3] else ""
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

                // Filter LED-related I2C devices
                if (name.contains("hk32", ignoreCase = true) ||
                    name.contains("aw20144", ignoreCase = true) ||
                    name.contains("aw862", ignoreCase = true) ||
                    name.contains("tc_led", ignoreCase = true) ||
                    address == "0050" || // HK32F0301 typical address
                    address == "003c") { // AW20144 typical address
                    devices.add(I2CDevice(
                        path = path,
                        name = name.ifEmpty { "Unknown" },
                        address = "0x${address.toIntOrNull(16)?.toString(16) ?: address}",
                        driver = driver
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
            return "HK32F0301 MCU @ I2C ${hk32I2C.address}"
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
        val aw20144I2C = i2cDevices.find { it.name.contains("aw20144", ignoreCase = true) }
        if (aw20144I2C != null) {
            return "AW20144 @ I2C ${aw20144I2C.address}"
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
        val rcFilesFound = mutableListOf<String>()

        // Check for relevant init.rc files
        val rcPaths = listOf(
            "/system/etc/init/",
            "/vendor/etc/init/",
            "/odm/etc/init/",
            "/product/etc/init/"
        )

        val ledRelatedRcs = listOf("tc_led", "light", "transsion", "led", "backcover")

        rcPaths.forEach { rcPath ->
            val result = executeRoot("ls -1 $rcPath*.rc 2>/dev/null")
            result.output.forEach { rcFile ->
                if (ledRelatedRcs.any { rcFile.contains(it, ignoreCase = true) }) {
                    rcFilesFound.add(rcFile)
                }
            }
        }

        // Check specific services
        val servicesToCheck = listOf(
            "tc_led",
            "tc-led",
            "lights-mtk-default",
            "android.hardware.lights",
            "transsion-led",
            "backcover-led",
            "vendor.light"
        )

        servicesToCheck.forEach { serviceName ->
            val status = checkInitService(serviceName)
            services.add(status)
        }

        // Check if tc_led and light HAL are enabled
        val tcLedEnabled = services.any { it.name.contains("tc_led") && it.status == "running" }
        val lightHalEnabled = services.any { it.name.contains("light") && it.status == "running" }

        return InitServicesInfo(
            services = services,
            rcFilesFound = rcFilesFound,
            tcLedEnabled = tcLedEnabled,
            lightHalEnabled = lightHalEnabled
        )
    }

    private fun checkInitService(serviceName: String): ServiceStatus {
        // Check via getprop
        val propResult = executeRoot("getprop init.svc.$serviceName")
        val status = propResult.outputText.trim()

        if (status == "running") {
            // Get PID
            val pidResult = executeRoot("pidof $serviceName")
            val pid = pidResult.outputText.trim().toIntOrNull()
            return ServiceStatus(serviceName, "running", pid)
        } else if (status == "stopped") {
            return ServiceStatus(serviceName, "stopped", null)
        }

        // Try service list
        val listResult = executeRoot("service list | grep -i $serviceName")
        if (listResult.success && listResult.output.isNotEmpty()) {
            return ServiceStatus(serviceName, "registered", null)
        }

        return ServiceStatus(serviceName, "not_found", null)
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

        // Check for additional HALs
        val halResult = executeRoot("service list | grep -i 'light\\|led\\|transsion'")
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

        return HALInfo(
            lightHal = lightHal,
            vibratorHal = vibratorHal,
            transsionHal = transsionHal,
            allHals = allHals
        )
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
        val gpioResult = executeRoot("cat /sys/kernel/debug/gpio 2>/dev/null | grep -i 'led\\|backcover'")
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

        return DriverInfo(
            modules = modules,
            hk32Driver = hk32Driver,
            aw20144Driver = aw20144Driver,
            aw862Driver = aw862Driver,
            gpioStatus = gpioStatus,
            i2cStatus = i2cStatus
        )
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

        return DriverStatus(
            loaded = module != null,
            path = path,
            version = version,
            params = params
        )
    }

    // ==================== Libraries Information ====================

    fun getLibrariesInfo(): LibrariesInfo {
        val systemLibs = mutableListOf<LibInfo>()
        val vendorLibs = mutableListOf<LibInfo>()
        val ledLibsFound = mutableListOf<LibInfo>()

        val ledLibPatterns = listOf("led", "light", "transsion", "tc_led", "backcover", "hk32", "aw20144")

        // Check system libraries
        val systemResult = executeRoot("find /system/lib* -name '*led*' -o -name '*light*' -o -name '*transsion*' 2>/dev/null")
        systemResult.output.forEach { libPath ->
            if (libPath.endsWith(".so")) {
                val name = libPath.substringAfterLast("/")
                val sizeResult = executeRoot("stat -c %s $libPath 2>/dev/null")
                val size = sizeResult.outputText.trim().toLongOrNull() ?: 0

                val libInfo = LibInfo(name, libPath, size)
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

                val libInfo = LibInfo(name, libPath, size)
                vendorLibs.add(libInfo)

                if (ledLibPatterns.any { name.contains(it, ignoreCase = true) }) {
                    ledLibsFound.add(libInfo)
                }
            }
        }

        return LibrariesInfo(
            systemLibs = systemLibs,
            vendorLibs = vendorLibs,
            ledLibsFound = ledLibsFound
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
        val ledBooleans = listOf("light_hwservice", "hal_light_default")

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

        return SELinuxInfo(
            mode = mode,
            policyVersion = policyVersion,
            booleans = booleans,
            ledRelatedContexts = contexts
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

        return DeviceTreeInfo(
            compatible = compatible.ifEmpty { "Not available" },
            model = model.ifEmpty { "Not available" },
            ledNode = ledNode,
            i2cNodes = i2cNodes,
            ledPinctrl = ledPinctrl
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
            deviceTreeInfo = getDeviceTreeInfo()
        )
    }
}
