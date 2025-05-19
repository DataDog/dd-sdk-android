/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.drawable.Drawable
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
class DrawableExtTest {

    @Test
    fun `M return canonicalName W resolveClassName { canonicalName is not null }`() {
        val drawable = mock<Drawable>()
        assertThat(drawable.resolveClassName())
            .isEqualTo(drawable.javaClass.canonicalName)
            .isNotNull()
    }

    @Test
    fun `M return simpleName W resolveClassName { canonicalName is null }`() {
        val drawable = object : Drawable() {
            override fun draw(canvas: Canvas) {
            }

            override fun setAlpha(alpha: Int) {
            }

            override fun setColorFilter(colorFilter: ColorFilter?) {
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun getOpacity(): Int {
                return 0
            }
        }

        assertThat(drawable.javaClass.canonicalName).isNull()
        assertThat(drawable.resolveClassName())
            .isEqualTo(drawable.javaClass.simpleName)
            .isNotNull()
    }
}
