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
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.SecurityConfig
import com.datadog.android.nightly.TEST_METHOD_NAME_KEY
import com.datadog.android.nightly.rules.NightlyTestRule
import com.datadog.android.nightly.services.NdkCrashService
import com.datadog.android.nightly.services.NdkHandlerDisabledNdkCrashService
import com.datadog.android.nightly.services.RumEnabledNdkCrashService
import com.datadog.android.nightly.services.RumEncryptionEnabledNdkCrashService
import com.datadog.android.nightly.utils.NeverUseThatEncryption
import com.datadog.android.nightly.utils.initializeSdk
import com.datadog.android.nightly.utils.stopSdk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class NdkCrashHandlerE2ETests {

    // region Tests

    @get:Rule
    val nightlyTestRule = NightlyTestRule()

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun build(): Configuration
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun addPlugin(com.datadog.android.plugin.DatadogPlugin, com.datadog.android.plugin.Feature): Builder
     */
    @Test
    fun ndk_crash_reports_rum_enabled() {
        // We initialize the SDK in this process as both processes are sharing the same data
        // storage space. Flushing the data in this process at the end of this test will also
        // flush the data produced by the service process. Besides this our SDK has a safety
        // measure to prevent sending an event twice from 2 different process. For this reason
        // we are using a NoOpOkHttpUploader in case the process is not the app main process.
        val testMethodName = "ndk_crash_reports_rum_enabled"
        initializeSdk(InstrumentationRegistry.getInstrumentation().targetContext)
        startService(
            testMethodName,
            RumEnabledNdkCrashService::class.java
        )
        waitForProcessToIdle()
        stopService(RumEnabledNdkCrashService::class.java)
        // we stop and initialize the SDK again to handle the preserved ndk crash
        stopSdk()
        initializeSdk(InstrumentationRegistry.getInstrumentation().targetContext)
        waitForProcessToIdle()
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun build(): Configuration
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun addPlugin(com.datadog.android.plugin.DatadogPlugin, com.datadog.android.plugin.Feature): Builder
     * apiMethodSignature: com.datadog.android.core.configuration.SecurityConfig#constructor(com.datadog.android.security.Encryption?)
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun setSecurityConfig(SecurityConfig): Builder
     * apiMethodSignature: com.datadog.android.security.Encryption#fun encrypt(ByteArray): ByteArray
     * apiMethodSignature: com.datadog.android.security.Encryption#fun decrypt(ByteArray): ByteArray
     */
    @Test
    fun ndk_crash_reports_rum_enabled_with_encryption() {
        val testMethodName = "ndk_crash_reports_rum_enabled_with_encryption"
        initializeSdk(InstrumentationRegistry.getInstrumentation().targetContext)
        startService(
            testMethodName,
            RumEncryptionEnabledNdkCrashService::class.java
        )
        waitForProcessToIdle()
        stopService(RumEncryptionEnabledNdkCrashService::class.java)
        // we stop and initialize the SDK again to handle the preserved ndk crash
        stopSdk()
        initializeSdk(
            InstrumentationRegistry.getInstrumentation().targetContext,
            // need that to be able to read encrypted data written by NDK crash service
            config = Configuration
                .Builder(
                    logsEnabled = true,
                    tracesEnabled = true,
                    crashReportsEnabled = true,
                    rumEnabled = true,
                    sessionReplayEnabled = true
                )
                .setSecurityConfig(SecurityConfig(localDataEncryption = NeverUseThatEncryption()))
                .build()
        )
        waitForProcessToIdle()
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun build(): Configuration
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun addPlugin(com.datadog.android.plugin.DatadogPlugin, com.datadog.android.plugin.Feature): Builder
     */
    @Test
    fun ndk_crash_reports_feature_disabled() {
        val testMethodName = "ndk_crash_reports_feature_disabled"
        initializeSdk(InstrumentationRegistry.getInstrumentation().targetContext)
        startService(
            testMethodName,
            NdkHandlerDisabledNdkCrashService::class.java
        )
        waitForProcessToIdle()
        stopService(NdkHandlerDisabledNdkCrashService::class.java)
        // we stop and initialize the SDK again to handle the preserved ndk crash
        stopSdk()
        initializeSdk(InstrumentationRegistry.getInstrumentation().targetContext)
        waitForProcessToIdle()
    }

    // endregion

    // region Internal

    private fun <T : NdkCrashService> startService(
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

    private fun <T : NdkCrashService> stopService(serviceClass: Class<T>) {
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
