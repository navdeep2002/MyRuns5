package com.example.navdeep_bilin_myruns5

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var tab: TabLayout
    private lateinit var pager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Link Toolbar as ActionBar referenced from week 3 slides
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        // referenced from lecture for findviewbyID and variable binding
        tab = findViewById(R.id.tabLayout)
        pager = findViewById(R.id.viewPager)

        // adpated from viewpager lecture, fragments array, in my own implementation
        val fragments = arrayListOf<Fragment>(
            StartFragment(),
            HistoryFragment(),
            SettingsFragment()
        )
        // used chatgpt here to help with constructor and array usage
        pager.adapter = MyFragmentStateAdapter(this, fragments)

        // minimal AI used here as well for custom titles using lamba formatting
        val titles = arrayOf("START", "HISTORY", "SETTINGS")
        TabLayoutMediator(tab, pager) { t, pos -> t.text = titles[pos] }.attach()
    }
}