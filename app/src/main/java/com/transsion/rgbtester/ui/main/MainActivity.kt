package com.transsion.rgbtester.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.transsion.rgbtester.R
import com.transsion.rgbtester.databinding.ActivityMainBinding
import com.transsion.rgbtester.ui.adapter.ViewPagerAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var rootGranted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Request root access immediately at app start
        requestRootAccess()
    }

    private fun requestRootAccess() {
        lifecycleScope.launch {
            try {
                val hasRoot = withContext(Dispatchers.IO) {
                    checkRootAccess()
                }

                if (hasRoot) {
                    rootGranted = true
                    setupViewPager()
                } else {
                    showRootDeniedDialog()
                }
            } catch (e: Exception) {
                showRootDeniedDialog()
            }
        }
    }

    private fun checkRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = process.outputStream
            outputStream.write("id\n".toByteArray())
            outputStream.flush()
            outputStream.write("exit\n".toByteArray())
            outputStream.flush()
            outputStream.close()

            val input = process.inputStream.bufferedReader().readText()
            process.waitFor()

            input.contains("uid=0") || input.contains("root")
        } catch (e: Exception) {
            false
        }
    }

    private fun showRootDeniedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Root Access Required")
            .setMessage("This app requires root access to read system information and control LED hardware.\n\n" +
                    "Please grant root access when prompted and restart the app.\n\n" +
                    "Without root, the app cannot function properly.")
            .setCancelable(false)
            .setPositiveButton("Retry") { _, _ ->
                requestRootAccess()
            }
            .setNegativeButton("Exit") { _, _ ->
                finish()
            }
            .show()
    }

    private fun setupViewPager() {
        val adapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = adapter

        // Connect TabLayout with ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Dashboard"
                1 -> "Testing"
                else -> "Tab $position"
            }
        }.attach()
    }

    override fun onResume() {
        super.onResume()
        // Re-check root if we're returning to the app
        if (!rootGranted) {
            requestRootAccess()
        }
    }
}
