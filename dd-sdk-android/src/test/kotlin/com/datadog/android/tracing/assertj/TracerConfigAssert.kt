package com.datadog.android.tracing.assertj

import datadog.trace.api.Config
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class TracerConfigAssert(actual: Config) : AbstractObjectAssert<TracerConfigAssert, Config>
    (actual, TracerConfigAssert::class.java) {

    companion object {
        fun assertThat(actual: Config): TracerConfigAssert {
            return TracerConfigAssert(actual)
        }
    }

    fun hasServiceName(expected: String): TracerConfigAssert {
        assertThat(actual.serviceName)
            .overridingErrorMessage(
                "Expected config to have serviceName $expected but was ${actual.serviceName}"
            )
            .isEqualTo(expected)

        return this
    }
}
