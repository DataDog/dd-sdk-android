/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import com.google.gson.JsonObject

internal data class SessionReplayResourceContext(
    var applicationId: String,
    var type: String = "resource"
) {
    internal fun toJson(): String {
        val json = JsonObject()
        json.addProperty(APPLICATION_ID_KEY, applicationId)
        json.addProperty(TYPE_KEY, type)
        return json.toString()
    }

    internal companion object {
        const val APPLICATION_ID_KEY: String = "application_id"
        const val TYPE_KEY: String = "type"
    }
}
