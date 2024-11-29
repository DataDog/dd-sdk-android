/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric.interactiontonextview

import com.datadog.android.rum.RumActionType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(ExtendWith(MockitoExtension::class))
@MockitoSettings(strictness = Strictness.LENIENT)
internal class ActionTypeInteractionValidatorTest {

    private lateinit var testedValidator: ActionTypeInteractionValidator

    // region setUp

    @BeforeEach
    fun `set up`() {
        testedValidator = ActionTypeInteractionValidator()
    }

    // endregion

    // region Tests

    @ParameterizedTest
    @MethodSource("validTypesContexts")
    fun `M return true W validate { allowed type }`(validContext: InternalInteractionContext) {
        // Given // When
        val result = testedValidator.validate(validContext)

        // Then
        assertThat(result).isTrue()
    }

    @ParameterizedTest
    @MethodSource("invalidTypesContexts")
    fun `M return false W validate { not allowed type }`(invalidContext: InternalInteractionContext) {
        // Given // When
        val result = testedValidator.validate(invalidContext)

        // Then
        assertThat(result).isFalse()
    }

    // endregion

    companion object {

        @JvmStatic
        fun validTypesContexts(): List<InternalInteractionContext> {
            return listOf(
                InternalInteractionContext(
                    "viewId",
                    RumActionType.TAP,
                    System.nanoTime()
                ),
                InternalInteractionContext(
                    "viewId",
                    RumActionType.CLICK,
                    System.nanoTime()
                ),
                InternalInteractionContext(
                    "viewId",
                    RumActionType.SWIPE,
                    System.nanoTime()
                ),
                InternalInteractionContext(
                    "viewId",
                    RumActionType.BACK,
                    System.nanoTime()
                )
            )
        }

        @JvmStatic
        fun invalidTypesContexts(): List<InternalInteractionContext> {
            return listOf(
                InternalInteractionContext(
                    "viewId",
                    RumActionType.CUSTOM,
                    System.nanoTime()
                ),
                InternalInteractionContext(
                    "viewId",
                    RumActionType.SCROLL,
                    System.nanoTime()
                )
            )
        }
    }
}
