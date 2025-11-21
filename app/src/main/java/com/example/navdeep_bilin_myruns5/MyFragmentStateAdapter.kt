package com.example.navdeep_bilin_myruns5

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter


// lecture reference for viewPager2 and fragmentStateAdapter
class MyFragmentStateAdapter(
    activity: FragmentActivity,
    private val fragments: List<Fragment> // created our own implementation where we pass in the list
) : FragmentStateAdapter(activity) {

    // lecture reference for fragments, lecture 5
    override fun getItemCount() = fragments.size
    override fun createFragment(position: Int) = fragments[position]
}