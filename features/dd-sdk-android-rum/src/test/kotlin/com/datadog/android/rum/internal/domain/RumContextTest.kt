/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(Configurator::class)
internal class RumContextTest {

    @RepeatedTest(8)
    fun `M return the same object W toMap + fromFeatureContext()`(
        @Forgery fakeRumContext: RumContext
    ) {
        // Given
        val anotherRumContext = RumContext.fromFeatureContext(fakeRumContext.toMap())

        // Then
        assertThat(anotherRumContext).isEqualTo(fakeRumContext)
    }

    @Test
    fun `M return no context W fromFeatureContext() {application id missing}`(
        @Forgery fakeRumContext: RumContext
    ) {
        // Given
        val featureContext = fakeRumContext.toMap() - RumContext.APPLICATION_ID

        // When
        val context = RumContext.fromFeatureContext(featureContext)

        // Then
        assertThat(context).isNull()
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = [RumContext.NULL_UUID])
    fun `M retain application without session W fromFeatureContext() {session absent}`(
        sessionId: String?,
        @StringForgery fakeApplicationId: String
    ) {
        // Given
        val featureContext =
            mapOf(
                RumContext.APPLICATION_ID to fakeApplicationId,
                RumContext.SESSION_ID to sessionId
            )

        // When
        val context = RumContext.fromFeatureContext(featureContext)

        // Then
        assertThat(context).isEqualTo(RumContext(applicationId = fakeApplicationId))
    }
}
