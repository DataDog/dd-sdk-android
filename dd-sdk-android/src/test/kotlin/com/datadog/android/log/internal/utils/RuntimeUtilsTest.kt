package com.datadog.android.log.internal.utils

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension

@Extensions(ExtendWith(MockitoExtension::class))
class RuntimeUtilsTest {

    @Test
    fun `the sdk logger should always be disabled for remote logs`() {
    }

    @Test
    fun `the sdk logger should disable the logcat logs if the BuildConfig flag is false`() {
    }

    @Test
    fun `the sdk logger should enable the logcat logs if the BuildConfig flag is false`() {
    }
}
