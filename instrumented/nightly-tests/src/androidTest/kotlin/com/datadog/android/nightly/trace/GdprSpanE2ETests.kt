/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.trace

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.nightly.rules.NightlyTestRule
import com.datadog.android.nightly.utils.initializeSdk
import com.datadog.android.nightly.utils.measureSdkInitialize
import com.datadog.android.nightly.utils.measureSetTrackingConsent
import com.datadog.android.privacy.TrackingConsent
import fr.xgouchet.elmyr.junit4.ForgeRule
import io.opentracing.util.GlobalTracer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class GdprSpanE2ETests {

    @get:Rule
    val forge = ForgeRule()

    @get:Rule
    val nightlyTestRule = NightlyTestRule()

    /**
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#constructor()
     * apiMethodSignature: com.datadog.android.core.configuration.Credentials#constructor(String, String, String, String?, String? = null)
     */
    @Test
    fun span_config_consent_pending() {
        val testMethodName = "span_config_consent_pending"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                consent = TrackingConsent.PENDING
            )
        }
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#constructor()
     * apiMethodSignature: com.datadog.android.core.configuration.Credentials#constructor(String, String, String, String?, String? = null)
     */
    @Test
    fun span_config_consent_granted() {
        val testMethodName = "span_config_consent_granted"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                consent = TrackingConsent.GRANTED
            )
        }
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#constructor()
     * apiMethodSignature: com.datadog.android.core.configuration.Credentials#constructor(String, String, String, String?, String? = null)
     */
    @Test
    fun span_config_consent_not_granted() {
        val testMethodName = "span_config_consent_not_granted"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                consent = TrackingConsent.NOT_GRANTED
            )
        }
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#constructor()
     * apiMethodSignature: com.datadog.android.Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent)
     */
    @Test
    fun span_config_consent_pending_to_granted() {
        val testMethodName = "span_config_consent_pending_to_granted"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                consent = TrackingConsent.PENDING
            )
        }
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .finish()
        measureSetTrackingConsent {
            Datadog.setTrackingConsent(TrackingConsent.GRANTED)
        }
    }

    /**
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#constructor()
     * apiMethodSignature: com.datadog.android.Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent)
     */
    @Test
    fun span_config_consent_pending_to_not_granted() {
        val testMethodName = "span_config_consent_pending_to_not_granted"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                consent = TrackingConsent.PENDING
            )
        }
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .finish()
        measureSetTrackingConsent {
            Datadog.setTrackingConsent(TrackingConsent.NOT_GRANTED)
        }
    }

    /**
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#constructor()
     * apiMethodSignature: com.datadog.android.Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent)
     */
    @Test
    fun span_config_consent_granted_to_not_granted() {
        val testMethodName = "span_config_consent_granted_to_not_granted"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                consent = TrackingConsent.GRANTED
            )
        }
        measureSetTrackingConsent {
            Datadog.setTrackingConsent(TrackingConsent.NOT_GRANTED)
        }
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#constructor()
     * apiMethodSignature: com.datadog.android.Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent)
     */
    @Test
    fun span_config_consent_not_granted_to_granted() {
        val testMethodName = "span_config_consent_not_granted_to_granted"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                consent = TrackingConsent.NOT_GRANTED
            )
        }
        measureSetTrackingConsent {
            Datadog.setTrackingConsent(TrackingConsent.GRANTED)
        }
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#constructor()
     * apiMethodSignature: com.datadog.android.Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent)
     */
    @Test
    fun span_config_consent_not_granted_to_pending() {
        val testMethodName = "span_config_consent_not_granted_to_pending"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                consent = TrackingConsent.NOT_GRANTED
            )
        }
        measureSetTrackingConsent {
            Datadog.setTrackingConsent(TrackingConsent.PENDING)
        }
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .finish()
    }

    /**
     * apiMethodSignature: com.datadog.android.trace.AndroidTracer$Builder#constructor()
     * apiMethodSignature: com.datadog.android.Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent)
     */
    @Test
    fun span_config_consent_granted_to_pending() {
        val testMethodName = "span_config_consent_granted_to_pending"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                consent = TrackingConsent.GRANTED
            )
        }
        measureSetTrackingConsent {
            Datadog.setTrackingConsent(TrackingConsent.PENDING)
        }
        GlobalTracer.get()
            .buildSpan(testMethodName)
            .start()
            .finish()
    }
}
