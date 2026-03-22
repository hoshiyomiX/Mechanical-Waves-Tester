package com.transsion.rgbtester.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.transsion.rgbtester.R
import com.transsion.rgbtester.databinding.FragmentDashboardBinding

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
        
        viewModel.loadInfo()
    }

    private fun setupObservers() {
        // Device Info
        viewModel.deviceInfo.observe(viewLifecycleOwner) { info ->
            binding.tvModel.text = "${info.manufacturer} ${info.model}"
            binding.tvPlatform.text = info.platform
            binding.tvAndroidVersion.text = info.androidVersion
            binding.tvKernel.text = info.kernelVersion.take(50) + "..."
        }

        // LED Info
        viewModel.ledInfo.observe(viewLifecycleOwner) { info ->
            binding.tvLedVariant.text = info.variant
            binding.tvFwVersion.text = info.fwVersion
            binding.tvLedType.text = info.ledType
            binding.tvLedController.text = info.controller
            binding.tvRgbDriver.text = info.rgbDriver
            binding.tvPdlcController.text = info.pdlcController
            
            // Modules
            if (info.modules.isNotEmpty()) {
                binding.tvModules.text = info.modules.joinToString("\n")
            } else {
                binding.tvModules.text = "No LED modules detected"
            }
            
            // Sysfs Paths
            if (info.sysfsPaths.isNotEmpty()) {
                binding.tvSysfsPaths.text = info.sysfsPaths.joinToString("\n")
            } else {
                binding.tvSysfsPaths.text = "No LED sysfs paths found (may need root to access)"
            }
        }

        // Service Status
        viewModel.serviceStatus.observe(viewLifecycleOwner) { status ->
            binding.tvLightHal.text = status.lightHal
            binding.tvTcLedService.text = status.tcLedService
            
            // Color based on status
            val context = requireContext()
            if (status.lightHal == "Running") {
                binding.tvLightHal.setTextColor(context.getColor(R.color.colorSuccess))
            } else {
                binding.tvLightHal.setTextColor(context.getColor(R.color.colorWarning))
            }
            
            if (status.tcLedService == "Running") {
                binding.tvTcLedService.setTextColor(context.getColor(R.color.colorSuccess))
            } else {
                binding.tvTcLedService.setTextColor(context.getColor(R.color.colorWarning))
            }
        }
        
        // Loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.btnRefresh.isEnabled = !isLoading
            binding.btnRefresh.text = if (isLoading) "Loading..." else "Refresh Information"
        }
    }

    private fun setupClickListeners() {
        binding.btnRefresh.setOnClickListener {
            viewModel.loadInfo()
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
