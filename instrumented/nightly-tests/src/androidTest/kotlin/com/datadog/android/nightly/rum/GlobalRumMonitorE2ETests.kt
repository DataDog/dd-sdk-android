/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.rum

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.api.SdkCore
import com.datadog.android.nightly.rules.NightlyTestRule
import com.datadog.android.nightly.utils.aResourceKey
import com.datadog.android.nightly.utils.aResourceMethod
import com.datadog.android.nightly.utils.aViewKey
import com.datadog.android.nightly.utils.aViewName
import com.datadog.android.nightly.utils.anActionName
import com.datadog.android.nightly.utils.anErrorMessage
import com.datadog.android.nightly.utils.defaultTestAttributes
import com.datadog.android.nightly.utils.executeInsideView
import com.datadog.android.nightly.utils.initializeSdk
import com.datadog.android.nightly.utils.measure
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.junit4.ForgeRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class GlobalRumMonitorE2ETests {
    @get:Rule
    val forge = ForgeRule()

    @get:Rule
    val nightlyTestRule = NightlyTestRule()

    lateinit var sdkCore: SdkCore

    /**
     * apiMethodSignature: com.datadog.android.Datadog#fun initialize(android.content.Context, com.datadog.android.core.configuration.Configuration, com.datadog.android.privacy.TrackingConsent): com.datadog.android.api.SdkCore?
     * apiMethodSignature: com.datadog.android.Datadog#fun initialize(String?, android.content.Context, com.datadog.android.core.configuration.Configuration, com.datadog.android.privacy.TrackingConsent): com.datadog.android.api.SdkCore?
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#fun build(): Configuration
     * apiMethodSignature: com.datadog.android.core.configuration.Configuration$Builder#constructor(String, String, String = NO_VARIANT, String? = null)
     * apiMethodSignature: com.datadog.android.rum.RumConfiguration$Builder#constructor(String)
     * apiMethodSignature: com.datadog.android.rum.RumConfiguration$Builder#fun build(): RumConfiguration
     * apiMethodSignature: com.datadog.android.rum.GlobalRumMonitor#fun get(com.datadog.android.api.SdkCore = Datadog.getInstance()): RumMonitor
     * apiMethodSignature: com.datadog.android.rum.GlobalRumMonitor#fun isRegistered(com.datadog.android.api.SdkCore = Datadog.getInstance()): Boolean
     * apiMethodSignature: com.datadog.android.rum.Rum#fun enable(RumConfiguration, com.datadog.android.api.SdkCore = Datadog.getInstance())
     */
    @Before
    fun setUp() {
        sdkCore = initializeSdk(
            InstrumentationRegistry.getInstrumentation().targetContext,
            forgeSeed = forge.seed
        )
    }

    // region View

    /**
     * apiMethodSignature: com.datadog.android.rum.GlobalRumMonitor#fun addAttribute(String, Any?)
     */
    @Test
    fun rum_globalrum_add_attribute_for_view() {
        val testMethodName = "rum_globalrum_add_attribute_for_view"
        val viewKey = forge.aViewKey()
        val viewName = forge.aViewName()
        val strAttrValue = forge.anAlphabeticalString()
        val intAttrValue = forge.anInt()
        addAttributesMeasured(strAttrValue, intAttrValue)
        GlobalRumMonitor.get(sdkCore).startView(
            viewKey,
            viewName,
            defaultTestAttributes(testMethodName)
        )
        GlobalRumMonitor.get(sdkCore).stopView(viewKey)
        Thread.sleep(WRITE_DELAY_MS)
        removeAttributes()
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.GlobalRumMonitor#fun removeAttribute(String)
     */
    @Test
    fun rum_globalrum_remove_attribute_for_view() {
        val testMethodName = "rum_globalrum_remove_attribute_for_view"
        val viewKey = forge.aViewKey()
        val viewName = forge.aViewName()
        val strAttrValue = forge.anAlphabeticalString()
        val intAttrValue = forge.anInt()
        addAttributes(strAttrValue, intAttrValue)
        removeAttributesMeasured()
        GlobalRumMonitor.get(sdkCore).startView(
            viewKey,
            viewName,
            defaultTestAttributes(testMethodName)
        )
        GlobalRumMonitor.get(sdkCore).stopView(viewKey)
    }

    // endregion

    // region Action

    /**
     * apiMethodSignature: com.datadog.android.rum.GlobalRumMonitor#fun addAttribute(String, Any?)
     */
    @Test
    fun rum_globalrum_add_attribute_for_action() {
        val testMethodName = "rum_globalrum_add_attribute_for_action"
        val viewKey = forge.aViewKey()
        val viewName = forge.aViewName()
        val actionName = forge.anAlphabeticalString()
        val strAttrValue = forge.anAlphabeticalString()
        val intAttrValue = forge.anInt()

        executeInsideView(viewKey, viewName, testMethodName, sdkCore) {
            addAttributesMeasured(strAttrValue, intAttrValue)
            GlobalRumMonitor.get(sdkCore).addAction(
                RumActionType.CUSTOM,
                actionName,
                attributes = defaultTestAttributes(testMethodName)
            )
            // wait for the action to be inactive
            Thread.sleep(ACTION_INACTIVITY_THRESHOLD_MS)
            removeAttributes()
        }
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.GlobalRumMonitor#fun removeAttribute(String)
     */
    @Test
    fun rum_globalrum_remove_attribute_for_action() {
        val testMethodName = "rum_globalrum_remove_attribute_for_action"
        val viewKey = forge.aViewKey()
        val viewName = forge.aViewName()
        val actionName = forge.anActionName()
        val strAttrValue = forge.anAlphabeticalString()
        val intAttrValue = forge.anInt()

        executeInsideView(viewKey, viewName, testMethodName, sdkCore) {
            addAttributes(strAttrValue, intAttrValue)
            removeAttributesMeasured()
            GlobalRumMonitor.get(sdkCore).addAction(
                RumActionType.CUSTOM,
                actionName,
                attributes = defaultTestAttributes(testMethodName)
            )
            // wait for the action to be inactive
            Thread.sleep(ACTION_INACTIVITY_THRESHOLD_MS)
        }
    }

    // endregion

    // region Resource

    /**
     * apiMethodSignature: com.datadog.android.rum.GlobalRumMonitor#fun addAttribute(String, Any?)
     */
    @Test
    fun rum_globalrum_add_attribute_for_resource() {
        val testMethodName = "rum_globalrum_add_attribute_for_resource"
        val viewKey = forge.aViewKey()
        val viewName = forge.aViewName()
        val resourceKey = forge.aResourceKey()
        val strAttrValue = forge.anAlphabeticalString()
        val intAttrValue = forge.anInt()

        executeInsideView(viewKey, viewName, testMethodName, sdkCore) {
            addAttributesMeasured(strAttrValue, intAttrValue)
            GlobalRumMonitor.get(sdkCore).startResource(
                resourceKey,
                forge.aResourceMethod(),
                resourceKey,
                attributes = defaultTestAttributes(testMethodName)
            )
            Thread.sleep(100)
            GlobalRumMonitor.get(sdkCore).stopResource(
                resourceKey,
                200,
                forge.aLong(min = 1),
                forge.aValueFrom(RumResourceKind::class.java),
                defaultTestAttributes(testMethodName)
            )
            Thread.sleep(WRITE_DELAY_MS)
            removeAttributes()
        }
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.GlobalRumMonitor#fun removeAttribute(String)
     */
    @Test
    fun rum_globalrum_remove_attribute_for_resource() {
        val testMethodName = "rum_globalrum_remove_attribute_for_resource"
        val viewKey = forge.aViewKey()
        val viewName = forge.aViewName()
        val resourceKey = forge.aResourceKey()
        val strAttrValue = forge.anAlphabeticalString()
        val intAttrValue = forge.anInt()

        executeInsideView(viewKey, viewName, testMethodName, sdkCore) {
            addAttributes(strAttrValue, intAttrValue)
            removeAttributesMeasured()
            GlobalRumMonitor.get(sdkCore).startResource(
                resourceKey,
                forge.aResourceMethod(),
                resourceKey,
                attributes = defaultTestAttributes(testMethodName)
            )
            Thread.sleep(100)
            GlobalRumMonitor.get(sdkCore).stopResource(
                resourceKey,
                200,
                forge.aLong(min = 1),
                forge.aValueFrom(RumResourceKind::class.java),
                defaultTestAttributes(testMethodName)
            )
        }
    }

    // endregion

    // region Error

    /**
     * apiMethodSignature: com.datadog.android.rum.GlobalRumMonitor#fun addAttribute(String, Any?)
     */
    @Test
    fun rum_globalrum_add_attribute_for_error() {
        val testMethodName = "rum_globalrum_add_attribute_for_error"
        val viewKey = forge.aViewKey()
        val viewName = forge.aViewName()
        val errorMessage = forge.anErrorMessage()
        val strAttrValue = forge.anAlphabeticalString()
        val intAttrValue = forge.anInt()

        executeInsideView(viewKey, viewName, testMethodName, sdkCore) {
            addAttributesMeasured(strAttrValue, intAttrValue)
            GlobalRumMonitor.get(sdkCore).addError(
                errorMessage,
                forge.aValueFrom(RumErrorSource::class.java),
                forge.aNullable { forge.aThrowable() },
                defaultTestAttributes(testMethodName)
            )
            Thread.sleep(WRITE_DELAY_MS)
            removeAttributes()
        }
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.GlobalRumMonitor#fun removeAttribute(String)
     */
    @Test
    fun rum_globalrum_remove_attribute_for_error() {
        val testMethodName = "rum_globalrum_remove_attribute_for_error"
        val viewKey = forge.aViewKey()
        val viewName = forge.aViewName()
        val errorMessage = forge.anErrorMessage()
        val strAttrValue = forge.anAlphabeticalString()
        val intAttrValue = forge.anInt()

        executeInsideView(viewKey, viewName, testMethodName, sdkCore) {
            addAttributes(strAttrValue, intAttrValue)
            removeAttributesMeasured()
            GlobalRumMonitor.get(sdkCore).addError(
                errorMessage,
                forge.aValueFrom(RumErrorSource::class.java),
                forge.aNullable { forge.aThrowable() },
                defaultTestAttributes(testMethodName)
            )
        }
    }

    // endregion

    // region Internal

    private fun addAttributesMeasured(strAttrValue: String, intAttrValue: Int) {
        measure(MEASURE_TEST_METHOD_ADD) {
            GlobalRumMonitor.get(sdkCore).addAttribute(RUM_CUSTOM_STR_ATTRIBUTE, strAttrValue)
        }
        measure(MEASURE_TEST_METHOD_ADD) {
            GlobalRumMonitor.get(sdkCore).addAttribute(RUM_CUSTOM_INT_ATTRIBUTE, intAttrValue)
        }
    }

    private fun addAttributes(strAttrValue: String, intAttrValue: Int) {
        GlobalRumMonitor.get(sdkCore).addAttribute(RUM_CUSTOM_STR_ATTRIBUTE, strAttrValue)
        GlobalRumMonitor.get(sdkCore).addAttribute(RUM_CUSTOM_INT_ATTRIBUTE, intAttrValue)
    }

    private fun removeAttributesMeasured() {
        measure(MEASURE_TEST_METHOD_REMOVE) {
            GlobalRumMonitor.get(sdkCore).removeAttribute(RUM_CUSTOM_STR_ATTRIBUTE)
        }
        measure(MEASURE_TEST_METHOD_REMOVE) {
            GlobalRumMonitor.get(sdkCore).removeAttribute(RUM_CUSTOM_INT_ATTRIBUTE)
        }
    }

    private fun removeAttributes() {
        GlobalRumMonitor.get(sdkCore).removeAttribute(RUM_CUSTOM_STR_ATTRIBUTE)
        GlobalRumMonitor.get(sdkCore).removeAttribute(RUM_CUSTOM_INT_ATTRIBUTE)
    }

    // endregion

    companion object {
        const val MEASURE_TEST_METHOD_ADD = "rum_globalrum_add_attribute"
        const val MEASURE_TEST_METHOD_REMOVE = "rum_globalrum_remove_attribute"
    }
}
