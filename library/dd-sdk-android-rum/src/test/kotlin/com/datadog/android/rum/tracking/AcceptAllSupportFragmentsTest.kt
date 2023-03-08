/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import androidx.fragment.app.Fragment
import com.datadog.tools.unit.ObjectTest
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
internal class AcceptAllSupportFragmentsTest : ObjectTest<AcceptAllSupportFragments>() {

    override fun createInstance(forge: Forge): AcceptAllSupportFragments {
        return AcceptAllSupportFragments()
    }

    override fun createEqualInstance(
        source: AcceptAllSupportFragments,
        forge: Forge
    ): AcceptAllSupportFragments {
        return AcceptAllSupportFragments()
    }

    override fun createUnequalInstance(
        source: AcceptAllSupportFragments,
        forge: Forge
    ): AcceptAllSupportFragments? {
        return null
    }

    @Test
    fun `M return true W accept()`() {
        // Given
        val fakeFragment = mock<Fragment>()
        val testedPredicate = AcceptAllSupportFragments()

        // When
        val result = testedPredicate.accept(fakeFragment)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return null W getViewName()`() {
        // Given
        val fakeFragment = mock<Fragment>()
        val testedPredicate = AcceptAllSupportFragments()

        // When
        val result = testedPredicate.getViewName(fakeFragment)

        // Then
        assertThat(result).isNull()
    }
}
