/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.quota

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class QuotaResultTest {

    @Test
    fun `M hold decision and reason W constructed`() {
        val result = QuotaResult(QuotaResult.Decision.DENIED, "quota_exceeded")
        assertThat(result.decision).isEqualTo(QuotaResult.Decision.DENIED)
        assertThat(result.reason).isEqualTo("quota_exceeded")
    }

    @Test
    fun `M be ALLOWED with timeout reason W FAIL_OPEN`() {
        assertThat(QuotaResult.FAIL_OPEN.decision).isEqualTo(QuotaResult.Decision.ALLOWED)
        assertThat(QuotaResult.FAIL_OPEN.reason).isEqualTo("timeout")
    }

    @Test
    fun `M be ALLOWED with api-error reason W API_ERROR`() {
        assertThat(QuotaResult.API_ERROR.decision).isEqualTo(QuotaResult.Decision.ALLOWED)
        assertThat(QuotaResult.API_ERROR.reason).isEqualTo("api-error")
    }
}
