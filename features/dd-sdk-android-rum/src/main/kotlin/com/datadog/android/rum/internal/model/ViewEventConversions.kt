/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("TooManyFunctions")

package com.datadog.android.rum.internal.model

import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.model.ViewUpdateEvent

// region Type conversions

internal fun ViewEvent.Usr.toViewUpdate() = ViewUpdateEvent.Usr(
    id = id,
    name = name,
    email = email,
    anonymousId = anonymousId,
    additionalProperties = additionalProperties
)

internal fun ViewEvent.Account.toViewUpdate() = ViewUpdateEvent.Account(
    id = id,
    name = name,
    additionalProperties = additionalProperties
)

internal fun ViewEvent.Connectivity.toViewUpdate() = ViewUpdateEvent.Connectivity(
    status = status.toViewUpdate(),
    interfaces = interfaces?.map { it.toViewUpdate() },
    effectiveType = effectiveType?.toViewUpdate(),
    cellular = cellular?.let { ViewUpdateEvent.Cellular(it.technology, it.carrierName) }
)

internal fun ViewEvent.Display.toViewUpdate() = ViewUpdateEvent.Display(
    scroll = scroll?.let {
        ViewUpdateEvent.Scroll(it.maxDepth, it.maxDepthScrollTop, it.maxScrollHeight, it.maxScrollHeightTime)
    },
    viewport = viewport?.let { ViewUpdateEvent.Viewport(it.width, it.height) }
)

internal fun ViewEvent.Os.toViewUpdate() = ViewUpdateEvent.Os(
    name = name,
    version = version,
    build = build,
    versionMajor = versionMajor
)

internal fun ViewEvent.Device.toViewUpdate() = ViewUpdateEvent.Device(
    type = type?.toViewUpdate(),
    name = name,
    model = model,
    brand = brand,
    architecture = architecture,
    locale = locale,
    locales = locales,
    timeZone = timeZone,
    batteryLevel = batteryLevel,
    powerSavingMode = powerSavingMode,
    brightnessLevel = brightnessLevel,
    logicalCpuCount = logicalCpuCount,
    totalRam = totalRam,
    isLowRam = isLowRam
)

internal fun ViewEvent.Container.toViewUpdate() = ViewUpdateEvent.Container(
    view = ViewUpdateEvent.ContainerView(view.id),
    source = source.toViewUpdate()
)

internal fun ViewEvent.FlutterBuildTime.toViewUpdate() = ViewUpdateEvent.FlutterBuildTime(
    min = min,
    max = max,
    average = average,
    metricMax = metricMax
)

internal fun ViewEvent.PerformanceCls.toViewUpdate() = ViewUpdateEvent.Cls(
    score = score,
    timestamp = timestamp,
    targetSelector = targetSelector,
    previousRect = previousRect?.let { ViewUpdateEvent.PreviousRect(it.x, it.y, it.width, it.height) },
    currentRect = currentRect?.let { ViewUpdateEvent.PreviousRect(it.x, it.y, it.width, it.height) }
)

internal fun ViewEvent.Inp.toViewUpdate() = ViewUpdateEvent.Inp(
    duration = duration,
    timestamp = timestamp,
    targetSelector = targetSelector
)

internal fun ViewEvent.Lcp.toViewUpdate() = ViewUpdateEvent.Lcp(
    timestamp = timestamp,
    targetSelector = targetSelector,
    resourceUrl = resourceUrl
)

// endregion

// region Enum conversions

internal fun ViewEvent.ViewEventSource.toViewUpdate() = when (this) {
    ViewEvent.ViewEventSource.ANDROID -> ViewUpdateEvent.ViewUpdateEventSource.ANDROID
    ViewEvent.ViewEventSource.IOS -> ViewUpdateEvent.ViewUpdateEventSource.IOS
    ViewEvent.ViewEventSource.BROWSER -> ViewUpdateEvent.ViewUpdateEventSource.BROWSER
    ViewEvent.ViewEventSource.FLUTTER -> ViewUpdateEvent.ViewUpdateEventSource.FLUTTER
    ViewEvent.ViewEventSource.REACT_NATIVE -> ViewUpdateEvent.ViewUpdateEventSource.REACT_NATIVE
    ViewEvent.ViewEventSource.ROKU -> ViewUpdateEvent.ViewUpdateEventSource.ROKU
    ViewEvent.ViewEventSource.UNITY -> ViewUpdateEvent.ViewUpdateEventSource.UNITY
    ViewEvent.ViewEventSource.KOTLIN_MULTIPLATFORM -> ViewUpdateEvent.ViewUpdateEventSource.KOTLIN_MULTIPLATFORM
}

internal fun ViewEvent.ViewEventSessionType.toViewUpdate() = when (this) {
    ViewEvent.ViewEventSessionType.USER -> ViewUpdateEvent.ViewUpdateEventSessionType.USER
    ViewEvent.ViewEventSessionType.SYNTHETICS -> ViewUpdateEvent.ViewUpdateEventSessionType.SYNTHETICS
    ViewEvent.ViewEventSessionType.CI_TEST -> ViewUpdateEvent.ViewUpdateEventSessionType.CI_TEST
}

internal fun ViewEvent.LoadingType.toViewUpdate() = when (this) {
    ViewEvent.LoadingType.INITIAL_LOAD -> ViewUpdateEvent.LoadingType.INITIAL_LOAD
    ViewEvent.LoadingType.ROUTE_CHANGE -> ViewUpdateEvent.LoadingType.ROUTE_CHANGE
    ViewEvent.LoadingType.ACTIVITY_DISPLAY -> ViewUpdateEvent.LoadingType.ACTIVITY_DISPLAY
    ViewEvent.LoadingType.ACTIVITY_REDISPLAY -> ViewUpdateEvent.LoadingType.ACTIVITY_REDISPLAY
    ViewEvent.LoadingType.FRAGMENT_DISPLAY -> ViewUpdateEvent.LoadingType.FRAGMENT_DISPLAY
    ViewEvent.LoadingType.FRAGMENT_REDISPLAY -> ViewUpdateEvent.LoadingType.FRAGMENT_REDISPLAY
    ViewEvent.LoadingType.VIEW_CONTROLLER_DISPLAY -> ViewUpdateEvent.LoadingType.VIEW_CONTROLLER_DISPLAY
    ViewEvent.LoadingType.VIEW_CONTROLLER_REDISPLAY -> ViewUpdateEvent.LoadingType.VIEW_CONTROLLER_REDISPLAY
}

internal fun ViewEvent.ConnectivityStatus.toViewUpdate() = when (this) {
    ViewEvent.ConnectivityStatus.CONNECTED -> ViewUpdateEvent.Status.CONNECTED
    ViewEvent.ConnectivityStatus.NOT_CONNECTED -> ViewUpdateEvent.Status.NOT_CONNECTED
    ViewEvent.ConnectivityStatus.MAYBE -> ViewUpdateEvent.Status.MAYBE
}

internal fun ViewEvent.Interface.toViewUpdate() = when (this) {
    ViewEvent.Interface.BLUETOOTH -> ViewUpdateEvent.Interface.BLUETOOTH
    ViewEvent.Interface.CELLULAR -> ViewUpdateEvent.Interface.CELLULAR
    ViewEvent.Interface.ETHERNET -> ViewUpdateEvent.Interface.ETHERNET
    ViewEvent.Interface.WIFI -> ViewUpdateEvent.Interface.WIFI
    ViewEvent.Interface.WIMAX -> ViewUpdateEvent.Interface.WIMAX
    ViewEvent.Interface.MIXED -> ViewUpdateEvent.Interface.MIXED
    ViewEvent.Interface.OTHER -> ViewUpdateEvent.Interface.OTHER
    ViewEvent.Interface.UNKNOWN -> ViewUpdateEvent.Interface.UNKNOWN
    ViewEvent.Interface.NONE -> ViewUpdateEvent.Interface.NONE
}

internal fun ViewEvent.EffectiveType.toViewUpdate() = when (this) {
    ViewEvent.EffectiveType.SLOW_2G -> ViewUpdateEvent.EffectiveType.SLOW_2G
    ViewEvent.EffectiveType.`2G` -> ViewUpdateEvent.EffectiveType.`2G`
    ViewEvent.EffectiveType.`3G` -> ViewUpdateEvent.EffectiveType.`3G`
    ViewEvent.EffectiveType.`4G` -> ViewUpdateEvent.EffectiveType.`4G`
}

internal fun ViewEvent.DeviceType.toViewUpdate() = when (this) {
    ViewEvent.DeviceType.MOBILE -> ViewUpdateEvent.DeviceType.MOBILE
    ViewEvent.DeviceType.DESKTOP -> ViewUpdateEvent.DeviceType.DESKTOP
    ViewEvent.DeviceType.TABLET -> ViewUpdateEvent.DeviceType.TABLET
    ViewEvent.DeviceType.TV -> ViewUpdateEvent.DeviceType.TV
    ViewEvent.DeviceType.GAMING_CONSOLE -> ViewUpdateEvent.DeviceType.GAMING_CONSOLE
    ViewEvent.DeviceType.BOT -> ViewUpdateEvent.DeviceType.BOT
    ViewEvent.DeviceType.OTHER -> ViewUpdateEvent.DeviceType.OTHER
}

internal fun ViewEvent.ReplayLevel.toViewUpdate() = when (this) {
    ViewEvent.ReplayLevel.ALLOW -> ViewUpdateEvent.ReplayLevel.ALLOW
    ViewEvent.ReplayLevel.MASK -> ViewUpdateEvent.ReplayLevel.MASK
    ViewEvent.ReplayLevel.MASK_USER_INPUT -> ViewUpdateEvent.ReplayLevel.MASK_USER_INPUT
}

internal fun ViewEvent.Plan.toViewUpdate() = when (this) {
    ViewEvent.Plan.PLAN_1 -> ViewUpdateEvent.Plan.PLAN_1
    ViewEvent.Plan.PLAN_2 -> ViewUpdateEvent.Plan.PLAN_2
}

internal fun ViewEvent.SessionPrecondition.toViewUpdate() = when (this) {
    ViewEvent.SessionPrecondition.USER_APP_LAUNCH -> ViewUpdateEvent.SessionPrecondition.USER_APP_LAUNCH
    ViewEvent.SessionPrecondition.INACTIVITY_TIMEOUT -> ViewUpdateEvent.SessionPrecondition.INACTIVITY_TIMEOUT
    ViewEvent.SessionPrecondition.MAX_DURATION -> ViewUpdateEvent.SessionPrecondition.MAX_DURATION
    ViewEvent.SessionPrecondition.BACKGROUND_LAUNCH -> ViewUpdateEvent.SessionPrecondition.BACKGROUND_LAUNCH
    ViewEvent.SessionPrecondition.PREWARM -> ViewUpdateEvent.SessionPrecondition.PREWARM
    ViewEvent.SessionPrecondition.FROM_NON_INTERACTIVE_SESSION ->
        ViewUpdateEvent.SessionPrecondition.FROM_NON_INTERACTIVE_SESSION
    ViewEvent.SessionPrecondition.EXPLICIT_STOP -> ViewUpdateEvent.SessionPrecondition.EXPLICIT_STOP
}

// endregion
