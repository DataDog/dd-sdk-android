/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * A generic test class used to ensure a given type's implementation of `equals` and `hashCode`
 * respects the contract of those method.
 */
@Suppress("ReplaceCallWithBinaryOperator", "StringLiteralDuplication")
@ExtendWith(ForgeExtension::class)
abstract class ObjectTest<T : Any> {

    // region `equals` contract

    /**
     * `equals` must be reflexive.
     * x.equals(x) is always true
     */
    @Test
    fun equalsIsReflexive(forge: Forge) {
        // Given
        val x = createInstance(forge)

        // When
        val result = x.equals(x)

        // Then
        assertThat(result).isTrue()
    }

    /**
     * `equals` must be symmetric.
     * x.equals(y) == y.equals(x)
     */
    @Test
    fun equalsIsSymmetric(forge: Forge) {
        // Given
        val x = createInstance(forge)
        val y = createInstance(forge)
        assumeTrue(x !== y) { "Can't check equals transitivity: x:$x is same instance as y:$y" }

        // When
        val resultA = x.equals(y)
        val resultB = y.equals(x)

        // Then
        assertThat(resultA).isEqualTo(resultB)
    }

    /**
     * `equals` must be transitive.
     * if (x.equals(y) && y.equals(z)) then x.equals(z)
     */
    @Test
    fun equalsIsTransitive(forge: Forge) {
        // Given
        val x = createInstance(forge)
        val y = createEqualInstance(x, forge)
        val z = createEqualInstance(y, forge)
        assumeTrue(x !== y) { "Can't check equals transitivity: x:$x is same instance as y:$y" }
        assumeTrue(y !== z) { "Can't check equals transitivity: y:$y is same instance as z:$z" }
        assumeTrue(x !== z) { "Can't check equals transitivity: x:$x is same instance as z:$z" }
        assumeTrue(x.equals(y)) { "Can't check equals transitivity: x:$x isn't equal to y:$y" }
        assumeTrue(y.equals(z)) { "Can't check equals transitivity: y:$y isn't equal to z:$z" }

        // When
        val result = x.equals(z)

        // Then
        assertThat(result).isTrue()
    }

    /**
     * `equals` must be consistent.
     * if a (relevant) field is changed, equals should detect it
     */
    @Test
    fun equalsIsConsistent(forge: Forge) {
        // Given
        val x = createInstance(forge)
        val y = createUnequalInstance(x, forge) ?: return
        assumeTrue(x !== y) { "Can't check equals consistency: x:$x is same instance as y:$y" }

        // When
        val result = x.equals(y)

        // Then
        assertThat(result).isFalse()
    }

    /**
     * `equals` must be type sensitive.
     * if the two compared instances are of different types, equals is false
     */
    @Test
    fun equalsIsTypeSensitive(forge: Forge) {
        // Given
        val x = createInstance(forge)
        val y = Object()
        assumeTrue(x !== y) { "Can't check equals type sensitivity: x:$x is same instance as y:$y" }
        assumeTrue(x.javaClass != y.javaClass) {
            "Can't check equals transitivity: x:$x has same type as y:$y"
        }

        // When
        val result = x.equals(y)

        // Then
        assertThat(result).isFalse()
    }

    // endregion

    // region `hashCode` contract

    /**
     * `hashCode` must be consistent internaly.
     * if a (relevant) field is changed, hashCode may detect it (but collisions can happen)
     */
    @Test
    @Suppress("FunctionMaxLength")
    fun hashCodeIsInternallyConsistent(forge: Forge) {
        // Given
        val x = createInstance(forge)

        var testedHashCode = 0
        var differentHashCode = 0

        // When
        repeat(64) {
            val y = createUnequalInstance(x, forge)
            if (y != null && y !== x) {
                testedHashCode++
                val xHC = x.hashCode()
                val yHC = y.hashCode()

                if (xHC != yHC) {
                    differentHashCode++
                }
            }
        }

        // Then
        if (testedHashCode == 0) return
        assertThat(differentHashCode).isGreaterThan(testedHashCode / 2)
    }

    /**
     * `hashCode` must be consistent internaly.
     * if (x.equals(y)) then x.hashCode() == y.hashCode()
     */
    @Test
    @Suppress("FunctionMaxLength")
    fun hashCodeIsConsistentWithEqual(forge: Forge) {
        // Given
        val x = createInstance(forge)
        val y = createEqualInstance(x, forge)
        assumeTrue(x !== y) { "Can't check equals transitivity: x:$x is same instance as y:$y" }
        assumeTrue(x.equals(y)) { "Can't check equals transitivity: x:$x isn't equal to y:$y" }

        // When
        val xHC = x.hashCode()
        val yHC = y.hashCode()

        // Then
        assertThat(xHC).isEqualTo(yHC)
    }

    // endregion

    /**
     * Create a new "random" instance of [T].
     * @param forge a [Forge]
     * @return an instance of [T]
     */
    abstract fun createInstance(forge: Forge): T

    /**
     * Create a new instance of [T], expected to be equal to the source parameter.
     * @param source an instance of [T]
     * @param forge a [Forge]
     * @return an instance of [T] considered equal to the source
     */
    abstract fun createEqualInstance(source: T, forge: Forge): T

    /**
     * Create a new instance of [T], expected to be different from the source parameter.
     * @param source an instance of [T]
     * @param forge a [Forge]
     * @return an instance of [T] considered different from the source
     */
    abstract fun createUnequalInstance(source: T, forge: Forge): T?
}
