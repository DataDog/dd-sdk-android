/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.internal.FeatureContextKeys
import com.datadog.android.internal.rum.RumContext
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(Configurator::class)
internal class RumFeatureContextTest {

    @Test
    fun `M publish only the typed snapshot W setRumContext()`(
        @Forgery fakeRumContext: RumContext
    ) {
        // Given
        val featureContext = mutableMapOf<String, Any?>()

        // When
        featureContext.setRumContext(fakeRumContext)

        // Then
        assertThat(featureContext[FeatureContextKeys.RUM_CONTEXT]).isSameAs(fakeRumContext)
        assertThat(featureContext).isEqualTo(mapOf(FeatureContextKeys.RUM_CONTEXT to fakeRumContext))
    }

    @Test
    fun `M clear the previous snapshot W setRumContext() {no context}`(
        @Forgery fakeRumContext: RumContext
    ) {
        // Given
        val featureContext = mutableMapOf<String, Any?>()
        featureContext.setRumContext(fakeRumContext)

        // When
        featureContext.setRumContext(null)

        // Then
        assertThat(featureContext).isEmpty()
    }
}
