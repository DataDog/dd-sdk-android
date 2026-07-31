/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.datadog.android.compose.datadog
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicInteger

/**
 * Instrumented regression test for #3661.
 *
 * Ports the measurement approach of the minimal reproduction (`dd-kcp-repro`): a parent recomposes
 * repeatedly while forwarding a stable, value-equal payload plus a [Modifier] to a skippable child.
 * The child records a "wasted execution" whenever its body re-runs with data identical to what it
 * last rendered — exactly the work Compose skipping exists to eliminate.
 *
 * With the fixed, equality-stable Datadog semantics modifier, an unchanged child must skip, so
 * wasted executions stay at zero even though the parent churns. Before the fix the injected modifier
 * was never value-equal across recompositions, so every recomposition of the parent forced the child
 * to re-execute, driving wasted executions up in lockstep.
 *
 * This test lives in `:instrumented:integration` (rather than `:integrations:dd-sdk-android-compose`)
 * so it runs as part of the existing instrumented-test CI coverage. `Modifier.instrumentedDatadog()`
 * is `internal` to the compose module, so it is invoked here via reflection.
 */
@RunWith(AndroidJUnit4::class)
internal class DatadogModifierRecompositionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @After
    fun tearDown() {
        Counters.reset()
    }

    @Test
    fun mustPreserveRecompositionSkipping_when_datadogModifierApplied() {
        // When
        driveRecompositions(ModifierUnderTest.DATADOG)

        // Then
        assertThat(Counters.parentCompositions.get())
            .describedAs("the parent must actually recompose, otherwise the test is vacuous")
            .isGreaterThan(1)
        assertThat(Counters.childWasted.get())
            .describedAs("Modifier.datadog() must not defeat skipping of an unchanged child")
            .isZero()
    }

    @Test
    fun mustPreserveRecompositionSkipping_when_instrumentedDatadogModifierApplied() {
        // When
        driveRecompositions(ModifierUnderTest.INSTRUMENTED_DATADOG)

        // Then
        assertThat(Counters.parentCompositions.get())
            .describedAs("the parent must actually recompose, otherwise the test is vacuous")
            .isGreaterThan(1)
        assertThat(Counters.childWasted.get())
            .describedAs("Modifier.instrumentedDatadog() must not defeat skipping of an unchanged child")
            .isZero()
    }

    @Test
    fun mustDefeatRecompositionSkipping_when_capturingLambdaModifierApplied_negativeControl() {
        // This is a negative control: a modifier built from a capturing lambda (the mechanism the
        // fix removed) is never value-equal across recompositions and MUST defeat skipping. It
        // proves the harness can actually observe the regression, so the zero-waste assertions above
        // are meaningful rather than vacuously true.

        // When
        driveRecompositions(ModifierUnderTest.CAPTURING_LAMBDA)

        // Then
        assertThat(Counters.childWasted.get())
            .describedAs("a capturing-lambda modifier is expected to defeat skipping")
            .isGreaterThan(0)
    }

    private fun driveRecompositions(kind: ModifierUnderTest) {
        val tick = mutableIntStateOf(0)
        composeTestRule.setContent {
            Host(tick = tick, kind = kind)
        }
        composeTestRule.waitForIdle()

        repeat(RECOMPOSITION_COUNT) {
            composeTestRule.runOnUiThread { tick.intValue += 1 }
            composeTestRule.waitForIdle()
        }
    }

    private enum class ModifierUnderTest {
        DATADOG,
        INSTRUMENTED_DATADOG,
        CAPTURING_LAMBDA
    }

    private companion object {
        private const val RECOMPOSITION_COUNT = 10
        private const val ROW_NAME = "row"
        private val FIXED_ROW = RowData(id = 1, value = 42)

        private const val DATADOG_MODIFIER_KT_CLASS = "com.datadog.android.compose.DatadogModifierKt"
        private const val INSTRUMENTED_DATADOG_METHOD_NAME = "instrumentedDatadog"

        // Modifier.instrumentedDatadog() is internal to :integrations:dd-sdk-android-compose,
        // reserved for the Datadog Kotlin Compiler Plugin's auto-instrumentation. It cannot be
        // called directly from this module, so it's invoked reflectively instead.
        private val instrumentedDatadogMethod: Method by lazy {
            Class.forName(DATADOG_MODIFIER_KT_CLASS).getDeclaredMethod(
                INSTRUMENTED_DATADOG_METHOD_NAME,
                Modifier::class.java,
                String::class.java,
                Boolean::class.javaPrimitiveType
            ).apply { isAccessible = true }
        }

        private fun Modifier.instrumentedDatadog(name: String, isImage: Boolean): Modifier {
            return instrumentedDatadogMethod.invoke(null, this, name, isImage) as Modifier
        }
    }

    // region Test composables

    private data class RowData(val id: Int, val value: Int)

    private object Counters {
        val childWasted = AtomicInteger(0)
        val parentCompositions = AtomicInteger(0)

        fun reset() {
            childWasted.set(0)
            parentCompositions.set(0)
        }
    }

    @Composable
    private fun Host(tick: IntState, kind: ModifierUnderTest) {
        Counters.parentCompositions.incrementAndGet()
        val childModifier = when (kind) {
            ModifierUnderTest.DATADOG -> Modifier.datadog(ROW_NAME)
            ModifierUnderTest.INSTRUMENTED_DATADOG -> Modifier.instrumentedDatadog(ROW_NAME, isImage = false)
            // Built in a NON-@Composable helper on purpose: the Compose compiler only memoizes
            // stable-capturing lambdas inside @Composable functions, so building it outside one
            // yields a fresh, never-equal element each recomposition — the exact mechanism the fix
            // removed (the original semantics {} lived in a non-composable helper too).
            ModifierUnderTest.CAPTURING_LAMBDA -> Modifier.capturingLambdaSemantics(ROW_NAME)
        }
        Column {
            // Reading the ticking state here forces Host to recompose on every tick, exercising the
            // forwarded-modifier path without changing anything the child depends on.
            val current by tick
            Text(text = "tick=$current")
            MeasuredRow(data = FIXED_ROW, modifier = childModifier)
        }
    }

    @Composable
    private fun MeasuredRow(data: RowData, modifier: Modifier) {
        // Scroll-proof waste detection: count only re-executions where the data is identical to what
        // was last rendered. Initial composition never counts; only redundant re-executions do — the
        // exact work skipping should eliminate.
        val lastRendered = remember { arrayOfNulls<RowData>(1) }
        SideEffect {
            if (lastRendered[0] == data) {
                Counters.childWasted.incrementAndGet()
            }
            lastRendered[0] = data
        }
        Box(modifier = modifier)
    }

    // Intentionally NOT @Composable: keeps the capturing lambda out of the Compose compiler's
    // lambda-memoization scope, so a fresh, never-equal element is allocated on each call.
    private fun Modifier.capturingLambdaSemantics(description: String): Modifier =
        this.semantics { contentDescription = description }

    // endregion
}
