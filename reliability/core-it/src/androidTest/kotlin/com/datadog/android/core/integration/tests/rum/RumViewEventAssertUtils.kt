/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests.rum

import com.datadog.android.rum.model.ViewEvent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.assertj.core.api.Assertions.assertThat

object RumViewEventAssertUtils {
    fun assertViewEvent(viewEvent: ViewEvent, backendViewEvent: JsonObject) {
        assertThat(viewEvent.view.action.count).isEqualTo(
            backendViewEvent["attributes"]
                ?.jsonObject
                ?.get("action")
                ?.jsonObject
                ?.get("count")
                ?.jsonPrimitive
                ?.long
        )
    }
}
