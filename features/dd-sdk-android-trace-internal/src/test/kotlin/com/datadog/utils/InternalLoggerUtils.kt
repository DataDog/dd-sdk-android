/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.utils

import com.datadog.android.api.InternalLogger
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.same
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.verification.VerificationMode

fun InternalLogger.verifyLog(
    level: InternalLogger.Level,
    target: InternalLogger.Target,
    message: String,
    throwable: Throwable? = null,
    onlyOnce: Boolean = false,
    mode: VerificationMode = times(1),
    additionalProperties: Map<String, Any?>? = null
) {
    argumentCaptor<() -> String> {
        verify(this@verifyLog, mode).log(
            eq(level),
            eq(target),
            capture(),
            same(throwable),
            eq(onlyOnce),
            eq(additionalProperties),
            eq(false)
        )
        allValues.forEach {
            assertThat(it()).isEqualTo(message)
        }
    }
}

fun InternalLogger.verifyLog(
    level: InternalLogger.Level,
    targets: List<InternalLogger.Target>,
    message: String?,
    throwable: Throwable? = null,
    onlyOnce: Boolean = false,
    mode: VerificationMode = times(1),
    additionalProperties: Map<String, Any?>? = null
) {
    argumentCaptor<() -> String> {
        verify(this@verifyLog, mode).log(
            eq(level),
            eq(targets),
            capture(),
            same(throwable),
            eq(onlyOnce),
            eq(additionalProperties),
            eq(false)
        )
        assertThat(firstValue()).isEqualTo(message)
    }
}
