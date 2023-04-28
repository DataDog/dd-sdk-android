/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import androidx.navigation.NavDestination
import com.datadog.tools.unit.ObjectTest
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.mock

@ExtendWith(ForgeExtension::class)
internal class AcceptAllNavDestinationsTest : ObjectTest<AcceptAllNavDestinations>() {

    override fun createInstance(forge: Forge): AcceptAllNavDestinations {
        return AcceptAllNavDestinations()
    }

    override fun createEqualInstance(
        source: AcceptAllNavDestinations,
        forge: Forge
    ): AcceptAllNavDestinations {
        return AcceptAllNavDestinations()
    }

    override fun createUnequalInstance(
        source: AcceptAllNavDestinations,
        forge: Forge
    ): AcceptAllNavDestinations? {
        return null
    }

    @Test
    fun `M return true W accept()`() {
        // Given
        val fakeNavDest = mock<NavDestination>()
        val testedPredicate = AcceptAllNavDestinations()

        // When
        val result = testedPredicate.accept(fakeNavDest)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return null W getViewName()`() {
        // Given
        val fakeNavDest = mock<NavDestination>()
        val testedPredicate = AcceptAllNavDestinations()

        // When
        val result = testedPredicate.getViewName(fakeNavDest)

        // Then
        assertThat(result).isNull()
    }
}
