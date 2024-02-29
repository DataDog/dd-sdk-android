/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.assertj

import com.datadog.legacy.trace.api.Config
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class TracerConfigAssert(actual: Config) : AbstractObjectAssert<TracerConfigAssert, Config>
(actual, TracerConfigAssert::class.java) {

    fun hasServiceName(expected: String): TracerConfigAssert {
        assertThat(actual.serviceName)
            .overridingErrorMessage(
                "Expected config to have serviceName $expected but was ${actual.serviceName}"
            )
            .isEqualTo(expected)

        return this
    }

    companion object {
        fun assertThat(actual: Config): TracerConfigAssert {
            return TracerConfigAssert(actual)
        }
    }
}
