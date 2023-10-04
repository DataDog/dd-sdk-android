/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.utils

import com.datadog.android.nightly.rum.RUM_ACTION_PREFIX
import com.datadog.android.nightly.rum.RUM_ERROR_MESSAGE_PREFIX
import com.datadog.android.nightly.rum.RUM_RESOURCE_ERROR_MESSAGE_PREFIX
import com.datadog.android.nightly.rum.RUM_RESOURCE_URL_PREFIX
import com.datadog.android.nightly.rum.RUM_VIEW_PREFIX
import com.datadog.android.nightly.rum.RUM_VIEW_URL_PREFIX
import com.datadog.android.nightly.rum.TAG_VALUE_PREFIX
import com.datadog.android.rum.RumResourceMethod
import fr.xgouchet.elmyr.Forge

fun Forge.exhaustiveAttributes(): Map<String, Any?> {
    return listOf(
        aBool(),
        anInt(),
        aLong(),
        aFloat(),
        aDouble(),
        anAsciiString(),
        aList { anAlphabeticalString() },
        null
    ).associateBy { anAlphabeticalString() }
}

fun Forge.aViewName(prefix: String = RUM_VIEW_PREFIX): String {
    return prefix + this.aStringMatching("[a-zA-z](.+)")
}

fun Forge.aViewKey(prefix: String = RUM_VIEW_URL_PREFIX): String {
    return prefix + this.aStringMatching("[a-zA-z]{3,5}/[a-zA-z]{3,5}")
}

fun Forge.anActionName(prefix: String = RUM_ACTION_PREFIX): String {
    return prefix + this.aStringMatching("[a-zA-z](.+)")
}

fun Forge.aResourceKey(prefix: String = RUM_RESOURCE_URL_PREFIX): String {
    return this.aStringMatching("http://$prefix[a-z0-9_]{8}\\.[a-z]{3}")
}

fun Forge.aResourceErrorMessage(prefix: String = RUM_RESOURCE_ERROR_MESSAGE_PREFIX): String {
    return prefix + this.aStringMatching("[a-zA-z](.+)")
}

fun Forge.anErrorMessage(prefix: String = RUM_ERROR_MESSAGE_PREFIX): String {
    return prefix + this.aStringMatching("[a-zA-z](.+)")
}

fun Forge.aResourceMethod(): RumResourceMethod {
    return this.aValueFrom(RumResourceMethod::class.java)
}

fun Forge.aTagValue(prefix: String = TAG_VALUE_PREFIX): String {
    return prefix + this.aStringMatching("[a-z0-9_:./-]{1,20}")
}
