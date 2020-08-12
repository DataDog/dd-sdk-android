/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.app.Activity
import com.datadog.android.core.internal.utils.runIfValid
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
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
class ComponentPredicateTest {

    lateinit var testedPredicate: ComponentPredicate<Activity>

    @Mock
    lateinit var mockValidActivity: Activity

    @Mock
    lateinit var mockInvalidActivity: Activity

    @BeforeEach
    fun `set up`() {
        testedPredicate = object : ComponentPredicate<Activity> {
            override fun accept(component: Activity): Boolean {
                return component == mockValidActivity
            }
        }
    }

    @Test
    fun `it will not execute operation if argument is not verified`() {
        var operationExecuted = false
        testedPredicate.runIfValid(mockInvalidActivity) {
            operationExecuted = true
        }
        assertThat(operationExecuted).isFalse()
    }

    @Test
    fun `it will execute operation if argument is verified`() {
        var operationExecuted = false
        testedPredicate.runIfValid(mockValidActivity) {
            operationExecuted = true
        }
        assertThat(operationExecuted).isTrue()
    }
}
