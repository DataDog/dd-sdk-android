/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.profiling

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.internal.FeatureContextKeys
import com.datadog.android.internal.profiling.quota.QuotaReason
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class ProfilingFeatureContextTest {

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @StringForgery
    lateinit var fakeSessionId: String

    @Test
    fun `M return quota reason W resolveProfilingQuotaReason() {reason for current session}`(
        @Forgery fakeQuotaReason: QuotaReason
    ) {
        // Given
        val datadogContext = fakeDatadogContext.withProfilingContext(
            FeatureContextKeys.PROFILING_QUOTA_REASON to fakeQuotaReason,
            FeatureContextKeys.PROFILING_QUOTA_SESSION_ID to fakeSessionId
        )

        // When
        val result = datadogContext.resolveProfilingQuotaReason(fakeSessionId)

        // Then
        assertThat(result).isEqualTo(fakeQuotaReason)
    }

    @Test
    fun `M return null W resolveProfilingQuotaReason() {reason for other session}`(
        @Forgery fakeQuotaReason: QuotaReason,
        @StringForgery fakeOtherSessionId: String
    ) {
        // Given
        val datadogContext = fakeDatadogContext.withProfilingContext(
            FeatureContextKeys.PROFILING_QUOTA_REASON to fakeQuotaReason,
            FeatureContextKeys.PROFILING_QUOTA_SESSION_ID to fakeOtherSessionId
        )

        // When
        val result = datadogContext.resolveProfilingQuotaReason(fakeSessionId)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W resolveProfilingQuotaReason() {no quota session id}`(
        @Forgery fakeQuotaReason: QuotaReason
    ) {
        // Given
        val datadogContext = fakeDatadogContext.withProfilingContext(
            FeatureContextKeys.PROFILING_QUOTA_REASON to fakeQuotaReason
        )

        // When
        val result = datadogContext.resolveProfilingQuotaReason(fakeSessionId)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W resolveProfilingQuotaReason() {reason is not a QuotaReason}`(
        @Forgery fakeQuotaReason: QuotaReason
    ) {
        // Given
        val datadogContext = fakeDatadogContext.withProfilingContext(
            FeatureContextKeys.PROFILING_QUOTA_REASON to fakeQuotaReason.rawValue,
            FeatureContextKeys.PROFILING_QUOTA_SESSION_ID to fakeSessionId
        )

        // When
        val result = datadogContext.resolveProfilingQuotaReason(fakeSessionId)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W resolveProfilingQuotaReason() {no profiling feature context}`() {
        // Given
        val datadogContext = fakeDatadogContext.copy(
            featuresContext = fakeDatadogContext.featuresContext - Feature.PROFILING_FEATURE_NAME
        )

        // When
        val result = datadogContext.resolveProfilingQuotaReason(fakeSessionId)

        // Then
        assertThat(result).isNull()
    }

    private fun DatadogContext.withProfilingContext(vararg entries: Pair<String, Any?>): DatadogContext {
        return copy(featuresContext = featuresContext + (Feature.PROFILING_FEATURE_NAME to mapOf(*entries)))
    }
}
