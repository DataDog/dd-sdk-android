/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration.tests.assertj

import com.datadog.android.rum.model.ViewUpdateEvent
import com.datadog.android.tests.assertj.StubEventsAssert
import org.assertj.core.api.Assertions

fun StubEventsAssert.hasRumEvent(index: Int, assertion: RumEventAssert.() -> Unit): StubEventsAssert {
    hasJsonObject(index) {
        val rumEventAssert = RumEventAssert(it)
        rumEventAssert.assertion()
    }
    return this
}

fun StubEventsAssert.hasRumViewUpdateEvent(index: Int, expected: ViewUpdateEvent): StubEventsAssert {
    hasJsonObject(index) { jsonObject ->
        val actual = ViewUpdateEvent.fromJsonObject(jsonObject)
        Assertions.assertThat(actual).isEqualTo(expected)
    }
    return this
}

fun StubEventsAssert.hasRumViewUpdateEvent(index: Int, assertion: ViewUpdateAssert.() -> Unit): StubEventsAssert {
    hasJsonObject(index) { jsonObject ->
        val actual = ViewUpdateEvent.fromJsonObject(jsonObject)
        ViewUpdateAssert.assertThat(actual).assertion()
    }
    return this
}
