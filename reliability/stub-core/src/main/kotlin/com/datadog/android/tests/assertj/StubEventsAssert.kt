/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tests.assertj

import android.annotation.SuppressLint
import com.datadog.android.core.stub.StubEvent
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

/**
 * Assertions class for a list of [StubEvent].
 * @param actual the actual value to write assertions on.
 */
@SuppressLint("VisibleForTests")
@Suppress("UndocumentedPublicFunction")
class StubEventsAssert(actual: List<StubEvent>) :
    AbstractObjectAssert<StubEventsAssert, List<StubEvent>>(actual, StubEventsAssert::class.java) {

    fun hasSize(size: Int): StubEventsAssert {
        assertThat(actual.size)
            .overridingErrorMessage("Expecting: event list to have size: $size but was ${actual.size}.")
            .isEqualTo(size)
        return this
    }

    fun hasJsonObject(index: Int, assertion: (JsonObject) -> Unit): StubEventsAssert {
        assertThat(actual.size)
            .overridingErrorMessage(
                "Expecting: event list to have size greater or equal to ${index + 1} but was ${actual.size}."
            )
            .isGreaterThanOrEqualTo(index + 1)

        val stubEvent = actual[index]
        val parsedAsJson = JsonParser.parseString(stubEvent.eventData) as JsonObject
        assertion(parsedAsJson)
        return this
    }

    companion object {

        /**
         * Creates a new instance of [StubEventsAssert].
         *
         * @param actual the actual value.
         * @return the created assertion object.
         */
        fun assertThat(actual: List<StubEvent>): StubEventsAssert {
            return StubEventsAssert(actual)
        }
    }
}
