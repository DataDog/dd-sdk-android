/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration

import android.app.Activity
import android.app.Application
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mock
import org.mockito.kotlin.verify

abstract class BaseActivityViewTrackingStrategyTest {

    protected lateinit var stubSdkCore: StubSDKCore

    protected lateinit var testedViewTrackingStrategy: ActivityViewTrackingStrategy

    @Mock
    lateinit var stubActivity: StubActivity

    @Mock
    lateinit var mockApplicationContext: Application

    @StringForgery
    protected lateinit var fakeApplicationId: String

    protected open fun configureRumBuilder(builder: RumConfiguration.Builder) {}

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)

        val builder = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
        configureRumBuilder(builder)
        Rum.enable(builder.build(), stubSdkCore)

        testedViewTrackingStrategy = ActivityViewTrackingStrategy(true)
        testedViewTrackingStrategy.register(stubSdkCore, mockApplicationContext)
        verify(mockApplicationContext).registerActivityLifecycleCallbacks(testedViewTrackingStrategy)
    }

    @AfterEach
    fun `tear down`() {
        testedViewTrackingStrategy.unregister(mockApplicationContext)
        verify(mockApplicationContext).unregisterActivityLifecycleCallbacks(testedViewTrackingStrategy)
    }

    // region Shared Scenarios

    protected fun runOnActivityResumed() {
        testedViewTrackingStrategy.onActivityResumed(stubActivity)
    }

    protected fun runOnActivityResumedAndStopped() {
        testedViewTrackingStrategy.onActivityResumed(stubActivity)
        testedViewTrackingStrategy.onActivityStopped(stubActivity)
        Thread.sleep(250L)
    }

    protected fun runOnActivityStopped() {
        testedViewTrackingStrategy.onActivityStopped(stubActivity)
        Thread.sleep(250L)
    }

    // endregion

    class StubActivity : Activity()
}
