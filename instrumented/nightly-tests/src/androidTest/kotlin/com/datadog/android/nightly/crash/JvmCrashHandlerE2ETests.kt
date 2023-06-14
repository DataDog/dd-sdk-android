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
import fr.xgouchet.elmyr.junit4.ForgeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class JvmCrashHandlerE2ETests {

    // region Tests

    @get:Rule
    val forgeRule = ForgeRule()

    @get:Rule
    val nightlyTestRule = NightlyTestRule()

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun build(): Configuration
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#constructor(Boolean)
     * apiMethodSignature: com.datadog.android.rum.RumConfiguration$Builder#constructor(String)
     * apiMethodSignature: com.datadog.android.rum.RumConfiguration$Builder#fun build(): RumConfiguration
     */
    @Test
    fun crash_reports_rum_enabled() {
        // We initialize the SDK in this process as both processes are sharing the same data
        // storage space. Flushing the data in this process at the end of this test will also
        // flush the data produced by the service process. Besides this our SDK has a safety
        // measure to prevent sending an event twice from 2 different process. For this reason
        // we are using a NoOpOkHttpUploader in case the process is not the app main process.
        val testMethodName = "crash_reports_rum_enabled"
        initializeSdk(
            InstrumentationRegistry.getInstrumentation().targetContext,
            forgeSeed = forgeRule.seed
        )
        startService(
            testMethodName,
            RumEnabledCrashService::class.java
        )
        waitForProcessToIdle()
        stopService(RumEnabledCrashService::class.java)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun build(): Configuration
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#constructor(Boolean)
     */
    @Test
    fun crash_reports_rum_disabled() {
        val testMethodName = "crash_reports_rum_disabled"
        initializeSdk(
            InstrumentationRegistry.getInstrumentation().targetContext,
            forgeSeed = forgeRule.seed
        )
        startService(
            testMethodName,
            RumDisabledCrashService::class.java
        )
        waitForProcessToIdle()
        stopService(RumDisabledCrashService::class.java)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun build(): Configuration
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#constructor(Boolean)
     */
    @Test
    fun crash_reports_feature_disabled() {
        val testMethodName = "crash_reports_feature_disabled"
        initializeSdk(
            InstrumentationRegistry.getInstrumentation().targetContext,
            forgeSeed = forgeRule.seed
        )
        startService(
            testMethodName,
            CrashHandlerDisabledCrashService::class.java
        )
        waitForProcessToIdle()
        stopService(CrashHandlerDisabledCrashService::class.java)
    }

    // endregion

    // region Internal

    private fun <T : JvmCrashService> startService(
        testMethodName: String,
        serviceClass: Class<T>
    ) {
        InstrumentationRegistry.getInstrumentation().targetContext.startService(
            Intent(
                InstrumentationRegistry.getInstrumentation().targetContext,
                serviceClass
            ).apply {
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

    private fun waitForProcessToIdle() {
        Thread.sleep(WAIT_FOR_IDLE_TIME_IN_MS)
    }

    // endregion

    companion object {
        const val WAIT_FOR_IDLE_TIME_IN_MS = 10000L
    }
}
