/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import androidx.fragment.app.Fragment
import androidx.leanback.app.HeadersSupportFragment
import androidx.leanback.app.RowsSupportFragment
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class AndroidTvFragmentPredicateTest {

    private lateinit var testedFragmentPredicate: AndroidTvFragmentPredicate

    @Mock
    lateinit var mockWrappedPredicate: ComponentPredicate<Fragment>

    @BeforeEach
    fun `set up`() {
        testedFragmentPredicate = AndroidTvFragmentPredicate(mockWrappedPredicate)
    }

    // region custom wrapped component predicate

    @Test
    fun `M call the wrapper W getViewName { custom wrapped component }`(
        @StringForgery fakeViewName: String
    ) {
        // Given
        val mockFragment: Fragment = mock()
        whenever(mockWrappedPredicate.getViewName(mockFragment)).thenReturn(fakeViewName)

        // When
        val viewName = testedFragmentPredicate.getViewName(mockFragment)

        // Then
        verify(mockWrappedPredicate).getViewName(mockFragment)
        assertThat(viewName).isEqualTo(fakeViewName)
    }

    @Test
    fun `M return false W accept { fragment is header support, custom wrapped component }`() {
        // Given
        val fakeFragment = HeadersSupportFragment()

        // Then
        assertThat(testedFragmentPredicate.accept(fakeFragment)).isFalse
        verifyZeroInteractions(mockWrappedPredicate)
    }

    @Test
    fun `M return false W accept { fragment is row support, custom wrapped component }`() {
        // Given
        val fakeFragment = RowsSupportFragment()

        // Then
        assertThat(testedFragmentPredicate.accept(fakeFragment)).isFalse
        verifyZeroInteractions(mockWrappedPredicate)
    }

    @Test
    fun `M call the wrapper W accept { fragment not dropped, custom wrapped component }`(
        @BoolForgery fakeReturnValue: Boolean
    ) {
        // Given
        val mockFragment: Fragment = mock()
        whenever(mockWrappedPredicate.accept(mockFragment)).thenReturn(fakeReturnValue)

        // Then
        assertThat(testedFragmentPredicate.accept(mockFragment)).isEqualTo(fakeReturnValue)
    }

    // endregion

    // region default wrapped component

    @Test
    fun `M use the default wrapped componentPredicate in case non provided W instantiating`() {
        // Given
        testedFragmentPredicate = AndroidTvFragmentPredicate()

        // When
        assertThat(testedFragmentPredicate.wrappedPredicate)
            .isExactlyInstanceOf(AcceptAllSupportFragments::class.java)
    }

    // endregion
}
