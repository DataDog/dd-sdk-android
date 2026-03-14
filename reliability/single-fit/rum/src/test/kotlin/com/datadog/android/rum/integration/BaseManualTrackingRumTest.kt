/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration

import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.internal.monitor.AdvancedNetworkRumMonitor
import com.datadog.android.rum.resource.ResourceId
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import org.junit.jupiter.api.BeforeEach

abstract class BaseManualTrackingRumTest {

    protected lateinit var stubSdkCore: StubSDKCore

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
    }

    // region Shared Scenarios

    protected fun runStartView(viewKey: String, viewName: String) {
        GlobalRumMonitor.get(stubSdkCore).startView(viewKey, viewName)
    }

    protected fun runStartViewAndStop(viewKey: String, viewName: String) {
        val monitor = GlobalRumMonitor.get(stubSdkCore)
        monitor.startView(viewKey, viewName)
        monitor.stopView(viewKey)
    }

    protected fun runStartViewAndAddFeatureFlag(
        viewKey: String,
        viewName: String,
        ffKey: String,
        ffValue: String
    ) {
        val monitor = GlobalRumMonitor.get(stubSdkCore)
        monitor.startView(viewKey, viewName)
        monitor.addFeatureFlagEvaluation(ffKey, ffValue)
    }

    protected fun runStartViewWithActionAndStop(
        viewKey: String,
        viewName: String,
        actionName: String
    ) {
        val monitor = GlobalRumMonitor.get(stubSdkCore)
        monitor.startView(viewKey, viewName)
        monitor.addAction(RumActionType.CUSTOM, actionName)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopView(viewKey)
    }

    protected fun runStartViewWithErrorAndStop(
        viewKey: String,
        viewName: String,
        errorMessage: String,
        errorSource: RumErrorSource,
        exception: Throwable
    ) {
        val monitor = GlobalRumMonitor.get(stubSdkCore)
        monitor.startView(viewKey, viewName)
        monitor.addError(errorMessage, errorSource, exception)
        monitor.stopView(viewKey)
    }

    protected fun runStartViewWithResourceAndStop(
        viewKey: String,
        viewName: String,
        resourceKey: String,
        resourceUrl: String,
        resourceStatus: Int,
        resourceSize: Long
    ) {
        val monitor = GlobalRumMonitor.get(stubSdkCore)
        monitor.startView(viewKey, viewName)
        monitor.startResource(resourceKey, RumResourceMethod.GET, resourceUrl)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopResource(resourceKey, resourceStatus, resourceSize, RumResourceKind.NATIVE)
        monitor.stopView(viewKey)
    }

    protected fun runStartViewWithResourceIdAndStop(
        viewKey: String,
        viewName: String,
        resourceId: ResourceId,
        resourceUrl: String,
        resourceStatus: Int,
        resourceSize: Long
    ) {
        val monitor = GlobalRumMonitor.get(stubSdkCore) as AdvancedNetworkRumMonitor
        monitor.startView(viewKey, viewName)
        monitor.startResource(resourceId, RumResourceMethod.GET, resourceUrl)
        stubSdkCore.advanceTimeBy(100)
        monitor.stopResource(resourceId, resourceStatus, resourceSize, RumResourceKind.NATIVE)
        monitor.stopView(viewKey)
    }

    @OptIn(ExperimentalRumApi::class)
    protected fun runStartViewAndAddLoadingTime(
        viewKey: String,
        viewName: String,
        overwrite: Boolean
    ) {
        val monitor = GlobalRumMonitor.get(stubSdkCore)
        monitor.startView(viewKey, viewName)
        stubSdkCore.advanceTimeBy(100)
        monitor.addViewLoadingTime(overwrite)
    }

    @OptIn(ExperimentalRumApi::class)
    protected fun runStartViewStopAndAddLoadingTime(
        viewKey: String,
        viewName: String,
        overwrite: Boolean
    ) {
        val monitor = GlobalRumMonitor.get(stubSdkCore)
        monitor.startView(viewKey, viewName)
        monitor.stopView(viewKey)
        monitor.addViewLoadingTime(overwrite)
    }

    @OptIn(ExperimentalRumApi::class)
    protected fun runStartViewAndOverwriteLoadingTime(
        viewKey: String,
        viewName: String,
        firstOverwrite: Boolean
    ) {
        val monitor = GlobalRumMonitor.get(stubSdkCore)
        monitor.startView(viewKey, viewName)
        stubSdkCore.advanceTimeBy(50)
        monitor.addViewLoadingTime(firstOverwrite)
        stubSdkCore.advanceTimeBy(50)
        monitor.addViewLoadingTime(true)
    }

    @OptIn(ExperimentalRumApi::class)
    protected fun runStartViewAndNoOverwriteLoadingTime(
        viewKey: String,
        viewName: String,
        firstOverwrite: Boolean
    ) {
        val monitor = GlobalRumMonitor.get(stubSdkCore)
        monitor.startView(viewKey, viewName)
        stubSdkCore.advanceTimeBy(50)
        monitor.addViewLoadingTime(firstOverwrite)
        stubSdkCore.advanceTimeBy(50)
        monitor.addViewLoadingTime(false)
    }

    protected fun runSetUserInfoAndStartView(
        viewKey: String,
        viewName: String,
        userId: String,
        userName: String,
        userEmail: String,
        additionalAttributes: Map<String, String>
    ) {
        stubSdkCore.setUserInfo(userId, userName, userEmail, additionalAttributes)
        GlobalRumMonitor.get(stubSdkCore).startView(viewKey, viewName)
    }

    protected fun runSetAccountInfoAndStartView(
        viewKey: String,
        viewName: String,
        accountId: String,
        accountName: String,
        extraInfo: Map<String, String>
    ) {
        stubSdkCore.setAccountInfo(accountId, accountName, extraInfo)
        GlobalRumMonitor.get(stubSdkCore).startView(viewKey, viewName)
    }

    // endregion
}
