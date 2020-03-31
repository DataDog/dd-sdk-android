/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.os.Bundle
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ActivityViewTrackingStrategyTest : ActivityLifecycleTrackingStrategyTest() {

    // region tests

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        underTest = ActivityViewTrackingStrategy(true)
    }

    @Test
    fun `when resumed it will start a view event`(forge: Forge) {
        // when
        underTest.onActivityResumed(mockActivity)
        // then
        verify(mockRumMonitor).startView(
            eq(mockActivity),
            eq(mockActivity.resolveViewName()),
            eq(emptyMap())
        )
    }

    @Test
    fun `when paused it will stop a view event`(forge: Forge) {
        // when
        underTest.onActivityPaused(mockActivity)
        // then
        verify(mockRumMonitor).stopView(
            eq(mockActivity),
            eq(emptyMap())
        )
    }

    @Test
    fun `when resumed will start a view event with intent extras as attributes`(
        forge: Forge
    ) {
        // given
        val arguments = Bundle()
        val expectedAttrs = mutableMapOf<String, Any?>()
        for (i in 0..10) {
            val key = forge.anAlphabeticalString()
            val value = forge.anAsciiString()
            arguments.putString(key, value)
            expectedAttrs["view.arguments.$key"] = value
        }
        whenever(mockIntent.extras).thenReturn(arguments)
        whenever(mockActivity.intent).thenReturn(mockIntent)

        // whenever
        underTest.onActivityResumed(mockActivity)

        verify(mockRumMonitor).startView(
            eq(mockActivity),
            eq(mockActivity.resolveViewName()),
            eq(expectedAttrs)
        )
    }

    @Test
    fun `when resumed and not tracking intent extras will send empty attributes`(
        forge: Forge
    ) {
        // given
        underTest = ActivityViewTrackingStrategy(false)
        val arguments = Bundle()
        for (i in 0..10) {
            val key = forge.anAlphabeticalString()
            val value = forge.anAsciiString()
            arguments.putString(key, value)
        }
        whenever(mockIntent.extras).thenReturn(arguments)
        whenever(mockActivity.intent).thenReturn(mockIntent)

        // whenever
        underTest.onActivityResumed(mockActivity)

        verify(mockRumMonitor).startView(
            eq(mockActivity),
            eq(mockActivity.resolveViewName()),
            eq(emptyMap())
        )
    }

    // endregion

    // region internal

    private fun Any.resolveViewName(): String {
        return javaClass.canonicalName ?: javaClass.simpleName
    }

    // endregion
}
