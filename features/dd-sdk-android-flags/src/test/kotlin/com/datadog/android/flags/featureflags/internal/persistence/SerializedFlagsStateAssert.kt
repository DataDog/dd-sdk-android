/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.persistence

import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject

internal class SerializedFlagsStateAssert(actual: JSONObject) :
    AbstractObjectAssert<SerializedFlagsStateAssert, JSONObject>(
        actual,
        SerializedFlagsStateAssert::class.java
    ) {

    fun hasTargetingKey(expected: String): SerializedFlagsStateAssert {
        val context = actual.getJSONObject("evaluationContext")
        assertThat(context.getString("targetingKey"))
            .overridingErrorMessage(
                "Expected evaluationContext.targetingKey to be $expected " +
                    "but was ${context.getString("targetingKey")}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasFlag(flagName: String): SerializedFlagsStateAssert {
        val flags = actual.getJSONObject("flags")
        assertThat(flags.has(flagName))
            .overridingErrorMessage(
                "Expected flags to contain flag named $flagName"
            )
            .isTrue()
        return this
    }

    fun hasEmptyFlags(): SerializedFlagsStateAssert {
        val flags = actual.getJSONObject("flags")
        assertThat(flags.length())
            .overridingErrorMessage(
                "Expected flags to be empty but had ${flags.length()} entries"
            )
            .isEqualTo(0)
        return this
    }

    fun hasTimestamp(expected: Long): SerializedFlagsStateAssert {
        assertThat(actual.getLong("lastUpdateTimestamp"))
            .overridingErrorMessage(
                "Expected lastUpdateTimestamp to be $expected " +
                    "but was ${actual.getLong("lastUpdateTimestamp")}"
            )
            .isEqualTo(expected)
        return this
    }

    companion object {
        fun assertThatSerializedFlagsState(json: String): SerializedFlagsStateAssert {
            return SerializedFlagsStateAssert(JSONObject(json))
        }
    }
}
