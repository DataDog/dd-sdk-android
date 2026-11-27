/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.extensions

import com.datadog.tools.unit.annotations.ProhibitLeavingStaticMocksIn
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.reflect.jvm.javaMethod

internal class ProhibitLeavingStaticMocksAnnotationTest {

    private val testedExtension = ProhibitLeavingStaticMocksExtension()

    @ParameterizedTest
    @ValueSource(classes = [AnnotatedParent::class, Child::class, Grandchild::class])
    fun `M detect static mock W afterEach() {class annotation}`(fakeTestClass: Class<*>) {
        // Given
        val mockContext = mock<ExtensionContext>()
        whenever(mockContext.requiredTestClass).thenReturn(fakeTestClass)
        whenever(mockContext.requiredTestMethod).thenReturn(checkNotNull(AnnotatedParent::testMethod.javaMethod))

        // When / Then
        assertThrows<UnwantedStaticMockException> { testedExtension.afterEach(mockContext) }
    }

    @Test
    fun `M use nearest class annotation W afterEach() {subclass overrides scan roots}`() {
        // Given
        val mockContext = mock<ExtensionContext>()
        whenever(mockContext.requiredTestClass).thenReturn(AnnotatedChild::class.java)
        whenever(mockContext.requiredTestMethod).thenReturn(checkNotNull(AnnotatedParent::testMethod.javaMethod))

        // When / Then
        assertDoesNotThrow { testedExtension.afterEach(mockContext) }
    }

    internal object RootWithStaticMock {
        @Suppress("unused")
        val badMock: Any = mock()
    }

    @ProhibitLeavingStaticMocksIn(RootWithStaticMock::class)
    internal open class AnnotatedParent {
        fun testMethod() = Unit
    }

    internal open class Child : AnnotatedParent()

    internal class Grandchild : Child()

    @ProhibitLeavingStaticMocksIn
    internal class AnnotatedChild : AnnotatedParent()
}
