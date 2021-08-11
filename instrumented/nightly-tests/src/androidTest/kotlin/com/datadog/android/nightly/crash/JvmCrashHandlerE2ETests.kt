/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.crash

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.nightly.TEST_METHOD_NAME_KEY
import com.datadog.android.nightly.rules.NightlyTestRule
import com.datadog.android.nightly.services.CrashHandlerDisabledCrashService
import com.datadog.android.nightly.services.JvmCrashService
import com.datadog.android.nightly.services.RumDisabledCrashService
import com.datadog.android.nightly.services.RumEnabledCrashService
import com.datadog.android.nightly.utils.initializeSdk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class JvmCrashHandlerE2ETests {

    @get:Rule
    val nightlyTestRule = NightlyTestRule()

    @Test
    fun crash_reports_config_enabled() {
        // We initialize the SDK in this process as both processes are sharing the same data
        // storage space. Flushing the data in this process at the end of this test will also
        // flush the data produced by the service process. Besides this our SDK has a safety
        // measure to prevent sending an even twice from 2 different process. For this reason
        // we are using a NoOpOkHttpUploader in case the process is not the app main process.
        val testMethodName = "crash_reports_rum_enabled"
        initializeSdk(InstrumentationRegistry.getInstrumentation().targetContext)
        startService(
            testMethodName,
            JvmCrashService.RUM_ENABLED_SCENARIO,
            RumEnabledCrashService::class.java
        )
        waitForProcessToIdle()
        stopService(RumEnabledCrashService::class.java)
    }

    @Test
    fun crash_reports_config_disabled() {
        val testMethodName = "crash_reports_feature_disabled"
        initializeSdk(InstrumentationRegistry.getInstrumentation().targetContext)
        startService(
            testMethodName,
            JvmCrashService.CRASH_REPORTS_DISABLED_SCENARIO,
            CrashHandlerDisabledCrashService::class.java
        )
        waitForProcessToIdle()
        stopService(CrashHandlerDisabledCrashService::class.java)
    }

    @Test
    fun crash_reports_rum_disabled() {
        val testMethodName = "crash_reports_rum_disabled"
        initializeSdk(InstrumentationRegistry.getInstrumentation().targetContext)
        startService(
            testMethodName,
            JvmCrashService.RUM_DISABLED_SCENARIO,
            RumDisabledCrashService::class.java
        )
        waitForProcessToIdle()
        stopService(RumDisabledCrashService::class.java)
    }

    // region Internal

    private fun <T : JvmCrashService> startService(
        testMethodName: String,
        testAction: String,
        serviceClass: Class<T>
    ) {
        InstrumentationRegistry.getInstrumentation().targetContext.startService(
            Intent(
                InstrumentationRegistry.getInstrumentation().targetContext,
                serviceClass
            ).apply {
                action = testAction
                putExtra(TEST_METHOD_NAME_KEY, testMethodName)
            }
        )
    }

    private fun <T : JvmCrashService> stopService(serviceClass: Class<T>) {
        InstrumentationRegistry.getInstrumentation().targetContext.stopService(
            Intent(
                InstrumentationRegistry.getInstrumentation().targetContext,
                serviceClass
            )
        )
    }

    /**
     * Give some time to the other process to handle the exception.
     */
    private fun waitForProcessToIdle() {
        Thread.sleep(10000)
    }

    // endregion
}
