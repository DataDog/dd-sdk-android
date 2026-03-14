/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration

import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.internal.monitor.AdvancedNetworkRumMonitor
import com.datadog.android.rum.resource.ResourceId

internal fun StubSDKCore.runStartView(viewKey: String, viewName: String) {
    GlobalRumMonitor.get(this).startView(viewKey, viewName)
}

internal fun StubSDKCore.runStartViewAndStop(viewKey: String, viewName: String) {
    val monitor = GlobalRumMonitor.get(this)
    monitor.startView(viewKey, viewName)
    monitor.stopView(viewKey)
}

internal fun StubSDKCore.runStartViewAndAddFeatureFlag(
    viewKey: String,
    viewName: String,
    ffKey: String,
    ffValue: String
) {
    val monitor = GlobalRumMonitor.get(this)
    monitor.startView(viewKey, viewName)
    monitor.addFeatureFlagEvaluation(ffKey, ffValue)
}

internal fun StubSDKCore.runStartViewWithActionAndStop(
    viewKey: String,
    viewName: String,
    actionName: String
) {
    val monitor = GlobalRumMonitor.get(this)
    monitor.startView(viewKey, viewName)
    monitor.addAction(RumActionType.CUSTOM, actionName)
    advanceTimeBy(100)
    monitor.stopView(viewKey)
}

internal fun StubSDKCore.runStartViewWithErrorAndStop(
    viewKey: String,
    viewName: String,
    errorMessage: String,
    errorSource: RumErrorSource,
    exception: Throwable
) {
    val monitor = GlobalRumMonitor.get(this)
    monitor.startView(viewKey, viewName)
    monitor.addError(errorMessage, errorSource, exception)
    monitor.stopView(viewKey)
}

internal fun StubSDKCore.runStartViewWithResourceAndStop(
    viewKey: String,
    viewName: String,
    resourceKey: String,
    resourceUrl: String,
    resourceStatus: Int,
    resourceSize: Long
) {
    val monitor = GlobalRumMonitor.get(this)
    monitor.startView(viewKey, viewName)
    monitor.startResource(resourceKey, RumResourceMethod.GET, resourceUrl)
    advanceTimeBy(100)
    monitor.stopResource(resourceKey, resourceStatus, resourceSize, RumResourceKind.NATIVE)
    monitor.stopView(viewKey)
}

internal fun StubSDKCore.runStartViewWithResourceIdAndStop(
    viewKey: String,
    viewName: String,
    resourceId: ResourceId,
    resourceUrl: String,
    resourceStatus: Int,
    resourceSize: Long
) {
    val monitor = GlobalRumMonitor.get(this) as AdvancedNetworkRumMonitor
    monitor.startView(viewKey, viewName)
    monitor.startResource(resourceId, RumResourceMethod.GET, resourceUrl)
    advanceTimeBy(100)
    monitor.stopResource(resourceId, resourceStatus, resourceSize, RumResourceKind.NATIVE)
    monitor.stopView(viewKey)
}

@OptIn(ExperimentalRumApi::class)
internal fun StubSDKCore.runStartViewAndAddLoadingTime(
    viewKey: String,
    viewName: String,
    overwrite: Boolean
) {
    val monitor = GlobalRumMonitor.get(this)
    monitor.startView(viewKey, viewName)
    advanceTimeBy(100)
    monitor.addViewLoadingTime(overwrite)
}

@OptIn(ExperimentalRumApi::class)
internal fun StubSDKCore.runStartViewStopAndAddLoadingTime(
    viewKey: String,
    viewName: String,
    overwrite: Boolean
) {
    val monitor = GlobalRumMonitor.get(this)
    monitor.startView(viewKey, viewName)
    monitor.stopView(viewKey)
    monitor.addViewLoadingTime(overwrite)
}

@OptIn(ExperimentalRumApi::class)
internal fun StubSDKCore.runStartViewAndOverwriteLoadingTime(
    viewKey: String,
    viewName: String,
    firstOverwrite: Boolean
) {
    val monitor = GlobalRumMonitor.get(this)
    monitor.startView(viewKey, viewName)
    advanceTimeBy(50)
    monitor.addViewLoadingTime(firstOverwrite)
    advanceTimeBy(50)
    monitor.addViewLoadingTime(true)
}

@OptIn(ExperimentalRumApi::class)
internal fun StubSDKCore.runStartViewAndNoOverwriteLoadingTime(
    viewKey: String,
    viewName: String,
    firstOverwrite: Boolean
) {
    val monitor = GlobalRumMonitor.get(this)
    monitor.startView(viewKey, viewName)
    advanceTimeBy(50)
    monitor.addViewLoadingTime(firstOverwrite)
    advanceTimeBy(50)
    monitor.addViewLoadingTime(false)
}

internal fun StubSDKCore.runSetUserInfoAndStartView(
    viewKey: String,
    viewName: String,
    userId: String,
    userName: String,
    userEmail: String,
    additionalAttributes: Map<String, String>
) {
    setUserInfo(userId, userName, userEmail, additionalAttributes)
    GlobalRumMonitor.get(this).startView(viewKey, viewName)
}

internal fun StubSDKCore.runSetAccountInfoAndStartView(
    viewKey: String,
    viewName: String,
    accountId: String,
    accountName: String,
    extraInfo: Map<String, String>
) {
    setAccountInfo(accountId, accountName, extraInfo)
    GlobalRumMonitor.get(this).startView(viewKey, viewName)
}
