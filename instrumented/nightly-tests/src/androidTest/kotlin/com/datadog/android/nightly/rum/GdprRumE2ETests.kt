/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.rum

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.nightly.rules.NightlyTestRule
import com.datadog.android.nightly.utils.initializeSdk
import com.datadog.android.nightly.utils.measureSdkInitialize
import com.datadog.android.nightly.utils.measureSetTrackingConsent
import com.datadog.android.privacy.TrackingConsent
import fr.xgouchet.elmyr.junit4.ForgeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class GdprRumE2ETests {

    @get:Rule
    val forge = ForgeRule()

    @get:Rule
    val nightlyTestRule = NightlyTestRule()

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Credentials#constructor(String, String, String, String?, String? = null)
     */
    @Test
    fun rum_config_consent_pending() {
        val testMethodName = "rum_config_consent_pending"
        val sdkCore = measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                consent = TrackingConsent.PENDING
            )
        }
        sendRandomRumEvent(forge, sdkCore, testMethodName)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Credentials#constructor(String, String, String, String?, String? = null)
     */
    @Test
    fun rum_config_consent_granted() {
        val testMethodName = "rum_config_consent_granted"
        val sdkCore = measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                consent = TrackingConsent.GRANTED
            )
        }
        sendAllRumEvents(forge, sdkCore, testMethodName)
    }

    /**
     * apiMethodSignature: com.datadog.android.core.configuration.Credentials#constructor(String, String, String, String?, String? = null)
     */
    @Test
    fun rum_config_consent_not_granted() {
        val testMethodName = "rum_config_consent_not_granted"
        val sdkCore = measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                consent = TrackingConsent.NOT_GRANTED
            )
        }
        sendRandomRumEvent(forge, sdkCore, testMethodName)
    }

    /**
     * apiMethodSignature: com.datadog.android.Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent, com.datadog.android.api.SdkCore = getInstance())
     */
    @Test
    fun rum_config_consent_pending_to_granted() {
        val testMethodName = "rum_config_consent_pending_to_granted"
        val sdkCore = measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                consent = TrackingConsent.PENDING
            )
        }
        sendAllRumEvents(forge, sdkCore, testMethodName)
        measureSetTrackingConsent {
            sdkCore.setTrackingConsent(TrackingConsent.GRANTED)
        }
    }

    /**
     * apiMethodSignature: com.datadog.android.Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent, com.datadog.android.api.SdkCore = getInstance())
     */
    @Test
    fun rum_config_consent_pending_to_not_granted() {
        val testMethodName = "rum_config_consent_pending_to_not_granted"
        val sdkCore = measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                consent = TrackingConsent.PENDING
            )
        }
        sendRandomRumEvent(forge, sdkCore, testMethodName)
        measureSetTrackingConsent {
            sdkCore.setTrackingConsent(TrackingConsent.NOT_GRANTED)
        }
    }

    /**
     * apiMethodSignature: com.datadog.android.Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent, com.datadog.android.api.SdkCore = getInstance())
     */
    @Test
    fun rum_config_consent_granted_to_not_granted() {
        val testMethodName = "rum_config_consent_granted_to_not_granted"
        val sdkCore = measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                consent = TrackingConsent.GRANTED
            )
        }
        measureSetTrackingConsent {
            sdkCore.setTrackingConsent(TrackingConsent.NOT_GRANTED)
        }
        sendRandomRumEvent(forge, sdkCore, testMethodName)
    }

    /**
     * apiMethodSignature: com.datadog.android.Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent, com.datadog.android.api.SdkCore = getInstance())
     */
    @Test
    fun rum_config_consent_granted_to_pending() {
        val testMethodName = "rum_config_consent_granted_to_pending"
        val sdkCore = measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                consent = TrackingConsent.GRANTED
            )
        }
        measureSetTrackingConsent {
            sdkCore.setTrackingConsent(TrackingConsent.PENDING)
        }
        sendRandomRumEvent(forge, sdkCore, testMethodName)
    }

    /**
     * apiMethodSignature: com.datadog.android.Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent, com.datadog.android.api.SdkCore = getInstance())
     */
    @Test
    fun rum_config_consent_not_granted_to_granted() {
        val testMethodName = "rum_config_consent_not_granted_to_granted"
        val sdkCore = measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                consent = TrackingConsent.NOT_GRANTED
            )
        }
        measureSetTrackingConsent {
            sdkCore.setTrackingConsent(TrackingConsent.GRANTED)
        }
        sendAllRumEvents(forge, sdkCore, testMethodName)
    }

    /**
     * apiMethodSignature: com.datadog.android.Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent, com.datadog.android.api.SdkCore = getInstance())
     */
    @Test
    fun rum_config_consent_not_granted_to_pending() {
        val testMethodName = "rum_config_consent_not_granted_to_pending"
        val sdkCore = measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                forgeSeed = forge.seed,
                consent = TrackingConsent.NOT_GRANTED
            )
        }
        measureSetTrackingConsent {
            sdkCore.setTrackingConsent(TrackingConsent.PENDING)
        }
        sendRandomRumEvent(forge, sdkCore, testMethodName)
    }
}
