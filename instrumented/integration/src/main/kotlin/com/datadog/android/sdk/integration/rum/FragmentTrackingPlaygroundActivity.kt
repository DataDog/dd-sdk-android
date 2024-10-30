/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("DEPRECATION")

package com.datadog.android.sdk.integration.rum

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.datadog.android.Datadog
import com.datadog.android.rum.Rum
import com.datadog.android.rum.tracking.FragmentViewTrackingStrategy
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.utils.getTrackingConsent

internal class FragmentTrackingPlaygroundActivity : AppCompatActivity() {
    lateinit var viewPager: ViewPager
    lateinit var btnNext: Button
    lateinit var btnLast: Button

    @Suppress("CheckInternal")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = RuntimeConfig.configBuilder()
            .build()
        val trackingConsent = intent.getTrackingConsent()

        Datadog.setVerbosity(Log.VERBOSE)
        val sdkCore = Datadog.initialize(this, config, trackingConsent)
        checkNotNull(sdkCore)

        val rumConfig = RuntimeConfig.rumConfigBuilder()
            .trackUserInteractions()
            .trackLongTasks(RuntimeConfig.LONG_TASK_LARGE_THRESHOLD)
            .useViewTrackingStrategy(FragmentViewTrackingStrategy(true))
            .build()
        Rum.enable(rumConfig, sdkCore)

        setContentView(R.layout.fragment_tracking_layout)
        viewPager = findViewById(R.id.pager)
        btnNext = findViewById(R.id.btn_next)
        btnLast = findViewById(R.id.btn_last)
        viewPager.apply {
            adapter = ViewPagerAdapter(supportFragmentManager)
        }
        btnNext.setOnClickListener {
            viewPager.setCurrentItem(viewPager.currentItem + 1, true)
        }
        btnLast.setOnClickListener {
            viewPager.setCurrentItem(viewPager.currentItem - 1, true)
        }

        // attach the fragment view tracking strategy
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

        override fun getCount(): Int {
            return 3
        }
    }
}
