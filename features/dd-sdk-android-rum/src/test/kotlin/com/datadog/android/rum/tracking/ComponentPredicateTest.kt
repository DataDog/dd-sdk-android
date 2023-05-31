/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.app.Activity
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.resolveViewName
import com.datadog.android.rum.utils.resolveViewUrl
import com.datadog.android.rum.utils.runIfValid
import com.datadog.android.v2.api.InternalLogger
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
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
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class ComponentPredicateTest {

    lateinit var testedPredicate: ComponentPredicate<Activity>

    @Mock
    lateinit var mockValidActivity: Activity

    @Mock
    lateinit var mockInvalidActivity: Activity

    @Mock
    lateinit var mockBlankActivity: Activity

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @StringForgery
    lateinit var fakeValidName: String

    @StringForgery(StringForgeryType.WHITESPACE)
    lateinit var fakeBlankName: String

    @BeforeEach
    fun `set up`() {
        testedPredicate = object : ComponentPredicate<Activity> {
            override fun accept(component: Activity): Boolean {
                return component == mockValidActivity
            }

            override fun getViewName(component: Activity): String? {
                return when (component) {
                    mockValidActivity -> fakeValidName
                    mockBlankActivity -> fakeBlankName
                    else -> null
                }
            }
        }
    }

    @Test
    fun `M not execute operation W runIfValid() {invalid}`() {
        var operationExecuted = false

        testedPredicate.runIfValid(mockInvalidActivity, mockInternalLogger) {
            operationExecuted = true
        }

        assertThat(operationExecuted).isFalse()
    }

    @Test
    fun `M execute operation W runIfValid() {valid}`() {
        var operationExecuted = false
        testedPredicate.runIfValid(mockValidActivity, mockInternalLogger) {
            operationExecuted = true
        }
        assertThat(operationExecuted).isTrue()
    }

    @Test
    fun `M return custom name W resolveViewName() {valid}`() {
        val name = testedPredicate.resolveViewName(mockValidActivity)

        assertThat(name).isEqualTo(fakeValidName)
    }

    @Test
    fun `M return default url W resolveViewName() {blank}`() {
        val name = testedPredicate.resolveViewName(mockBlankActivity)

        assertThat(name).isEqualTo(mockBlankActivity.resolveViewUrl())
    }

    @Test
    fun `M return default url W resolveViewName() {null}`() {
        val name = testedPredicate.resolveViewName(mockInvalidActivity)

        assertThat(name).isEqualTo(mockInvalidActivity.resolveViewUrl())
    }
}
