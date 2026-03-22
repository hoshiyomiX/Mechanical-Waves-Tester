package com.transsion.rgbtester.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.transsion.rgbtester.R
import com.transsion.rgbtester.data.local.SystemInfoReader
import com.transsion.rgbtester.databinding.FragmentDashboardBinding
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
        setupClickListeners()

        // Load info on start
        viewModel.loadInfo()
    }

    private fun setupObservers() {
        // Device Info
        viewModel.deviceInfo.observe(viewLifecycleOwner) { info ->
            binding.tvModel.text = "${info.manufacturer} ${info.model}"
            binding.tvPlatform.text = info.platform
            binding.tvAndroidVersion.text = info.androidVersion
            binding.tvBuildType.text = info.buildType
            binding.tvKernel.text = info.kernelVersion.take(80) + if (info.kernelVersion.length > 80) "..." else ""
            binding.tvRomType.text = viewModel.getRomTypeDisplay(info.romType)
            binding.tvBuildFingerprint.text = info.buildFingerprint.take(60) + "..."
            binding.tvOtaVersion.text = info.otaVersion.ifEmpty { "N/A" }

            // Stock props match indicator
            if (info.stockPropsMatch) {
                binding.tvStockProps.text = "✅ Stock properties detected"
                binding.tvStockProps.setTextColor(requireContext().getColor(R.color.colorSuccess))
            } else {
                binding.tvStockProps.text = "⚠️ Stock properties not matched"
                binding.tvStockProps.setTextColor(requireContext().getColor(R.color.colorWarning))
            }

            // Show ROM banner for custom ROMs
            if (info.romType == SystemInfoReader.RomType.CUSTOM) {
                binding.cardRomBanner.visibility = View.VISIBLE
                binding.tvRomBanner.text = "⚠️ Custom ROM Detected - LED may need additional configuration"
            } else {
                binding.cardRomBanner.visibility = View.GONE
            }
        }

        // LED Info
        viewModel.ledInfo.observe(viewLifecycleOwner) { info ->
            binding.tvLedVariant.text = info.variant
            binding.tvFwVersion.text = info.fwVersion
            binding.tvLedType.text = info.ledType
            binding.tvLedController.text = info.controller
            binding.tvRgbDriver.text = info.rgbDriver
            binding.tvPdlcController.text = info.pdlcController
            binding.tvPowerState.text = info.powerState

            // Color code LED type
            if (info.ledType.contains("White LED Only", ignoreCase = true)) {
                binding.tvLedType.setTextColor(requireContext().getColor(R.color.colorWarning))
            } else if (info.ledType.contains("RGB", ignoreCase = true)) {
                binding.tvLedType.setTextColor(requireContext().getColor(R.color.colorSuccess))
            }

            // I2C Devices
            if (info.i2cDevices.isNotEmpty()) {
                val i2cText = info.i2cDevices.joinToString("\n") { device ->
                    "• ${device.name} @ ${device.address} [${device.driver}]"
                }
                binding.tvI2cDevices.text = i2cText
            } else {
                binding.tvI2cDevices.text = "No LED-related I2C devices found"
            }

            // Sysfs Paths
            if (info.sysfsPaths.isNotEmpty()) {
                binding.tvSysfsPaths.text = info.sysfsPaths.joinToString("\n")
            } else {
                binding.tvSysfsPaths.text = "No LED sysfs paths found"
            }

            // LED Attributes
            if (info.ledAttributes.isNotEmpty()) {
                val attrText = info.ledAttributes.entries.take(10).joinToString("\n") { (k, v) ->
                    "• $k: $v"
                }
                binding.tvLedAttributes.text = attrText
            } else {
                binding.tvLedAttributes.text = "No LED attributes found"
            }
        }

        // Init Services Info
        viewModel.initServicesInfo.observe(viewLifecycleOwner) { info ->
            val servicesText = StringBuilder()

            info.services.forEach { service ->
                val statusText = viewModel.getServiceStatusText(service.status)
                val pidText = service.pid?.let { " (PID: $it)" } ?: ""
                val rcText = service.rcFile?.let { " [${it.substringAfterLast("/")}]" } ?: ""
                servicesText.append("• ${service.name}: $statusText$pidText$rcText\n")
            }

            binding.tvServices.text = servicesText.toString().trimEnd()

            // RC Files
            if (info.rcFilesFound.isNotEmpty()) {
                val rcText = info.rcFilesFound.joinToString("\n") { rc ->
                    "• ${rc.path} (${rc.content.take(50)}...)"
                }
                binding.tvRcFiles.text = "RC Files:\n$rcText"
            } else {
                binding.tvRcFiles.text = "No LED-related RC files found"
            }

            // Service Analysis
            if (info.serviceAnalysis.isNotEmpty()) {
                binding.tvServiceAnalysis.text = info.serviceAnalysis
                binding.cardServiceAnalysis.visibility = View.VISIBLE
            } else {
                binding.cardServiceAnalysis.visibility = View.GONE
            }
        }

        // HAL Info
        viewModel.halInfo.observe(viewLifecycleOwner) { info ->
            binding.tvLightHal.text = viewModel.getHALStatusText(info.lightHal)
            binding.tvVibratorHal.text = viewModel.getHALStatusText(info.vibratorHal)
            binding.tvTranssionHal.text = viewModel.getHALStatusText(info.transsionHal)

            // Color coding
            setColorByStatus(binding.tvLightHal, info.lightHal.running)
            setColorByStatus(binding.tvTranssionHal, info.transsionHal.running)

            // HIDL Interfaces
            if (info.hidlInterfaces.isNotEmpty()) {
                val hidlText = info.hidlInterfaces.joinToString("\n") { iface ->
                    val status = if (iface.available) "✅" else "❌"
                    "• $status ${iface.name} v${iface.version}"
                }
                binding.tvHidlInterfaces.text = "HIDL Interfaces:\n$hidlText"
            } else {
                binding.tvHidlInterfaces.text = "No HIDL interfaces detected"
            }

            // AIDL Interfaces
            if (info.aidlInterfaces.isNotEmpty()) {
                val aidlText = info.aidlInterfaces.joinToString("\n") { iface ->
                    val status = if (iface.available) "✅" else "❌"
                    "• $status ${iface.name}"
                }
                binding.tvAidlInterfaces.text = "AIDL Interfaces:\n$aidlText"
            } else {
                binding.tvAidlInterfaces.text = "No AIDL interfaces detected"
            }
        }

        // Driver Info
        viewModel.driverInfo.observe(viewLifecycleOwner) { info ->
            binding.tvHk32Driver.text = if (info.hk32Driver.loaded) "✅ Loaded ${info.hk32Driver.initstate ?: ""}" else "❌ Not loaded"
            binding.tvAw20144Driver.text = if (info.aw20144Driver.loaded) "✅ Loaded ${info.aw20144Driver.initstate ?: ""}" else "❌ Not loaded"
            binding.tvAw862Driver.text = if (info.aw862Driver.loaded) "✅ Loaded ${info.aw862Driver.initstate ?: ""}" else "❌ Not loaded"

            setColorByStatus(binding.tvHk32Driver, info.hk32Driver.loaded)
            setColorByStatus(binding.tvAw20144Driver, info.aw20144Driver.loaded)
            setColorByStatus(binding.tvAw862Driver, info.aw862Driver.loaded)

            // I2C & GPIO Status
            binding.tvGpioStatus.text = info.gpioStatus.take(100)
            binding.tvI2cBusStatus.text = info.i2cStatus

            // Modules
            if (info.modules.isNotEmpty()) {
                val modulesText = info.modules.joinToString("\n") { mod ->
                    "• ${mod.name} (${mod.size} bytes) [${mod.state}]"
                }
                binding.tvModules.text = "Loaded Modules:\n$modulesText"
            } else {
                binding.tvModules.text = "No LED-related kernel modules loaded"
            }

            // Kernel Config
            if (info.kernelConfig.isNotEmpty()) {
                val configText = info.kernelConfig.entries.take(10).joinToString("\n") { (k, v) ->
                    "• $k=$v"
                }
                binding.tvKernelConfig.text = "Kernel Config:\n$configText"
            } else {
                binding.tvKernelConfig.text = "Kernel config not available"
            }

            // Cmdline params
            if (info.cmdlineParams.isNotEmpty()) {
                val ledParams = info.cmdlineParams.filterKeys { 
                    it.contains("led", ignoreCase = true) || 
                    it.contains("i2c", ignoreCase = true) ||
                    it.contains("gpio", ignoreCase = true)
                }
                if (ledParams.isNotEmpty()) {
                    binding.tvCmdlineParams.text = "LED-related cmdline: ${ledParams.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
                } else {
                    binding.tvCmdlineParams.text = ""
                }
            }
        }

        // Libraries Info
        viewModel.librariesInfo.observe(viewLifecycleOwner) { info ->
            val libsText = StringBuilder()

            if (info.ledLibsFound.isNotEmpty()) {
                libsText.append("LED Libraries Found:\n")
                info.ledLibsFound.forEach { lib ->
                    libsText.append("• ${lib.name}\n  ${lib.path}\n")
                }
            } else {
                libsText.append("No LED-specific libraries found\n")
            }

            libsText.append("\nVendor Partition: ${info.vendorPartitionStatus}")

            binding.tvLibraries.text = libsText.toString()

            // Missing libs
            if (info.missingLibs.isNotEmpty()) {
                binding.tvMissingLibs.text = "⚠️ Missing:\n${info.missingLibs.joinToString("\n") { "• $it" }}"
                binding.tvMissingLibs.visibility = View.VISIBLE
            } else {
                binding.tvMissingLibs.visibility = View.GONE
            }
        }

        // SELinux Info
        viewModel.selinuxInfo.observe(viewLifecycleOwner) { info ->
            binding.tvSelinuxMode.text = viewModel.getSELinuxModeDisplay(info.mode)
            binding.tvSelinuxPolicy.text = if (info.policyVersion.isNotEmpty()) "v${info.policyVersion}" else "-"

            // Color coding for SELinux mode
            when (info.mode.lowercase()) {
                "enforcing" -> binding.tvSelinuxMode.setTextColor(requireContext().getColor(R.color.colorSuccess))
                "permissive" -> binding.tvSelinuxMode.setTextColor(requireContext().getColor(R.color.colorWarning))
                else -> binding.tvSelinuxMode.setTextColor(requireContext().getColor(R.color.textSecondary))
            }

            // Contexts
            if (info.ledRelatedContexts.isNotEmpty()) {
                binding.tvSelinuxContexts.text = "Contexts:\n${info.ledRelatedContexts.take(5).joinToString("\n")}"
            } else {
                binding.tvSelinuxContexts.text = ""
            }

            // Denials
            if (info.denials.isNotEmpty()) {
                val denialText = info.denials.take(5).joinToString("\n\n") { d ->
                    "• ${d.permission} denied\n  scontext: ${d.scontext}\n  tcontext: ${d.tcontext}"
                }
                binding.tvSelinuxDenials.text = "Recent Denials:\n$denialText"
                binding.cardSelinuxDenials.visibility = View.VISIBLE
            } else {
                binding.cardSelinuxDenials.visibility = View.GONE
            }
        }

        // Device Tree Info
        viewModel.deviceTreeInfo.observe(viewLifecycleOwner) { info ->
            binding.tvDtCompatible.text = info.compatible
            binding.tvDtLedNode.text = info.ledNode?.substringAfterLast("/") ?: "Not found"
            binding.tvDtPinctrl.text = info.ledPinctrl ?: "Not found"

            // Overlays
            if (info.overlays.isNotEmpty()) {
                val overlayText = info.overlays.joinToString("\n") { o ->
                    val status = if (o.applied) "✅" else "❌"
                    "• $status ${o.name}"
                }
                binding.tvOverlays.text = "Overlays:\n$overlayText"
            } else {
                binding.tvOverlays.text = "No device tree overlays found"
            }

            // LED DTS content
            if (info.ledDtsContent != null) {
                binding.tvLedDts.text = "DTS Content:\n${info.ledDtsContent}"
            } else {
                binding.tvLedDts.text = ""
            }
        }

        // Vendor Info
        viewModel.vendorInfo.observe(viewLifecycleOwner) { info ->
            binding.tvVendorStatus.text = viewModel.getVendorStatusDisplay(info)

            // Vendor Props
            if (info.vendorProps.isNotEmpty()) {
                val propsText = info.vendorProps.entries.joinToString("\n") { (k, v) ->
                    "• $k: $v"
                }
                binding.tvVendorProps.text = "Vendor Props:\n$propsText"
            } else {
                binding.tvVendorProps.text = "No vendor props found"
            }

            // Missing vendor files
            if (info.missingVendorFiles.isNotEmpty()) {
                binding.tvMissingVendorFiles.text = "Missing Files:\n${info.missingVendorFiles.joinToString("\n") { "• $it" }}"
                binding.cardMissingVendorFiles.visibility = View.VISIBLE
            } else {
                binding.cardMissingVendorFiles.visibility = View.GONE
            }
        }

        // Kernel Info
        viewModel.kernelInfo.observe(viewLifecycleOwner) { info ->
            binding.tvKernelVersion.text = info.version.take(100)
            binding.tvCmdline.text = info.cmdline.take(200) + if (info.cmdline.length > 200) "..." else ""
            binding.tvInitramfs.text = "Initramfs: ${info.initramfsType}"
        }

        // Loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.btnRefresh.isEnabled = !isLoading
            binding.btnRefresh.text = if (isLoading) "Loading..." else "Refresh All Information"
            binding.btnExport.isEnabled = !isLoading
        }

        // Error message
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }

        // Generate recommendations based on data
        viewModel.ledInfo.observe(viewLifecycleOwner) { ledInfo ->
            viewModel.halInfo.observe(viewLifecycleOwner) { halInfo ->
                viewModel.driverInfo.observe(viewLifecycleOwner) { driverInfo ->
                    viewModel.selinuxInfo.observe(viewLifecycleOwner) { selinuxInfo ->
                        viewModel.vendorInfo.observe(viewLifecycleOwner) { vendorInfo ->
                            generateRecommendations(ledInfo, halInfo, driverInfo, selinuxInfo, vendorInfo)
                        }
                    }
                }
            }
        }
    }

    private fun setColorByStatus(view: android.widget.TextView, success: Boolean) {
        val color = if (success) R.color.colorSuccess else R.color.statusError
        view.setTextColor(requireContext().getColor(color))
    }

    private fun generateRecommendations(
        ledInfo: SystemInfoReader.LEDInfo,
        halInfo: SystemInfoReader.HALInfo,
        driverInfo: SystemInfoReader.DriverInfo,
        selinuxInfo: SystemInfoReader.SELinuxInfo,
        vendorInfo: SystemInfoReader.VendorInfo
    ) {
        val recommendations = mutableListOf<String>()

        // Check LED variant
        if (ledInfo.ledType.contains("White LED", ignoreCase = true)) {
            recommendations.add("• This device has White LED hardware only - RGB is not supported")
        }

        // Check I2C devices
        if (ledInfo.i2cDevices.isEmpty()) {
            recommendations.add("• No LED I2C devices detected - check I2C bus or driver")
        }

        // Check HAL
        if (!halInfo.lightHal.running) {
            recommendations.add("• Light HAL not running - check init.rc configuration")
        }
        if (!halInfo.transsionHal.running) {
            recommendations.add("• Transsion HAL not running - may need vendor HAL implementation")
        }

        // Check HIDL
        val missingHidl = halInfo.hidlInterfaces.filter { !it.available }
        if (missingHidl.isNotEmpty()) {
            recommendations.add("• Missing HIDL interfaces: ${missingHidl.joinToString { it.name }}")
        }

        // Check drivers
        if (!driverInfo.hk32Driver.loaded && ledInfo.sysfsPaths.isNotEmpty()) {
            recommendations.add("• HK32F0301 driver not loaded - try: modprobe hk32f0301_led")
        }
        if (!driverInfo.aw20144Driver.loaded && ledInfo.ledType.contains("RGB", ignoreCase = true)) {
            recommendations.add("• AW20144 RGB driver not loaded - RGB may not work")
        }

        // Check SELinux
        if (selinuxInfo.mode.equals("Enforcing", ignoreCase = true)) {
            recommendations.add("• SELinux is Enforcing - may block LED access. Try: setenforce 0")
        }
        if (selinuxInfo.denials.isNotEmpty()) {
            recommendations.add("• SELinux denials detected - check dmesg for details")
        }

        // Check vendor partition
        if (!vendorInfo.vendorPartitionMounted) {
            recommendations.add("• Vendor partition not mounted - LED won't work on custom ROM")
        }
        if (vendorInfo.missingVendorFiles.isNotEmpty()) {
            recommendations.add("• Missing vendor files: ${vendorInfo.missingVendorFiles.size} files")
        }

        if (recommendations.isNotEmpty()) {
            binding.cardRecommendations.visibility = View.VISIBLE
            binding.tvRecommendations.text = recommendations.joinToString("\n")
        } else {
            binding.cardRecommendations.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        binding.btnRefresh.setOnClickListener {
            viewModel.loadInfo()
        }

        binding.btnExport.setOnClickListener {
            exportDebugReport()
        }
    }

    private fun exportDebugReport() {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "rgb_debug_$timestamp.txt"
            val file = File(requireContext().cacheDir, fileName)

            FileWriter(file).use { writer ->
                writer.write("=== RGB Back Cover Debug Report ===\n")
                writer.write("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                writer.write("App Version: Mechanical Wave Tester v1.1\n\n")

                // Device Info
                viewModel.deviceInfo.value?.let { info ->
                    writer.write("--- Device Information ---\n")
                    writer.write("Model: ${info.manufacturer} ${info.model}\n")
                    writer.write("Platform: ${info.platform}\n")
                    writer.write("Android: ${info.androidVersion}\n")
                    writer.write("Build Type: ${info.buildType}\n")
                    writer.write("ROM Type: ${info.romType}\n")
                    writer.write("Kernel: ${info.kernelVersion}\n")
                    writer.write("Build ID: ${info.buildId}\n")
                    writer.write("Fingerprint: ${info.buildFingerprint}\n")
                    writer.write("Vendor Fingerprint: ${info.vendorFingerprint}\n")
                    writer.write("OTA Version: ${info.otaVersion}\n")
                    writer.write("Stock Props Match: ${info.stockPropsMatch}\n\n")
                }

                // LED Info
                viewModel.ledInfo.value?.let { info ->
                    writer.write("--- LED Hardware ---\n")
                    writer.write("Variant: ${info.variant}\n")
                    writer.write("FW Version: ${info.fwVersion}\n")
                    writer.write("LED Type: ${info.ledType}\n")
                    writer.write("Controller: ${info.controller}\n")
                    writer.write("RGB Driver: ${info.rgbDriver}\n")
                    writer.write("PDLC Controller: ${info.pdlcController}\n")
                    writer.write("Power State: ${info.powerState}\n\n")

                    writer.write("I2C Devices:\n")
                    info.i2cDevices.forEach { device ->
                        writer.write("  - ${device.name} @ ${device.address} [${device.driver}]\n")
                    }
                    writer.write("\nSysfs Paths:\n")
                    info.sysfsPaths.forEach { path ->
                        writer.write("  - $path\n")
                    }
                    writer.write("\nLED Attributes:\n")
                    info.ledAttributes.forEach { (k, v) ->
                        writer.write("  $k: $v\n")
                    }
                    writer.write("\n")
                }

                // Init Services
                viewModel.initServicesInfo.value?.let { info ->
                    writer.write("--- Init Services ---\n")
                    info.services.forEach { service ->
                        writer.write("  ${service.name}: ${service.status} ${service.pid?.let { "(PID: $it)" } ?: ""}\n")
                    }
                    writer.write("\nRC Files:\n")
                    info.rcFilesFound.forEach { rcFile ->
                        writer.write("  - ${rcFile.path}\n")
                        writer.write("    Content: ${rcFile.content.take(200)}...\n")
                    }
                    writer.write("\nAnalysis: ${info.serviceAnalysis}\n\n")
                }

                // HAL Info
                viewModel.halInfo.value?.let { info ->
                    writer.write("--- HAL Services ---\n")
                    writer.write("Light HAL: ${if (info.lightHal.running) "Running (PID: ${info.lightHal.pid})" else "Not running"}\n")
                    writer.write("Vibrator HAL: ${if (info.vibratorHal.running) "Running" else "Not running"}\n")
                    writer.write("Transsion HAL: ${if (info.transsionHal.running) "Running (PID: ${info.transsionHal.pid})" else "Not running"}\n\n")

                    writer.write("HIDL Interfaces:\n")
                    info.hidlInterfaces.forEach { iface ->
                        writer.write("  ${if (iface.available) "✅" else "❌"} ${iface.name} v${iface.version}\n")
                    }
                    writer.write("\nAIDL Interfaces:\n")
                    info.aidlInterfaces.forEach { iface ->
                        writer.write("  ${if (iface.available) "✅" else "❌"} ${iface.name}\n")
                    }
                    writer.write("\n")
                }

                // Drivers
                viewModel.driverInfo.value?.let { info ->
                    writer.write("--- Kernel Drivers ---\n")
                    writer.write("HK32F0301: ${if (info.hk32Driver.loaded) "Loaded (${info.hk32Driver.initstate})" else "Not loaded"}\n")
                    writer.write("AW20144: ${if (info.aw20144Driver.loaded) "Loaded (${info.aw20144Driver.initstate})" else "Not loaded"}\n")
                    writer.write("AW862 PDLC: ${if (info.aw862Driver.loaded) "Loaded" else "Not loaded"}\n")
                    writer.write("GPIO Status: ${info.gpioStatus}\n")
                    writer.write("I2C Status: ${info.i2cStatus}\n\n")

                    writer.write("Loaded Modules:\n")
                    info.modules.forEach { mod ->
                        writer.write("  - ${mod.name} (${mod.size} bytes) [${mod.state}]\n")
                    }
                    writer.write("\nKernel Config:\n")
                    info.kernelConfig.forEach { (k, v) ->
                        writer.write("  $k=$v\n")
                    }
                    writer.write("\nCmdline Params:\n")
                    info.cmdlineParams.forEach { (k, v) ->
                        writer.write("  $k=$v\n")
                    }
                    writer.write("\n")
                }

                // Libraries
                viewModel.librariesInfo.value?.let { info ->
                    writer.write("--- Shared Libraries ---\n")
                    info.ledLibsFound.forEach { lib ->
                        writer.write("  - ${lib.name}\n    Path: ${lib.path}\n    Size: ${lib.size}\n")
                    }
                    writer.write("\nVendor Partition: ${info.vendorPartitionStatus}\n")
                    writer.write("\nMissing Libraries:\n")
                    info.missingLibs.forEach { lib ->
                        writer.write("  - $lib\n")
                    }
                    writer.write("\n")
                }

                // SELinux
                viewModel.selinuxInfo.value?.let { info ->
                    writer.write("--- SELinux ---\n")
                    writer.write("Mode: ${info.mode}\n")
                    writer.write("Policy Version: ${info.policyVersion}\n")
                    writer.write("Policy File Exists: ${info.policyFileExists}\n\n")

                    writer.write("Booleans:\n")
                    info.booleans.forEach { (k, v) ->
                        writer.write("  $k: ${if (v) "on" else "off"}\n")
                    }
                    writer.write("\nContexts:\n")
                    info.ledRelatedContexts.forEach { ctx ->
                        writer.write("  $ctx\n")
                    }
                    writer.write("\nRecent Denials:\n")
                    info.denials.forEach { denial ->
                        writer.write("  - ${denial.permission}: scontext=${denial.scontext}, tcontext=${denial.tcontext}, tclass=${denial.tclass}\n")
                    }
                    writer.write("\n")
                }

                // Device Tree
                viewModel.deviceTreeInfo.value?.let { info ->
                    writer.write("--- Device Tree ---\n")
                    writer.write("Compatible: ${info.compatible}\n")
                    writer.write("Model: ${info.model}\n")
                    writer.write("LED Node: ${info.ledNode ?: "Not found"}\n")
                    writer.write("Pinctrl: ${info.ledPinctrl ?: "Not found"}\n\n")

                    writer.write("Overlays:\n")
                    info.overlays.forEach { overlay ->
                        writer.write("  ${if (overlay.applied) "✅" else "❌"} ${overlay.name}\n")
                    }
                    writer.write("\nLED DTS Content:\n${info.ledDtsContent ?: "N/A"}\n\n")
                }

                // Vendor Info
                viewModel.vendorInfo.value?.let { info ->
                    writer.write("--- Vendor Information ---\n")
                    writer.write("Vendor Mounted: ${info.vendorPartitionMounted}\n")
                    writer.write("Vendor Type: ${info.vendorPartitionType}\n")
                    writer.write("ODM Mounted: ${info.odmPartitionMounted}\n")
                    writer.write("Product Mounted: ${info.productPartitionMounted}\n\n")

                    writer.write("Vendor Props:\n")
                    info.vendorProps.forEach { (k, v) ->
                        writer.write("  $k: $v\n")
                    }
                    writer.write("\nMissing Vendor Files:\n")
                    info.missingVendorFiles.forEach { file ->
                        writer.write("  - $file\n")
                    }
                    writer.write("\n")
                }

                // Kernel Info
                viewModel.kernelInfo.value?.let { info ->
                    writer.write("--- Kernel Information ---\n")
                    writer.write("Version: ${info.version}\n")
                    writer.write("Initramfs Type: ${info.initramfsType}\n")
                    writer.write("Modules Loaded: ${info.modulesLoaded}\n")
                    writer.write("\nCmdline:\n${info.cmdline}\n\n")
                }

                // Recommendations
                val recommendations = binding.tvRecommendations.text.toString()
                if (recommendations.isNotEmpty()) {
                    writer.write("--- Recommendations ---\n")
                    writer.write(recommendations)
                    writer.write("\n")
                }
            }

            // Share the file
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Export Debug Report"))

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = DashboardFragment()
    }
}
