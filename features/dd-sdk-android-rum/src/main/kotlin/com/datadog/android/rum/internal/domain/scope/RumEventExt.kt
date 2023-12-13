/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("TooManyFunctions")

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DeviceType
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.internal.RumErrorSourceType
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import java.util.Locale

internal fun RumResourceMethod.toResourceMethod(): ResourceEvent.Method {
    return when (this) {
        RumResourceMethod.GET -> ResourceEvent.Method.GET
        RumResourceMethod.POST -> ResourceEvent.Method.POST
        RumResourceMethod.HEAD -> ResourceEvent.Method.HEAD
        RumResourceMethod.PUT -> ResourceEvent.Method.PUT
        RumResourceMethod.DELETE -> ResourceEvent.Method.DELETE
        RumResourceMethod.PATCH -> ResourceEvent.Method.PATCH
    }
}

internal fun RumResourceMethod.toErrorMethod(): ErrorEvent.Method {
    return when (this) {
        RumResourceMethod.GET -> ErrorEvent.Method.GET
        RumResourceMethod.POST -> ErrorEvent.Method.POST
        RumResourceMethod.HEAD -> ErrorEvent.Method.HEAD
        RumResourceMethod.PUT -> ErrorEvent.Method.PUT
        RumResourceMethod.DELETE -> ErrorEvent.Method.DELETE
        RumResourceMethod.PATCH -> ErrorEvent.Method.PATCH
    }
}

internal fun String.toOperationType(internalLogger: InternalLogger): ResourceEvent.OperationType? {
    return try {
        ResourceEvent.OperationType.valueOf(this.uppercase(Locale.US))
    } catch (e: IllegalArgumentException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            { "Unable to convert [$this] to a valid graphql operation type" },
            e
        )
        null
    }
}

internal fun RumResourceKind.toSchemaType(): ResourceEvent.ResourceType {
    return when (this) {
        RumResourceKind.BEACON -> ResourceEvent.ResourceType.BEACON
        RumResourceKind.FETCH -> ResourceEvent.ResourceType.FETCH
        RumResourceKind.XHR -> ResourceEvent.ResourceType.XHR
        RumResourceKind.DOCUMENT -> ResourceEvent.ResourceType.DOCUMENT
        RumResourceKind.IMAGE -> ResourceEvent.ResourceType.IMAGE
        RumResourceKind.JS -> ResourceEvent.ResourceType.JS
        RumResourceKind.FONT -> ResourceEvent.ResourceType.FONT
        RumResourceKind.CSS -> ResourceEvent.ResourceType.CSS
        RumResourceKind.MEDIA -> ResourceEvent.ResourceType.MEDIA
        RumResourceKind.NATIVE -> ResourceEvent.ResourceType.NATIVE
        RumResourceKind.UNKNOWN,
        RumResourceKind.OTHER -> ResourceEvent.ResourceType.OTHER
    }
}

internal fun RumErrorSource.toSchemaSource(): ErrorEvent.ErrorSource {
    return when (this) {
        RumErrorSource.NETWORK -> ErrorEvent.ErrorSource.NETWORK
        RumErrorSource.SOURCE -> ErrorEvent.ErrorSource.SOURCE
        RumErrorSource.CONSOLE -> ErrorEvent.ErrorSource.CONSOLE
        RumErrorSource.LOGGER -> ErrorEvent.ErrorSource.LOGGER
        RumErrorSource.AGENT -> ErrorEvent.ErrorSource.AGENT
        RumErrorSource.WEBVIEW -> ErrorEvent.ErrorSource.WEBVIEW
    }
}

internal fun RumErrorSourceType.toSchemaSourceType(): ErrorEvent.SourceType {
    return when (this) {
        RumErrorSourceType.ANDROID -> ErrorEvent.SourceType.ANDROID
        RumErrorSourceType.BROWSER -> ErrorEvent.SourceType.BROWSER
        RumErrorSourceType.REACT_NATIVE -> ErrorEvent.SourceType.REACT_NATIVE
        RumErrorSourceType.FLUTTER -> ErrorEvent.SourceType.FLUTTER
    }
}

internal fun ResourceTiming.dns(): ResourceEvent.Dns? {
    return if (dnsStart > 0) {
        ResourceEvent.Dns(duration = dnsDuration, start = dnsStart)
    } else {
        null
    }
}

internal fun ResourceTiming.connect(): ResourceEvent.Connect? {
    return if (connectStart > 0) {
        ResourceEvent.Connect(duration = connectDuration, start = connectStart)
    } else {
        null
    }
}

internal fun ResourceTiming.ssl(): ResourceEvent.Ssl? {
    return if (sslStart > 0) {
        ResourceEvent.Ssl(duration = sslDuration, start = sslStart)
    } else {
        null
    }
}

internal fun ResourceTiming.firstByte(): ResourceEvent.FirstByte? {
    return if (firstByteStart >= 0 && firstByteDuration > 0) {
        ResourceEvent.FirstByte(duration = firstByteDuration, start = firstByteStart)
    } else {
        null
    }
}

internal fun ResourceTiming.download(): ResourceEvent.Download? {
    return if (downloadStart > 0) {
        ResourceEvent.Download(duration = downloadDuration, start = downloadStart)
    } else {
        null
    }
}

internal fun RumActionType.toSchemaType(): ActionEvent.ActionEventActionType {
    return when (this) {
        RumActionType.TAP -> ActionEvent.ActionEventActionType.TAP
        RumActionType.SCROLL -> ActionEvent.ActionEventActionType.SCROLL
        RumActionType.SWIPE -> ActionEvent.ActionEventActionType.SWIPE
        RumActionType.CLICK -> ActionEvent.ActionEventActionType.CLICK
        RumActionType.BACK -> ActionEvent.ActionEventActionType.BACK
        RumActionType.CUSTOM -> ActionEvent.ActionEventActionType.CUSTOM
    }
}

// region NetworkInfo conversion

internal fun NetworkInfo.toResourceConnectivity(): ResourceEvent.Connectivity {
    val status = if (isConnected()) {
        ResourceEvent.Status.CONNECTED
    } else {
        ResourceEvent.Status.NOT_CONNECTED
    }
    val interfaces = when (connectivity) {
        NetworkInfo.Connectivity.NETWORK_ETHERNET -> listOf(ResourceEvent.Interface.ETHERNET)
        NetworkInfo.Connectivity.NETWORK_WIFI -> listOf(ResourceEvent.Interface.WIFI)
        NetworkInfo.Connectivity.NETWORK_WIMAX -> listOf(ResourceEvent.Interface.WIMAX)
        NetworkInfo.Connectivity.NETWORK_BLUETOOTH -> listOf(ResourceEvent.Interface.BLUETOOTH)
        NetworkInfo.Connectivity.NETWORK_2G,
        NetworkInfo.Connectivity.NETWORK_3G,
        NetworkInfo.Connectivity.NETWORK_4G,
        NetworkInfo.Connectivity.NETWORK_5G,
        NetworkInfo.Connectivity.NETWORK_MOBILE_OTHER,
        NetworkInfo.Connectivity.NETWORK_CELLULAR -> listOf(ResourceEvent.Interface.CELLULAR)
        NetworkInfo.Connectivity.NETWORK_OTHER -> listOf(ResourceEvent.Interface.OTHER)
        NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED -> emptyList()
    }

    val cellular = if (cellularTechnology != null || carrierName != null) {
        ResourceEvent.Cellular(
            technology = cellularTechnology,
            carrierName = carrierName
        )
    } else {
        null
    }
    return ResourceEvent.Connectivity(
        status,
        interfaces,
        cellular
    )
}

internal fun NetworkInfo.toErrorConnectivity(): ErrorEvent.Connectivity {
    val status = if (isConnected()) {
        ErrorEvent.Status.CONNECTED
    } else {
        ErrorEvent.Status.NOT_CONNECTED
    }
    val interfaces = when (connectivity) {
        NetworkInfo.Connectivity.NETWORK_ETHERNET -> listOf(ErrorEvent.Interface.ETHERNET)
        NetworkInfo.Connectivity.NETWORK_WIFI -> listOf(ErrorEvent.Interface.WIFI)
        NetworkInfo.Connectivity.NETWORK_WIMAX -> listOf(ErrorEvent.Interface.WIMAX)
        NetworkInfo.Connectivity.NETWORK_BLUETOOTH -> listOf(ErrorEvent.Interface.BLUETOOTH)
        NetworkInfo.Connectivity.NETWORK_2G,
        NetworkInfo.Connectivity.NETWORK_3G,
        NetworkInfo.Connectivity.NETWORK_4G,
        NetworkInfo.Connectivity.NETWORK_5G,
        NetworkInfo.Connectivity.NETWORK_MOBILE_OTHER,
        NetworkInfo.Connectivity.NETWORK_CELLULAR -> listOf(ErrorEvent.Interface.CELLULAR)
        NetworkInfo.Connectivity.NETWORK_OTHER -> listOf(ErrorEvent.Interface.OTHER)
        NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED -> emptyList()
    }

    val cellular = if (cellularTechnology != null || carrierName != null) {
        ErrorEvent.Cellular(
            technology = cellularTechnology,
            carrierName = carrierName
        )
    } else {
        null
    }
    return ErrorEvent.Connectivity(
        status,
        interfaces,
        cellular
    )
}

internal fun NetworkInfo.toLongTaskConnectivity(): LongTaskEvent.Connectivity {
    val status = if (isConnected()) {
        LongTaskEvent.Status.CONNECTED
    } else {
        LongTaskEvent.Status.NOT_CONNECTED
    }
    val interfaces = when (connectivity) {
        NetworkInfo.Connectivity.NETWORK_ETHERNET -> listOf(LongTaskEvent.Interface.ETHERNET)
        NetworkInfo.Connectivity.NETWORK_WIFI -> listOf(LongTaskEvent.Interface.WIFI)
        NetworkInfo.Connectivity.NETWORK_WIMAX -> listOf(LongTaskEvent.Interface.WIMAX)
        NetworkInfo.Connectivity.NETWORK_BLUETOOTH -> listOf(LongTaskEvent.Interface.BLUETOOTH)
        NetworkInfo.Connectivity.NETWORK_2G,
        NetworkInfo.Connectivity.NETWORK_3G,
        NetworkInfo.Connectivity.NETWORK_4G,
        NetworkInfo.Connectivity.NETWORK_5G,
        NetworkInfo.Connectivity.NETWORK_MOBILE_OTHER,
        NetworkInfo.Connectivity.NETWORK_CELLULAR -> listOf(LongTaskEvent.Interface.CELLULAR)
        NetworkInfo.Connectivity.NETWORK_OTHER -> listOf(LongTaskEvent.Interface.OTHER)
        NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED -> emptyList()
    }

    val cellular = if (cellularTechnology != null || carrierName != null) {
        LongTaskEvent.Cellular(
            technology = cellularTechnology,
            carrierName = carrierName
        )
    } else {
        null
    }
    return LongTaskEvent.Connectivity(
        status,
        interfaces,
        cellular
    )
}

internal fun NetworkInfo.toViewConnectivity(): ViewEvent.Connectivity {
    val status = if (isConnected()) {
        ViewEvent.Status.CONNECTED
    } else {
        ViewEvent.Status.NOT_CONNECTED
    }
    val interfaces = when (connectivity) {
        NetworkInfo.Connectivity.NETWORK_ETHERNET -> listOf(ViewEvent.Interface.ETHERNET)
        NetworkInfo.Connectivity.NETWORK_WIFI -> listOf(ViewEvent.Interface.WIFI)
        NetworkInfo.Connectivity.NETWORK_WIMAX -> listOf(ViewEvent.Interface.WIMAX)
        NetworkInfo.Connectivity.NETWORK_BLUETOOTH -> listOf(ViewEvent.Interface.BLUETOOTH)
        NetworkInfo.Connectivity.NETWORK_2G,
        NetworkInfo.Connectivity.NETWORK_3G,
        NetworkInfo.Connectivity.NETWORK_4G,
        NetworkInfo.Connectivity.NETWORK_5G,
        NetworkInfo.Connectivity.NETWORK_MOBILE_OTHER,
        NetworkInfo.Connectivity.NETWORK_CELLULAR -> listOf(ViewEvent.Interface.CELLULAR)
        NetworkInfo.Connectivity.NETWORK_OTHER -> listOf(ViewEvent.Interface.OTHER)
        NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED -> emptyList()
    }

    val cellular = if (cellularTechnology != null || carrierName != null) {
        ViewEvent.Cellular(
            technology = cellularTechnology,
            carrierName = carrierName
        )
    } else {
        null
    }
    return ViewEvent.Connectivity(
        status,
        interfaces,
        cellular
    )
}

internal fun NetworkInfo.toActionConnectivity(): ActionEvent.Connectivity {
    val status = if (isConnected()) {
        ActionEvent.Status.CONNECTED
    } else {
        ActionEvent.Status.NOT_CONNECTED
    }
    val interfaces = when (connectivity) {
        NetworkInfo.Connectivity.NETWORK_ETHERNET -> listOf(ActionEvent.Interface.ETHERNET)
        NetworkInfo.Connectivity.NETWORK_WIFI -> listOf(ActionEvent.Interface.WIFI)
        NetworkInfo.Connectivity.NETWORK_WIMAX -> listOf(ActionEvent.Interface.WIMAX)
        NetworkInfo.Connectivity.NETWORK_BLUETOOTH -> listOf(ActionEvent.Interface.BLUETOOTH)
        NetworkInfo.Connectivity.NETWORK_2G,
        NetworkInfo.Connectivity.NETWORK_3G,
        NetworkInfo.Connectivity.NETWORK_4G,
        NetworkInfo.Connectivity.NETWORK_5G,
        NetworkInfo.Connectivity.NETWORK_MOBILE_OTHER,
        NetworkInfo.Connectivity.NETWORK_CELLULAR -> listOf(ActionEvent.Interface.CELLULAR)
        NetworkInfo.Connectivity.NETWORK_OTHER -> listOf(ActionEvent.Interface.OTHER)
        NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED -> emptyList()
    }

    val cellular = if (cellularTechnology != null || carrierName != null) {
        ActionEvent.Cellular(
            technology = cellularTechnology,
            carrierName = carrierName
        )
    } else {
        null
    }
    return ActionEvent.Connectivity(
        status,
        interfaces,
        cellular
    )
}

internal fun NetworkInfo.isConnected(): Boolean {
    return connectivity != NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED
}

// endregion

// region DeviceType conversion

internal fun DeviceType.toViewSchemaType(): ViewEvent.DeviceType {
    return when (this) {
        DeviceType.MOBILE -> ViewEvent.DeviceType.MOBILE
        DeviceType.TABLET -> ViewEvent.DeviceType.TABLET
        DeviceType.TV -> ViewEvent.DeviceType.TV
        DeviceType.DESKTOP -> ViewEvent.DeviceType.DESKTOP
        else -> ViewEvent.DeviceType.OTHER
    }
}

internal fun DeviceType.toActionSchemaType(): ActionEvent.DeviceType {
    return when (this) {
        DeviceType.MOBILE -> ActionEvent.DeviceType.MOBILE
        DeviceType.TABLET -> ActionEvent.DeviceType.TABLET
        DeviceType.TV -> ActionEvent.DeviceType.TV
        DeviceType.DESKTOP -> ActionEvent.DeviceType.DESKTOP
        else -> ActionEvent.DeviceType.OTHER
    }
}

internal fun DeviceType.toLongTaskSchemaType(): LongTaskEvent.DeviceType {
    return when (this) {
        DeviceType.MOBILE -> LongTaskEvent.DeviceType.MOBILE
        DeviceType.TABLET -> LongTaskEvent.DeviceType.TABLET
        DeviceType.TV -> LongTaskEvent.DeviceType.TV
        DeviceType.DESKTOP -> LongTaskEvent.DeviceType.DESKTOP
        else -> LongTaskEvent.DeviceType.OTHER
    }
}

internal fun DeviceType.toResourceSchemaType(): ResourceEvent.DeviceType {
    return when (this) {
        DeviceType.MOBILE -> ResourceEvent.DeviceType.MOBILE
        DeviceType.TABLET -> ResourceEvent.DeviceType.TABLET
        DeviceType.TV -> ResourceEvent.DeviceType.TV
        DeviceType.DESKTOP -> ResourceEvent.DeviceType.DESKTOP
        else -> ResourceEvent.DeviceType.OTHER
    }
}

internal fun DeviceType.toErrorSchemaType(): ErrorEvent.DeviceType {
    return when (this) {
        DeviceType.MOBILE -> ErrorEvent.DeviceType.MOBILE
        DeviceType.TABLET -> ErrorEvent.DeviceType.TABLET
        DeviceType.TV -> ErrorEvent.DeviceType.TV
        DeviceType.DESKTOP -> ErrorEvent.DeviceType.DESKTOP
        else -> ErrorEvent.DeviceType.OTHER
    }
}

// endregion

// region Source

internal fun ViewEvent.ViewEventSource.Companion.tryFromSource(
    source: String,
    internalLogger: InternalLogger
): ViewEvent.ViewEventSource? {
    return try {
        fromJson(source)
    } catch (e: NoSuchElementException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            { UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, source) },
            e
        )
        null
    }
}

internal fun LongTaskEvent.LongTaskEventSource.Companion.tryFromSource(
    source: String,
    internalLogger: InternalLogger
): LongTaskEvent.LongTaskEventSource? {
    return try {
        fromJson(source)
    } catch (e: NoSuchElementException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            { UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, source) },
            e
        )
        null
    }
}

internal fun ErrorEvent.ErrorEventSource.Companion.tryFromSource(
    source: String,
    internalLogger: InternalLogger
): ErrorEvent.ErrorEventSource? {
    return try {
        fromJson(source)
    } catch (e: NoSuchElementException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            { UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, source) },
            e
        )
        null
    }
}

internal fun ActionEvent.ActionEventSource.Companion.tryFromSource(
    source: String,
    internalLogger: InternalLogger
): ActionEvent.ActionEventSource? {
    return try {
        fromJson(source)
    } catch (e: NoSuchElementException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            { UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, source) },
            e
        )
        null
    }
}

internal fun ResourceEvent.ResourceEventSource.Companion.tryFromSource(
    source: String,
    internalLogger: InternalLogger
): ResourceEvent.ResourceEventSource? {
    return try {
        fromJson(source)
    } catch (e: NoSuchElementException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            { UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, source) },
            e
        )
        null
    }
}

internal const val UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT = "You are using an unknown " +
    "source %s for your events"

// endregion
