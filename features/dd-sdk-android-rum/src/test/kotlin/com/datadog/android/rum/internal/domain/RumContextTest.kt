/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.extension.ExtendWith

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
}
