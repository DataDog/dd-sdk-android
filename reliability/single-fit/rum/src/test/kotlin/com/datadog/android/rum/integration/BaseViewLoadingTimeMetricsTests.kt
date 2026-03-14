/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration

import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import org.assertj.core.data.Offset
import org.junit.jupiter.api.BeforeEach
import java.util.concurrent.TimeUnit

abstract class BaseViewLoadingTimeMetricsTests {

    protected lateinit var stubSdkCore: StubSDKCore

    @StringForgery
    protected lateinit var fakeApplicationId: String

    @StringForgery
    protected lateinit var viewKey: String

    @StringForgery
    protected lateinit var viewName: String

    @StringForgery
    protected lateinit var previousViewKey: String

    @StringForgery
    protected lateinit var previousViewName: String

    @StringForgery
    protected lateinit var resourceKey: String

    @StringForgery(regex = "https://[a-z]+/[a-z]+\\.com")
    protected lateinit var resourceUrl: String

    @StringForgery
    protected lateinit var lastInteractionName: String

    @IntForgery(200, 599)
    protected var resourceStatus: Int = 0

    @LongForgery(0)
    protected var resourceSize: Long = 0L

    @Forgery
    protected lateinit var rumResourceMethod: RumResourceMethod

    @Forgery
    protected lateinit var rumResourceKind: RumResourceKind

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)
    }

    protected open fun configureRumBuilder(builder: RumConfiguration.Builder) {}

    protected fun configurationBuilder(): RumConfiguration.Builder {
        val builder = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
        configureRumBuilder(builder)
        return builder
    }

    // region Shared Scenario Helpers

    protected fun enableRum(configure: RumConfiguration.Builder.() -> Unit = {}): RumMonitor {
        val builder = configurationBuilder()
        builder.configure()
        Rum.enable(builder.build(), stubSdkCore)
        return GlobalRumMonitor.get(stubSdkCore)
    }

    protected fun Forge.aValidLastInteractionActionType(): RumActionType {
        return aValueFrom(RumActionType::class.java, exclude = listOf(RumActionType.CUSTOM, RumActionType.SCROLL))
    }

    // region TTNS Scenarios

    protected fun runTtnsResourceScenario(monitor: RumMonitor) {
        monitor.startView(viewKey, viewName)
        monitor.startResource(resourceKey, rumResourceMethod, resourceUrl)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopResource(resourceKey, resourceStatus, resourceSize, rumResourceKind)
        monitor.stopView(viewKey)
    }

    protected fun runTtnsResourceWithTimingsScenario(monitor: RumMonitor, forge: Forge) {
        monitor.startView(viewKey, viewName)
        monitor.startResource(resourceKey, rumResourceMethod, resourceUrl)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopResource(resourceKey, resourceStatus, resourceSize, rumResourceKind)
        monitor.addTiming(forge.anAlphabeticalString())
        stubSdkCore.advanceTimeBy(100)
        monitor.addTiming(forge.anAlphabeticalString())
        monitor.stopView(viewKey)
    }

    protected fun runTtnsResourceErrorScenario(
        monitor: RumMonitor,
        errorMessage: String,
        errorSource: RumErrorSource,
        throwable: Throwable
    ) {
        monitor.startView(viewKey, viewName)
        monitor.startResource(resourceKey, rumResourceMethod, resourceUrl)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopResourceWithError(resourceKey, resourceStatus, errorMessage, errorSource, throwable)
        monitor.stopView(viewKey)
    }

    protected fun runTtnsDelayedResourceScenario(monitor: RumMonitor, delayMs: Long) {
        monitor.startView(viewKey, viewName)
        stubSdkCore.advanceTimeBy(delayMs)
        monitor.startResource(resourceKey, rumResourceMethod, resourceUrl)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopResource(resourceKey, resourceStatus, resourceSize, rumResourceKind)
        monitor.stopView(viewKey)
    }

    // endregion

    // region ITNV Scenarios

    protected fun runSuccessfulItnvTestScenario(monitor: RumMonitor, rumActionType: RumActionType): Long {
        monitor.startView(previousViewKey, previousViewName)
        monitor.startAction(rumActionType, lastInteractionName)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopAction(rumActionType, lastInteractionName)
        monitor.stopView(previousViewKey)
        stubSdkCore.advanceTimeBy(100)
        monitor.startView(viewKey, viewName)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopView(viewKey)
        return TimeUnit.MILLISECONDS.toNanos(100)
    }

    protected fun runUnsuccessfulItnvTestScenario(monitor: RumMonitor, rumActionType: RumActionType) {
        monitor.startView(previousViewKey, previousViewName)
        monitor.startAction(rumActionType, lastInteractionName)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopAction(rumActionType, lastInteractionName)
        monitor.stopView(previousViewKey)
        monitor.startView(viewKey, viewName)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopView(viewKey)
    }

    protected fun runItnvThresholdSurpassedScenario(
        monitor: RumMonitor,
        rumActionType: RumActionType,
        delayMs: Long
    ) {
        monitor.startView(previousViewKey, previousViewName)
        monitor.startAction(rumActionType, lastInteractionName)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopAction(rumActionType, lastInteractionName)
        monitor.stopView(previousViewKey)
        stubSdkCore.advanceTimeBy(delayMs)
        monitor.startView(viewKey, viewName)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopView(viewKey)
    }

    // endregion

    // endregion

    companion object {
        val TTNS_METRIC_OFFSET_IN_NANOSECONDS: Offset<Long> = Offset.offset(TimeUnit.MILLISECONDS.toNanos(10))
        val ITNV_METRIC_OFFSET_IN_NANOSECONDS: Offset<Long> = Offset.offset(TimeUnit.MILLISECONDS.toNanos(10))
    }
}
