/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tests.elmyr

import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.MutableHttpRequestInfo
import fr.xgouchet.elmyr.Case
import fr.xgouchet.elmyr.Forge
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.util.Locale
import java.util.TimeZone

const val URL_FORGERY_PATTERN = "http(s?)://[a-z]+\\.com/[a-z]+"

fun Forge.exhaustiveAttributes(
    excludedKeys: Set<String> = emptySet()
): MutableMap<String, Any?> {
    return listOf(
        aBool(),
        anInt(),
        aLong(),
        aFloat(),
        aDouble(),
        anAsciiString(),
        getForgery<Date>(),
        getForgery<Locale>(),
        getForgery<TimeZone>(),
        getForgery<File>(),
        getForgery<JSONObject>(),
        getForgery<JSONArray>(),
        aList { anAlphabeticalString() },
        aList { aDouble() },
        null
    )
        .associateBy { anAlphaNumericalString() }
        .filter { it.key !in excludedKeys }
        .toMutableMap()
}

fun <T : Forge> T.useCoreFactories(): T {
    addFactory(DatadogContextForgeryFactory())
    addFactory(DeviceInfoForgeryFactory())
    addFactory(NetworkInfoForgeryFactory())
    addFactory(LocaleInfoForgeryFactory())
    addFactory(ProcessInfoForgeryFactory())
    addFactory(TimeInfoForgeryFactory())
    addFactory(UserInfoForgeryFactory())
    addFactory(AccountInfoForgeryFactory())
    addFactory(RawBatchEventForgeryFactory())
    addFactory(ThreadDumpForgeryFactory())
    addFactory(RequestExecutionContextForgeryFactory())
    addFactory(RequestInfoForgeryFactory())

    return this
}

fun Forge.aHostName(): String {
    val regionSize = anInt(min = 2, max = 4)
    return "${anAlphabeticalString(Case.LOWER)}.${anAlphabeticalString(Case.LOWER, regionSize)}"
}

fun Forge.anUrlString(): String = aStringMatching(URL_FORGERY_PATTERN)

fun Forge.anHttpRequestInfo(headers: Map<String, String>): HttpRequestInfo {
    return (getForgery<HttpRequestInfo>() as MutableHttpRequestInfo)
        .newBuilder()
        .apply { headers.forEach { (key, value) -> addHeader(key, value) } }
        .build()
}

fun Forge.anOkHttpResponse(
    request: Request,
    statusCode: Int,
    configure: Response.Builder.() -> Unit = {}
): Response =
    Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_2)
        .code(statusCode)
        .message(anAsciiString())
        .apply(configure)
        .build()
