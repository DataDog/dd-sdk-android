/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.rum

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.nightly.aResourceKey
import com.datadog.android.nightly.aResourceMethod
import com.datadog.android.nightly.aViewKey
import com.datadog.android.nightly.aViewName
import com.datadog.android.nightly.anActionName
import com.datadog.android.nightly.anErrorMessage
import com.datadog.android.nightly.rules.NightlyTestRule
import com.datadog.android.nightly.utils.defaultTestAttributes
import com.datadog.android.nightly.utils.executeInsideView
import com.datadog.android.nightly.utils.initializeSdk
import com.datadog.android.nightly.utils.measureSdkInitialize
import com.datadog.android.nightly.utils.measureSetTrackingConsent
import com.datadog.android.nightly.utils.sendRandomActionOutcomeEvent
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
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
     * apiMethodSignature: Credentials#constructor(String, String, String, String?, String? = null)
     */
    @Test
    fun rum_config_consent_pending() {
        val testMethodName = "rum_config_consent_pending"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                consent = TrackingConsent.PENDING
            )
        }
        sendRandomRumEvent(forge, testMethodName)
    }

    /**
     * apiMethodSignature: Credentials#constructor(String, String, String, String?, String? = null)
     */
    @Test
    fun rum_config_consent_granted() {
        val testMethodName = "rum_config_consent_granted"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                consent = TrackingConsent.GRANTED
            )
        }
        sendRandomRumEvent(forge, testMethodName)
    }

    /**
     * apiMethodSignature: Credentials#constructor(String, String, String, String?, String? = null)
     */
    @Test
    fun rum_config_consent_not_granted() {
        val testMethodName = "rum_config_consent_not_granted"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                consent = TrackingConsent.NOT_GRANTED
            )
        }
        sendRandomRumEvent(forge, testMethodName)
    }

    /**
     * apiMethodSignature: Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent)
     */
    @Test
    fun rum_config_consent_pending_to_granted() {
        val testMethodName = "rum_config_consent_pending_to_granted"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                consent = TrackingConsent.PENDING
            )
        }
        sendRandomRumEvent(forge, testMethodName)
        measureSetTrackingConsent {
            Datadog.setTrackingConsent(TrackingConsent.GRANTED)
        }
    }

    /**
     * apiMethodSignature: Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent)
     */
    @Test
    fun rum_config_consent_pending_to_not_granted() {
        val testMethodName = "rum_config_consent_pending_to_not_granted"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                consent = TrackingConsent.PENDING
            )
        }
        sendRandomRumEvent(forge, testMethodName)
        measureSetTrackingConsent {
            Datadog.setTrackingConsent(TrackingConsent.NOT_GRANTED)
        }
    }

    /**
     * apiMethodSignature: Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent)
     */
    @Test
    fun rum_config_consent_granted_to_not_granted() {
        val testMethodName = "rum_config_consent_granted_to_not_granted"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                consent = TrackingConsent.GRANTED
            )
        }
        measureSetTrackingConsent {
            Datadog.setTrackingConsent(TrackingConsent.NOT_GRANTED)
        }
        sendRandomRumEvent(forge, testMethodName)
    }

    /**
     * apiMethodSignature: Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent)
     */
    @Test
    fun rum_config_consent_granted_to_pending() {
        val testMethodName = "rum_config_consent_granted_to_pending"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                consent = TrackingConsent.GRANTED
            )
        }
        measureSetTrackingConsent {
            Datadog.setTrackingConsent(TrackingConsent.PENDING)
        }
        sendRandomRumEvent(forge, testMethodName)
    }

    /**
     * apiMethodSignature: Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent)
     */
    @Test
    fun rum_config_consent_not_granted_to_granted() {
        val testMethodName = "rum_config_consent_not_granted_to_granted"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                consent = TrackingConsent.NOT_GRANTED
            )
        }
        measureSetTrackingConsent {
            Datadog.setTrackingConsent(TrackingConsent.GRANTED)
        }
        sendRandomRumEvent(forge, testMethodName)
    }

    /**
     * apiMethodSignature: Datadog#fun setTrackingConsent(com.datadog.android.privacy.TrackingConsent)
     */
    @Test
    fun rum_config_consent_not_granted_to_pending() {
        val testMethodName = "rum_config_consent_not_granted_to_pending"
        measureSdkInitialize {
            initializeSdk(
                InstrumentationRegistry.getInstrumentation().targetContext,
                consent = TrackingConsent.NOT_GRANTED
            )
        }
        measureSetTrackingConsent {
            Datadog.setTrackingConsent(TrackingConsent.PENDING)
        }
        sendRandomRumEvent(forge, testMethodName)
    }

    private fun sendRandomRumEvent(forge: Forge, testMethodName: String) {
        when (forge.anInt(min = 0, max = 4)) {
            0 -> {
                val aViewKey = forge.aViewKey()
                GlobalRum.get().startView(
                    aViewKey,
                    forge.aViewName(),
                    defaultTestAttributes(testMethodName)
                )
                GlobalRum.get().stopView(aViewKey, defaultTestAttributes(testMethodName))
            }
            1 -> {
                val aResourceKey = forge.aResourceKey()
                executeInsideView(forge.aViewKey(), forge.aViewName(), testMethodName) {
                    GlobalRum.get().startResource(
                        aResourceKey,
                        forge.aResourceMethod(),
                        aResourceKey,
                        defaultTestAttributes(testMethodName)
                    )
                    GlobalRum.get().stopResource(
                        aResourceKey,
                        forge.anInt(min = 200, max = 500),
                        forge.aLong(min = 1),
                        forge.aValueFrom(RumResourceKind::class.java),
                        defaultTestAttributes(testMethodName)
                    )
                }
            }
            2 -> {
                executeInsideView(forge.aViewKey(), forge.aViewName(), testMethodName) {
                    GlobalRum.get().addError(
                        forge.anErrorMessage(),
                        forge.aValueFrom(RumErrorSource::class.java),
                        forge.aNullable { forge.aThrowable() },
                        defaultTestAttributes(testMethodName)
                    )
                }
            }
            3 -> {
                executeInsideView(forge.aViewKey(), forge.aViewName(), testMethodName) {
                    GlobalRum.get().addUserAction(
                        forge.aValueFrom(RumActionType::class.java),
                        forge.anActionName(),
                        defaultTestAttributes(testMethodName)
                    )
                    sendRandomActionOutcomeEvent(forge)
                }
            }
        }
    }
}
