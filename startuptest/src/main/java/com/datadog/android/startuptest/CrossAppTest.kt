/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.startuptest

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CrossAppTest {
    private lateinit var device: UiDevice

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // Start your AUT explicitly
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = context.packageManager.getLaunchIntentForPackage("com.datadog.sample.android")!!.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg("com.datadog.sample.android").depth(0)), 5_000)
    }

    @Test
    fun shareFlow_reachesGmail() {
//        // Interact inside AUT (via resource ids/text)
//        device.findObject(By.res("com.example.myapp:id/share")).click()
//
//        // Now you’re in the system share sheet / another app
//        device.findObject(By.textContains("Gmail")).click()
//        device.wait(Until.hasObject(By.pkg("com.google.android.gm")), 5_000)
//        // Assert something in Gmail compose…
    }
}
