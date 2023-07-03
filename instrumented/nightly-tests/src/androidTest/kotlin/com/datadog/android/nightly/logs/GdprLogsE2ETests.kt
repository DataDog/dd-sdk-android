/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.logs

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.log.Logger
import com.datadog.android.nightly.rules.NightlyTestRule
import com.datadog.android.nightly.utils.initializeSdk
import com.datadog.android.nightly.utils.measureLoggerInitialize
import com.datadog.android.nightly.utils.measureSdkInitialize
import com.datadog.android.nightly.utils.measureSetTrackingConsent
import com.datadog.android.privacy.TrackingConsent
import fr.xgouchet.elmyr.junit4.ForgeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class GdprLogsE2ETests {

    lateinit var logger: Logger

    @get:Rule
    val forge = ForgeRule()

    @get:Rule
    val nightlyTestRule = NightlyTestRule()

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Credentials#constructor(String, String, String, String?, String? = null)
     */
    @Test
    fun logs_config_consent_pending() {
        val testMethodName = "logs_config_consent_pending"
        val sdkCore = measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                consent = TrackingConsent.PENDING
            )
        }
        measureLoggerInitialize {
            logger = initializeLogger(sdkCore)
        }
        logger.sendRandomLog(
            testMethodName,
            forge
        )
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Credentials#constructor(String, String, String, String?, String? = null)
     */
    @Test
    fun logs_config_consent_granted() {
        val testMethodName = "logs_config_consent_granted"
        val sdkCore = measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                consent = TrackingConsent.GRANTED
            )
        }
        measureLoggerInitialize {
            logger = initializeLogger(sdkCore)
        }
        logger.sendRandomLog(
            testMethodName,
            forge
        )
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Credentials#constructor(String, String, String, String?, String? = null)
     */
    @Test
    fun logs_config_consent_not_granted() {
        val testMethodName = "logs_config_consent_not_granted"
        val sdkCore = measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                consent = TrackingConsent.NOT_GRANTED
            )
        }
        measureLoggerInitialize {
            logger = initializeLogger(sdkCore)
        }
        logger.sendRandomLog(testMethodName, forge)
    }

    /**
     * apiMethodSignature: com.datadog.android.Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent, com.datadog.android.api.SdkCore = getInstance())
     */
    @Test
    fun logs_config_consent_pending_to_granted() {
        val testMethodName = "logs_config_consent_pending_to_granted"
        val sdkCore = measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                consent = TrackingConsent.PENDING
            )
        }
        measureLoggerInitialize {
            logger = initializeLogger(sdkCore)
        }
        logger.sendRandomLog(testMethodName, forge)
        measureSetTrackingConsent {
            sdkCore.setTrackingConsent(TrackingConsent.GRANTED)
        }
    }

    /**
     * apiMethodSignature: com.datadog.android.Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent, com.datadog.android.api.SdkCore = getInstance())
     */
    @Test
    fun logs_config_consent_pending_to_not_granted() {
        val testMethodName = "logs_config_consent_pending_to_not_granted"
        val sdkCore = measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                consent = TrackingConsent.PENDING
            )
        }
        measureLoggerInitialize {
            logger = initializeLogger(sdkCore)
        }
        logger.sendRandomLog(testMethodName, forge)
        measureSetTrackingConsent {
            sdkCore.setTrackingConsent(TrackingConsent.NOT_GRANTED)
        }
    }

    /**
     * apiMethodSignature: com.datadog.android.Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent, com.datadog.android.api.SdkCore = getInstance())
     */
    @Test
    fun logs_config_consent_granted_to_not_granted() {
        val testMethodName = "logs_config_consent_granted_to_not_granted"
        val sdkCore = measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                consent = TrackingConsent.GRANTED
            )
        }
        measureLoggerInitialize {
            logger = initializeLogger(sdkCore)
        }
        measureSetTrackingConsent {
            sdkCore.setTrackingConsent(TrackingConsent.NOT_GRANTED)
        }
        logger.sendRandomLog(testMethodName, forge)
    }

    /**
     * apiMethodSignature: com.datadog.android.Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent, com.datadog.android.api.SdkCore = getInstance())
     */
    @Test
    fun logs_config_consent_not_granted_to_granted() {
        val testMethodName = "logs_config_consent_not_granted_to_granted"
        val sdkCore = measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                consent = TrackingConsent.NOT_GRANTED
            )
        }
        measureLoggerInitialize {
            logger = initializeLogger(sdkCore)
        }
        measureSetTrackingConsent {
            sdkCore.setTrackingConsent(TrackingConsent.GRANTED)
        }
        logger.sendRandomLog(testMethodName, forge)
    }

    /**
     * apiMethodSignature: com.datadog.android.Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent, com.datadog.android.api.SdkCore = getInstance())
     */
    @Test
    fun logs_config_consent_not_granted_to_pending() {
        val testMethodName = "logs_config_consent_not_granted_to_pending"
        val sdkCore = measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                consent = TrackingConsent.NOT_GRANTED
            )
        }
        measureLoggerInitialize {
            logger = initializeLogger(sdkCore)
        }
        measureSetTrackingConsent {
            sdkCore.setTrackingConsent(TrackingConsent.PENDING)
        }
        logger.sendRandomLog(testMethodName, forge)
    }

    /**
     * apiMethodSignature: com.datadog.android.Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent, com.datadog.android.api.SdkCore = getInstance())
     */
    @Test
    fun logs_config_consent_granted_to_pending() {
        val testMethodName = "logs_config_consent_granted_to_pending"
        val sdkCore = measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                consent = TrackingConsent.GRANTED
            )
        }
        measureLoggerInitialize {
            logger = initializeLogger(sdkCore)
        }
        measureSetTrackingConsent {
            sdkCore.setTrackingConsent(TrackingConsent.PENDING)
        }
        logger.sendRandomLog(testMethodName, forge)
    }
}
