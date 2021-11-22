/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.datadog.android.sample.viewpager.FragmentA
import com.datadog.android.sample.viewpager.FragmentB
import com.datadog.android.sample.viewpager.FragmentC

class ViewPagerActivity : AppCompatActivity() {

    lateinit var viewPager: ViewPager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_pager)
        viewPager = findViewById(R.id.view_pager)
        viewPager.adapter = ViewPagerAdapter(supportFragmentManager)
    }

    internal inner class ViewPagerAdapter(fragmentManager: FragmentManager) :
        FragmentPagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        override fun getItem(position: Int): Fragment {
            return when (position) {
                0 -> FragmentA()
                1 -> FragmentB()
                else -> FragmentC()
            }.apply {
                val args = Bundle().apply {
                    putString("fragmentClassName", this::class.java.simpleName)
                    putInt("adapterPosition", position)
                }
                arguments = args
            }
        }

        override fun getPageTitle(position: Int): CharSequence {
            return when (position) {
                0 -> FragmentA.NAME
                1 -> FragmentB.NAME
                else -> FragmentC.NAME
            }
        }

        override fun getCount(): Int {
            return 3
        }
    }
}
