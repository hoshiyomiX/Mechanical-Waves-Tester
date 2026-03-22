package com.transsion.rgbtester.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.transsion.rgbtester.data.local.SystemInfoReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val systemInfoReader = SystemInfoReader()

    // Device Info
    private val _deviceInfo = MutableLiveData<SystemInfoReader.DeviceInfo>()
    val deviceInfo: LiveData<SystemInfoReader.DeviceInfo> = _deviceInfo

    // LED Info
    private val _ledInfo = MutableLiveData<SystemInfoReader.LEDInfo>()
    val ledInfo: LiveData<SystemInfoReader.LEDInfo> = _ledInfo

    // Init Services Info
    private val _initServicesInfo = MutableLiveData<SystemInfoReader.InitServicesInfo>()
    val initServicesInfo: LiveData<SystemInfoReader.InitServicesInfo> = _initServicesInfo

    // HAL Info
    private val _halInfo = MutableLiveData<SystemInfoReader.HALInfo>()
    val halInfo: LiveData<SystemInfoReader.HALInfo> = _halInfo

    // Driver Info
    private val _driverInfo = MutableLiveData<SystemInfoReader.DriverInfo>()
    val driverInfo: LiveData<SystemInfoReader.DriverInfo> = _driverInfo

    // Libraries Info
    private val _librariesInfo = MutableLiveData<SystemInfoReader.LibrariesInfo>()
    val librariesInfo: LiveData<SystemInfoReader.LibrariesInfo> = _librariesInfo

    // SELinux Info
    private val _selinuxInfo = MutableLiveData<SystemInfoReader.SELinuxInfo>()
    val selinuxInfo: LiveData<SystemInfoReader.SELinuxInfo> = _selinuxInfo

    // Device Tree Info
    private val _deviceTreeInfo = MutableLiveData<SystemInfoReader.DeviceTreeInfo>()
    val deviceTreeInfo: LiveData<SystemInfoReader.DeviceTreeInfo> = _deviceTreeInfo

    // Vendor Info
    private val _vendorInfo = MutableLiveData<SystemInfoReader.VendorInfo>()
    val vendorInfo: LiveData<SystemInfoReader.VendorInfo> = _vendorInfo

    // Kernel Info
    private val _kernelInfo = MutableLiveData<SystemInfoReader.KernelInfo>()
    val kernelInfo: LiveData<SystemInfoReader.KernelInfo> = _kernelInfo

    // Loading state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // Error message
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadInfo() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // Load device info (basic info, no root needed for some parts)
                val deviceInfo = withContext(Dispatchers.IO) {
                    systemInfoReader.getDeviceInfo()
                }
                _deviceInfo.value = deviceInfo

                // Load LED hardware info
                val ledInfo = withContext(Dispatchers.IO) {
                    systemInfoReader.getLEDInfo()
                }
                _ledInfo.value = ledInfo

                // Load init services info
                val initServices = withContext(Dispatchers.IO) {
                    systemInfoReader.getInitServicesInfo()
                }
                _initServicesInfo.value = initServices

                // Load HAL info
                val halInfo = withContext(Dispatchers.IO) {
                    systemInfoReader.getHALInfo()
                }
                _halInfo.value = halInfo

                // Load driver info
                val driverInfo = withContext(Dispatchers.IO) {
                    systemInfoReader.getDriverInfo()
                }
                _driverInfo.value = driverInfo

                // Load libraries info
                val librariesInfo = withContext(Dispatchers.IO) {
                    systemInfoReader.getLibrariesInfo()
                }
                _librariesInfo.value = librariesInfo

                // Load SELinux info
                val selinuxInfo = withContext(Dispatchers.IO) {
                    systemInfoReader.getSELinuxInfo()
                }
                _selinuxInfo.value = selinuxInfo

                // Load Device Tree info
                val deviceTreeInfo = withContext(Dispatchers.IO) {
                    systemInfoReader.getDeviceTreeInfo()
                }
                _deviceTreeInfo.value = deviceTreeInfo

                // Load Vendor info
                val vendorInfo = withContext(Dispatchers.IO) {
                    systemInfoReader.getVendorInfo()
                }
                _vendorInfo.value = vendorInfo

                // Load Kernel info
                val kernelInfo = withContext(Dispatchers.IO) {
                    systemInfoReader.getKernelInfo()
                }
                _kernelInfo.value = kernelInfo

            } catch (e: Exception) {
                _errorMessage.value = "Error loading info: ${e.message}"
            }

            _isLoading.value = false
        }
    }

    // Helper functions for UI formatting

    fun getRomTypeDisplay(romType: SystemInfoReader.RomType): String {
        return when (romType) {
            SystemInfoReader.RomType.STOCK -> "Stock ROM ✓"
            SystemInfoReader.RomType.CUSTOM -> "Custom ROM ⚠️"
            SystemInfoReader.RomType.UNKNOWN -> "Unknown"
        }
    }

    fun getSELinuxModeDisplay(mode: String): String {
        return when (mode.lowercase()) {
            "enforcing" -> "Enforcing (Strict)"
            "permissive" -> "Permissive ⚠️"
            "disabled" -> "Disabled"
            else -> mode
        }
    }

    fun getDriverStatusDisplay(status: SystemInfoReader.DriverStatus): String {
        return if (status.loaded) {
            val version = status.version?.let { " v$it" } ?: ""
            val state = status.initstate?.let { " [$it]" } ?: ""
            "${status.path ?: "Loaded"}$version$state"
        } else {
            "Not loaded ❌"
        }
    }

    fun getServiceStatusText(status: String): String {
        return when (status) {
            "running" -> "✅ Running"
            "stopped" -> "⏹️ Stopped"
            "registered" -> "📋 Registered"
            else -> "❌ Not found"
        }
    }

    fun getHALStatusText(hal: SystemInfoReader.HALServiceStatus): String {
        return if (hal.running) {
            "✅ Running ${hal.pid?.let { "(PID: $it)" } ?: ""}"
        } else {
            "❌ Not running"
        }
    }

    fun getVendorStatusDisplay(vendorInfo: SystemInfoReader.VendorInfo): String {
        val status = StringBuilder()

        if (!vendorInfo.vendorPartitionMounted) {
            status.append("❌ Vendor partition not mounted\n")
        } else {
            status.append("✅ Vendor: ${vendorInfo.vendorPartitionType}\n")
        }

        if (!vendorInfo.odmPartitionMounted) {
            status.append("⚠️ ODM partition not mounted\n")
        }

        if (!vendorInfo.productPartitionMounted) {
            status.append("⚠️ Product partition not mounted\n")
        }

        if (vendorInfo.missingVendorFiles.isNotEmpty()) {
            status.append("Missing files: ${vendorInfo.missingVendorFiles.size}\n")
        }

        return status.toString().trim()
    }
}
