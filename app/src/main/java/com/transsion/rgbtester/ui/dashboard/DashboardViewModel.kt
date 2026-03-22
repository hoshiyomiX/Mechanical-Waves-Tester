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

    private val _deviceInfo = MutableLiveData<SystemInfoReader.DeviceInfo>()
    val deviceInfo: LiveData<SystemInfoReader.DeviceInfo> = _deviceInfo

    private val _ledInfo = MutableLiveData<SystemInfoReader.LEDInfo>()
    val ledInfo: LiveData<SystemInfoReader.LEDInfo> = _ledInfo

    private val _serviceStatus = MutableLiveData<ServiceStatusDisplay>()
    val serviceStatus: LiveData<ServiceStatusDisplay> = _serviceStatus

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    data class ServiceStatusDisplay(
        val lightHal: String,
        val tcLedService: String
    )

    fun loadInfo() {
        viewModelScope.launch {
            _isLoading.value = true
            
            // Load device info
            val deviceInfo = withContext(Dispatchers.IO) {
                systemInfoReader.getDeviceInfo()
            }
            _deviceInfo.value = deviceInfo

            // Load LED info
            val ledInfo = withContext(Dispatchers.IO) {
                systemInfoReader.getLEDInfo()
            }
            _ledInfo.value = ledInfo

            // Load service status
            val serviceStatus = withContext(Dispatchers.IO) {
                systemInfoReader.getServiceStatus()
            }
            _serviceStatus.value = ServiceStatusDisplay(
                lightHal = serviceStatus.lightHal,
                tcLedService = serviceStatus.tcLedService
            )
            
            _isLoading.value = false
        }
    }
}
