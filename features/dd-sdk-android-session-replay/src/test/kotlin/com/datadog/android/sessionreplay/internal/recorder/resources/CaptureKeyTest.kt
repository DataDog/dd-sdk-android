/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.recorder.resources.DefaultDrawableCopier
import com.datadog.android.sessionreplay.recorder.resources.DrawableCopier
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
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
@ForgeConfiguration(ForgeConfigurator::class)
internal class CaptureKeyTest {

    @Mock
    lateinit var mockDrawable: Drawable

    @Mock
    lateinit var mockOtherDrawable: Drawable

    @Mock
    lateinit var mockCopier: DrawableCopier

    @Mock
    lateinit var mockOtherCopier: DrawableCopier

    @Mock
    lateinit var mockResources: Resources

    @Mock
    lateinit var mockOtherResources: Resources

    @Mock
    lateinit var mockDisplayMetrics: DisplayMetrics

    @Mock
    lateinit var mockOtherDisplayMetrics: DisplayMetrics

    @IntForgery(min = 1, max = 1000)
    var fakeSize: Int = 0

    @StringForgery
    lateinit var fakeCustomKey: String

    @Test
    fun `M be equal W equals() { same objects and settings }`() {
        // When
        val key = drawableKey()
        val otherKey = drawableKey()

        // Then
        assertThat(key).isEqualTo(otherKey)
        assertThat(key.hashCode()).isEqualTo(otherKey.hashCode())
    }

    @Test
    fun `M not be equal W equals() { another drawable }`() {
        // When / Then
        assertThat(drawableKey()).isNotEqualTo(drawableKey(drawable = mockOtherDrawable))
    }

    @Test
    fun `M not be equal W equals() { another copier }`() {
        // When / Then
        assertThat(drawableKey()).isNotEqualTo(drawableKey(copier = mockOtherCopier))
    }

    @Test
    fun `M be equal W equals() { two default copiers }`() {
        // When
        val key = drawableKey(copier = DefaultDrawableCopier())
        val otherKey = drawableKey(copier = DefaultDrawableCopier())

        // Then
        assertThat(key).isEqualTo(otherKey)
    }

    @Test
    fun `M not be equal W equals() { default copier and another copier }`() {
        // When / Then
        assertThat(drawableKey(copier = DefaultDrawableCopier())).isNotEqualTo(drawableKey(copier = mockCopier))
    }

    @Test
    fun `M not be equal W equals() { another resources }`() {
        // When / Then
        assertThat(drawableKey()).isNotEqualTo(drawableKey(resources = mockOtherResources))
    }

    @Test
    fun `M not be equal W equals() { another display metrics }`() {
        // When / Then
        assertThat(drawableKey()).isNotEqualTo(drawableKey(displayMetrics = mockOtherDisplayMetrics))
    }

    @Test
    fun `M not be equal W equals() { another width }`() {
        // When / Then
        assertThat(drawableKey(width = fakeSize)).isNotEqualTo(drawableKey(width = fakeSize + 1))
    }

    @Test
    fun `M not be equal W equals() { another height }`() {
        // When / Then
        assertThat(drawableKey(height = fakeSize)).isNotEqualTo(drawableKey(height = fakeSize + 1))
    }

    @Test
    fun `M not be equal W equals() { another custom key }`() {
        // When / Then
        assertThat(drawableKey(customKey = fakeCustomKey)).isNotEqualTo(drawableKey(customKey = "$fakeCustomKey-"))
    }

    @Test
    fun `M not be equal W equals() { drawable held in one and not the other }`() {
        // When / Then
        assertThat(drawableKey(isHolding = true)).isNotEqualTo(drawableKey(isHolding = false))
    }

    @Test
    fun `M be equal only for the same signature W equals() { bitmap keys }`() {
        // When / Then
        assertThat(CaptureKey.OfBitmap(fakeCustomKey)).isEqualTo(CaptureKey.OfBitmap(fakeCustomKey))
        assertThat(CaptureKey.OfBitmap(fakeCustomKey)).isNotEqualTo(CaptureKey.OfBitmap("$fakeCustomKey-"))
    }

    @Test
    fun `M not be equal W equals() { bitmap key and drawable key }`() {
        // When / Then
        assertThat(CaptureKey.OfBitmap(fakeCustomKey)).isNotEqualTo(drawableKey(customKey = fakeCustomKey))
    }

    private fun drawableKey(
        drawable: Drawable = mockDrawable,
        copier: DrawableCopier = mockCopier,
        resources: Resources = mockResources,
        displayMetrics: DisplayMetrics = mockDisplayMetrics,
        width: Int = 0,
        height: Int = 0,
        customKey: String? = null,
        isHolding: Boolean = false
    ): CaptureKey = CaptureKey.OfDrawable(
        drawable = drawable,
        copier = copier,
        resources = resources,
        displayMetrics = displayMetrics,
        width = width,
        height = height,
        customResourceIdCacheKey = customKey,
        holdsDrawableStrongly = isHolding
    )
}
