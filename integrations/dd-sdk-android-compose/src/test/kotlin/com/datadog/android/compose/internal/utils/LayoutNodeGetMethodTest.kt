/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose.internal.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

/**
 * Tests for the [LayoutNodeUtils.getMethod] reflection helper that resolves
 * Kotlin-internal method names regardless of module-suffix mangling.
 *
 * Compose internal properties are compiled to JVM methods whose names are mangled
 * with a module identifier suffix, e.g. {@code getLayoutDelegate$ui} (debug) or
 * {@code getLayoutDelegate$ui_release} (release).  [FakeLayoutNodeUi] and
 * [FakeLayoutNodeUiRelease] simulate these two naming conventions.
 */
internal class LayoutNodeGetMethodTest {

    private val testedLayoutNodeUtils = LayoutNodeUtils()

    // getMethod is private fun Any.getMethod(prefix: String): Any? compiled as an
    // instance method with signature getMethod(Object, String) in the JVM.
    private val getMethodFn: Method = LayoutNodeUtils::class.java
        .getDeclaredMethod("getMethod", Any::class.java, String::class.java)
        .also { it.isAccessible = true }

    // region $ui suffix

    @Test
    fun `M find method W getMethod {method has dollar ui suffix}`() {
        // Given
        val expected = Any()
        val fakeNode = FakeLayoutNodeUi(expected)

        // When
        val result = getMethodFn.invoke(testedLayoutNodeUtils, fakeNode, "getLayoutDelegate")

        // Then
        assertThat(result).isSameAs(expected)
    }

    @Test
    fun `M find method W getMethod {outer coordinator method has dollar ui suffix}`() {
        // Given
        val expected = Any()
        val fakeNode = FakeLayoutNodeUi(expected)

        // When
        val result = getMethodFn.invoke(testedLayoutNodeUtils, fakeNode, "getOuterCoordinator")

        // Then
        assertThat(result).isSameAs(expected)
    }

    @Test
    fun `M find method W getMethod {coordinates method has dollar ui suffix}`() {
        // Given
        val expected = Any()
        val fakeNode = FakeLayoutNodeUi(expected)

        // When
        val result = getMethodFn.invoke(testedLayoutNodeUtils, fakeNode, "getCoordinates")

        // Then
        assertThat(result).isSameAs(expected)
    }

    // endregion

    // region $ui_release suffix

    @Test
    fun `M find method W getMethod {method has dollar ui_release suffix}`() {
        // Given
        val expected = Any()
        val fakeNode = FakeLayoutNodeUiRelease(expected)

        // When
        val result = getMethodFn.invoke(testedLayoutNodeUtils, fakeNode, "getLayoutDelegate")

        // Then
        assertThat(result).isSameAs(expected)
    }

    @Test
    fun `M find method W getMethod {outer coordinator method has dollar ui_release suffix}`() {
        // Given
        val expected = Any()
        val fakeNode = FakeLayoutNodeUiRelease(expected)

        // When
        val result = getMethodFn.invoke(testedLayoutNodeUtils, fakeNode, "getOuterCoordinator")

        // Then
        assertThat(result).isSameAs(expected)
    }

    @Test
    fun `M find method W getMethod {coordinates method has dollar ui_release suffix}`() {
        // Given
        val expected = Any()
        val fakeNode = FakeLayoutNodeUiRelease(expected)

        // When
        val result = getMethodFn.invoke(testedLayoutNodeUtils, fakeNode, "getCoordinates")

        // Then
        assertThat(result).isSameAs(expected)
    }

    // endregion

    // region no match

    @Test
    fun `M return null W getMethod {no matching method}`() {
        // Given
        val fakeNode = Any()

        // When
        val result = getMethodFn.invoke(testedLayoutNodeUtils, fakeNode, "getLayoutDelegate")

        // Then
        assertThat(result).isNull()
    }

    // endregion
}
