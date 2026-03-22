package com.transsion.rgbtester.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.transsion.rgbtester.ui.dashboard.DashboardFragment
import com.transsion.rgbtester.ui.testing.TestingFragment

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> DashboardFragment.newInstance()
            1 -> TestingFragment.newInstance()
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}
