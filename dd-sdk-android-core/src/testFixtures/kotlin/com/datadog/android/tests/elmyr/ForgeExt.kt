/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tests.elmyr

import fr.xgouchet.elmyr.Forge
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
    addFactory(ProcessInfoForgeryFactory())
    addFactory(TimeInfoForgeryFactory())
    addFactory(UserInfoForgeryFactory())
    addFactory(AccountInfoForgeryFactory())
    addFactory(RawBatchEventForgeryFactory())
    addFactory(ThreadDumpForgeryFactory())
    addFactory(RequestExecutionContextForgeryFactory())

    return this
}
