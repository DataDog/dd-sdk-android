/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.log.model.LogEvent
import com.datadog.android.log.model.WebViewLogEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class WebViewLogEventForgeryFactory : ForgeryFactory<WebViewLogEvent> {
    override fun getForgery(forge: Forge): WebViewLogEvent {
        val reservedKeysAsSet = mutableSetOf<String>().apply {
            LogEvent.RESERVED_PROPERTIES.forEach {
                this.add(it)
            }
        }

        return WebViewLogEvent(
            service = forge.aNullable { forge.anAlphabeticalString() },
            status = forge.aNullable { forge.aValueFrom(WebViewLogEvent.Status::class.java) },
            message = forge.anAlphabeticalString(),
            date = forge.aLong(min = 0),
            additionalProperties = forge.exhaustiveAttributes(
                excludedKeys = reservedKeysAsSet,
                filterThreshold = 0f
            ),
            ddtags = forge.exhaustiveTags().joinToString(separator = ",")
        )
    }

    private fun Forge.exhaustiveTags(): List<String> {
        return aList { aStringMatching("[a-z]([a-z0-9_:./-]{0,198}[a-z0-9_./-])?") }
    }
}
