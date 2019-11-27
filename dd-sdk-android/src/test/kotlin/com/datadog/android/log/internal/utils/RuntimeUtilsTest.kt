package com.datadog.android.log.internal.utils

import com.datadog.android.utils.BuildConfigExtension
import com.datadog.android.utils.EnableLogcat
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension

@Extensions(ExtendWith(MockitoExtension::class, BuildConfigExtension::class))
class RuntimeUtilsTest {

    @Test
    fun `the sdk logger should always be disabled for remote logs`() {
        assertThat(sdkLogger.datadogLogsEnabled).isFalse()
    }

    @Test
    @EnableLogcat(isEnabled = false)
    fun `the sdk logger should disable the logcat logs if the BuildConfig flag is false`() {
        assertThat(buildLogger().logcatLogsEnabled).isFalse()
    }

    @Test
    @EnableLogcat(isEnabled = true)
    fun `the sdk logger should enable the logcat logs if the BuildConfig flag is false`() {
        assertThat(buildLogger().logcatLogsEnabled).isTrue()
    }
}
