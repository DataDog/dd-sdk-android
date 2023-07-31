/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.extensions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock

@Suppress("UtilityClassWithPublicConstructor")
class ProhibitLeavingStaticMocksExtensionTest {

    private val testedExtension = ProhibitLeavingStaticMocksExtension()

    @Test
    fun `M throw an error W mock in object class`() {
        val roots = listOf(MockInObjectClass::class)
        assertThrows<UnwantedStaticMockException> {
            testedExtension.scanForStaticMocksLeft(roots, PACKAGE_PREFIXES)
        }
    }

    @Test
    fun `M throw an error W mock in object class { mock belongs to the parent class }`() {
        val roots = listOf(MockInParentClass::class)
        assertThrows<UnwantedStaticMockException> {
            testedExtension.scanForStaticMocksLeft(roots, PACKAGE_PREFIXES)
        }
    }

    @Test
    fun `M throw an error W mock in object class { property with custom getter }`() {
        val roots = listOf(MockInObjectClassPropertyWithCustomGetter::class)
        assertThrows<UnwantedStaticMockException> {
            testedExtension.scanForStaticMocksLeft(roots, PACKAGE_PREFIXES)
        }
    }

    @Test
    fun `M throw an error W mock in object class { delegate property }`() {
        val roots = listOf(MockInObjectClassPropertyDelegate::class)
        assertThrows<UnwantedStaticMockException> {
            testedExtension.scanForStaticMocksLeft(roots, PACKAGE_PREFIXES)
        }
    }

    @Test
    fun `M throw an error W mock in java class`() {
        val roots = listOf(JavaClassWithStaticMock::class)
        assertThrows<UnwantedStaticMockException> {
            testedExtension.scanForStaticMocksLeft(roots, PACKAGE_PREFIXES)
        }
    }

    @Test
    fun `M throw an error W mock in companion object`() {
        val roots = listOf(MockInCompanionObject::class)
        assertThrows<UnwantedStaticMockException> {
            testedExtension.scanForStaticMocksLeft(roots, PACKAGE_PREFIXES)
        }
    }

    @Test
    fun `M throw an error W mock in nested object`() {
        val roots = listOf(MockInNestedObject::class)
        assertThrows<UnwantedStaticMockException> {
            testedExtension.scanForStaticMocksLeft(roots, PACKAGE_PREFIXES)
        }
    }

    @Test
    fun `M exception thrown has valid message W mock in nested object`() {
        val roots = listOf(MockInNestedObject::class)
        val exception = assertThrows<UnwantedStaticMockException> {
            testedExtension.scanForStaticMocksLeft(roots, PACKAGE_PREFIXES)
        }

        assertThat(exception.message)
            .isEqualTo(
                "Unexpected mock remaining in the field badMock of com.datadog.tools.unit." +
                    "extensions.ProhibitLeavingStaticMocksExtensionTest\$MockInObjectClass.\n" +
                    "Calling sequence:\ncom.datadog.tools.unit.extensions." +
                    "ProhibitLeavingStaticMocksExtensionTest\$MockInNestedObject.nested"
            )
    }

    @Test
    fun `M throw an error W mock in nested of java object`() {
        val roots = listOf(JavaClassWithNestedStaticMock::class)
        assertThrows<UnwantedStaticMockException> {
            testedExtension.scanForStaticMocksLeft(roots, PACKAGE_PREFIXES)
        }
    }

    @Test
    fun `M throw an error W mock in nested java object`() {
        val roots = listOf(MockInNestedJavaObject::class)
        assertThrows<UnwantedStaticMockException> {
            testedExtension.scanForStaticMocksLeft(roots, PACKAGE_PREFIXES)
        }
    }

    @Test
    fun `M throw an error W mock in nested companion object`() {
        val roots = listOf(MockInNestedCompanionObject::class)
        assertThrows<UnwantedStaticMockException> {
            testedExtension.scanForStaticMocksLeft(roots, PACKAGE_PREFIXES)
        }
    }

    @Test
    fun `M throw an error W mock in nested object of companion object`() {
        val roots = listOf(MockInNestedOfCompanionObject::class)
        assertThrows<UnwantedStaticMockException> {
            testedExtension.scanForStaticMocksLeft(roots, PACKAGE_PREFIXES)
        }
    }

    @Test
    fun `M throw an error W mock in nested java object of companion object`() {
        val roots = listOf(MockInJavaNestedOfCompanionObject::class)
        assertThrows<UnwantedStaticMockException> {
            testedExtension.scanForStaticMocksLeft(roots, PACKAGE_PREFIXES)
        }
    }

    @Test
    fun `M throw an error W mock in nested companion object of companion object`() {
        val roots = listOf(MockInCompanionInNestedOfCompanionObject::class)
        assertThrows<UnwantedStaticMockException> {
            testedExtension.scanForStaticMocksLeft(roots, PACKAGE_PREFIXES)
        }
    }

    @Test
    fun `M not throw an error W mock in a non-object class`() {
        val roots = listOf(MockInNonObjectClass::class)
        assertDoesNotThrow { testedExtension.scanForStaticMocksLeft(roots, PACKAGE_PREFIXES) }
    }

    @Test
    fun `M not throw an error W mock in a lateinit field`() {
        val roots = listOf(MockInObjectClassLateinitField::class)
        assertDoesNotThrow { testedExtension.scanForStaticMocksLeft(roots, PACKAGE_PREFIXES) }
    }

    @Test
    fun `M not throw error W object with synthetic field`() {
        val roots = listOf(ObjectWithLambdaMember::class)
        assertDoesNotThrow { testedExtension.scanForStaticMocksLeft(roots, PACKAGE_PREFIXES) }
    }

    // region fake classes for test

    @Suppress("unused")
    object MockInObjectClass {
        private const val CONST_VAL = 2022L
        private val NON_CONST_VAL = Any()
        internal val INTERNAL_NON_CONST_VAL = Any()

        val badMock: Any = mock()
    }

    open class ParentClassWithMock {
        @Suppress("unused")
        val badMock: Any = mock()
    }

    object MockInParentClass : ParentClassWithMock()

    class MockInCompanionObject {
        companion object {
            @Suppress("unused")
            val badMock: Any = mock()
        }
    }

    object MockInObjectClassLateinitField {
        @Suppress("unused", "UNNECESSARY_LATEINIT")
        lateinit var badMock: Any

        init {
            @Suppress("JoinDeclarationAndAssignment")
            badMock = mock()
        }
    }

    object MockInObjectClassPropertyWithCustomGetter {
        @Suppress("unused")
        val badMock: Any
            get() {
                return mock()
            }
    }

    object MockInObjectClassPropertyDelegate {
        @Suppress("unused")
        val badMock by lazy {
            mock<Any>()
        }
    }

    object MockInNestedObject {
        @Suppress("unused")
        val nested = MockInObjectClass
    }

    object MockInNestedJavaObject {
        @Suppress("unused")
        val nested = JavaClassWithStaticMock()
    }

    object MockInNestedCompanionObject {
        @Suppress("unused")
        val nested = MockInCompanionObject
    }

    class MockInNestedOfCompanionObject {
        companion object {
            @Suppress("unused")
            val nested = MockInObjectClass
        }
    }

    class MockInJavaNestedOfCompanionObject {
        companion object {
            @Suppress("unused")
            val nested =
                JavaClassWithStaticMock()
        }
    }

    class MockInCompanionInNestedOfCompanionObject {
        companion object {
            @Suppress("unused")
            val nested = MockInCompanionObject
        }
    }

    class MockInNonObjectClass(
        @Suppress("unused") val mock: Any = mock()
    )

    object ObjectWithLambdaMember {
        @Suppress("unused")
        val lambda = { println("foobar") }
    }

    companion object {
        val PACKAGE_PREFIXES = listOf("com.datadog")
    }

    // endregion
}
