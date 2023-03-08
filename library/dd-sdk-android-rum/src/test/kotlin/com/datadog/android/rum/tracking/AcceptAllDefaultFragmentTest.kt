/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("DEPRECATION")

package com.datadog.android.rum.tracking

import android.app.Fragment
import com.datadog.tools.unit.ObjectTest
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
internal class AcceptAllDefaultFragmentTest : ObjectTest<AcceptAllDefaultFragment>() {

    override fun createInstance(forge: Forge): AcceptAllDefaultFragment {
        return AcceptAllDefaultFragment()
    }

    override fun createEqualInstance(
        source: AcceptAllDefaultFragment,
        forge: Forge
    ): AcceptAllDefaultFragment {
        return AcceptAllDefaultFragment()
    }

    override fun createUnequalInstance(
        source: AcceptAllDefaultFragment,
        forge: Forge
    ): AcceptAllDefaultFragment? {
        return null
    }

    @Test
    fun `M return true W accept()`() {
        // Given
        val fakeFragment = mock<Fragment>()
        val testedPredicate = AcceptAllDefaultFragment()

        // When
        val result = testedPredicate.accept(fakeFragment)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return null W getViewName()`() {
        // Given
        val fakeFragment = mock<Fragment>()
        val testedPredicate = AcceptAllDefaultFragment()

        // When
        val result = testedPredicate.getViewName(fakeFragment)

        // Then
        assertThat(result).isNull()
    }
}
