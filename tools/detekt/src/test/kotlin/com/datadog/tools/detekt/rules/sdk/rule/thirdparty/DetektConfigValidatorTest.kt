/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk.rule.thirdparty

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class DetektConfigValidatorTest {

    private val parser = DetektConfigParser()
    private val testedValidator = DetektConfigValidator()

    @Test
    fun `M throw exception W validate() {known throwing literal is listed as safe}`() {
        assertThrows<IllegalStateException> {
            validate(
                safeCalls = listOf("kotlin.Array.first(kotlin.Function1)"),
                throwingCalls = listOf("kotlin.Array.first(kotlin.Function1):java.util.NoSuchElementException")
            )
        }
    }

    @Test
    fun `M throw exception W validate() {known throwing call is covered by safe wildcard}`() {
        assertThrows<IllegalStateException> {
            validate(
                safeCalls = listOf("kotlin.Array.first(?)"),
                throwingCalls = listOf("kotlin.Array.first(kotlin.Function1):java.util.NoSuchElementException")
            )
        }
    }

    @Test
    fun `M throw exception W validate() {safe call resolves to method declaring checked exception}`() {
        assertThrows<IllegalStateException> {
            validate(
                safeCalls = listOf("java.io.BufferedWriter.write(kotlin.String)")
            )
        }
    }

    @Test
    fun `M throw exception W validate() {safe call resolves to no-arg method declaring checked exception}`() {
        assertThrows<IllegalStateException> {
            validate(
                safeCalls = listOf("java.io.InputStream.read()")
            )
        }
    }

    @Test
    fun `M throw exception W validate() {safe call resolves to constructor declaring checked exception}`() {
        assertThrows<IllegalStateException> {
            validate(
                safeCalls = listOf("java.io.RandomAccessFile.constructor(kotlin.String, kotlin.String)")
            )
        }
    }

    @Test
    fun `M not throw W validate() {safe call resolves to overload set with a non-throwing member}`() {
        validate(
            safeCalls = listOf("java.io.FileInputStream.constructor(kotlin.String)")
        )
    }

    @Test
    fun `M not throw W validate() {safe call resolves to method with only unchecked exceptions}`() {
        validate(
            safeCalls = listOf("java.lang.StringBuilder.append(kotlin.String?)")
        )
    }

    @Test
    fun `M not throw W validate() {safe call declaring class is not on the classpath}`() {
        validate(
            safeCalls = listOf("com.example.unknown.Mystery.doStuff(kotlin.String)")
        )
    }

    @Test
    fun `M throw exception W validate() {safe wildcard overlaps with throwing literal}`() {
        assertThrows<IllegalStateException> {
            validate(
                safeCalls = listOf("kotlin.collections.MutableList.add(*)"),
                throwingCalls = listOf(
                    "kotlin.collections.MutableList.add(kotlin.String):java.lang.UnsupportedOperationException"
                )
            )
        }
    }

    @Test
    fun `M throw exception W validate() {safe literal overlaps with throwing wildcard}`() {
        assertThrows<IllegalStateException> {
            validate(
                safeCalls = listOf("kotlin.collections.MutableList.add(kotlin.String)"),
                throwingCalls = listOf(
                    "kotlin.collections.MutableList.add(*):java.lang.UnsupportedOperationException"
                )
            )
        }
    }

    @Test
    fun `M throw exception W validate() {safe and throwing wildcards have same shape}`() {
        assertThrows<IllegalStateException> {
            validate(
                safeCalls = listOf("kotlin.collections.MutableMap.put(*, ?)"),
                throwingCalls = listOf(
                    "kotlin.collections.MutableMap.put(*, ?):java.lang.UnsupportedOperationException"
                )
            )
        }
    }

    @Test
    fun `M throw exception W validate() {safe non-nullable wildcard overlaps with throwing any-type wildcard}`() {
        assertThrows<IllegalStateException> {
            validate(
                safeCalls = listOf("kotlin.collections.MutableList.add(*)"),
                throwingCalls = listOf(
                    "kotlin.collections.MutableList.add(?):java.lang.UnsupportedOperationException"
                )
            )
        }
    }

    @Test
    fun `M not throw W validate() {safe and throwing patterns target different methods}`() {
        validate(
            safeCalls = listOf("kotlin.collections.MutableList.add(*)"),
            throwingCalls = listOf(
                "kotlin.collections.MutableList.remove(*):java.lang.UnsupportedOperationException"
            )
        )
    }

    @Test
    fun `M throw exception W validate() {safe any-type wildcard overlaps with throwing non-nullable literal}`() {
        assertThrows<IllegalStateException> {
            validate(
                safeCalls = listOf("kotlin.collections.MutableList.add(?)"),
                throwingCalls = listOf(
                    "kotlin.collections.MutableList.add(kotlin.String):java.lang.UnsupportedOperationException"
                )
            )
        }
    }

    @Test
    fun `M throw exception W validate() {same literal entry listed twice in safe list}`() {
        assertThrows<IllegalStateException> {
            validate(
                safeCalls = listOf(
                    "kotlin.collections.MutableList.add(kotlin.String)",
                    "kotlin.collections.MutableList.add(kotlin.String)"
                )
            )
        }
    }

    @Test
    fun `M throw exception W validate() {duplicate entry with trailing whitespace in param}`() {
        assertThrows<IllegalStateException> {
            validate(
                safeCalls = listOf(
                    "kotlin.collections.MutableList.add(kotlin.String )",
                    "kotlin.collections.MutableList.add(kotlin.String)"
                )
            )
        }
    }

    @Test
    fun `M throw exception W validate() {duplicate entry with no space after comma}`() {
        assertThrows<IllegalStateException> {
            validate(
                safeCalls = listOf(
                    "kotlin.collections.MutableList.add(kotlin.Int,kotlin.String)",
                    "kotlin.collections.MutableList.add(kotlin.Int, kotlin.String)"
                )
            )
        }
    }

    @Test
    fun `M throw exception W validate() {wildcard subsumes literal in same safe list}`() {
        assertThrows<IllegalStateException> {
            validate(
                safeCalls = listOf(
                    "kotlin.collections.MutableList.add(*)",
                    "kotlin.collections.MutableList.add(kotlin.String)"
                )
            )
        }
    }

    @Test
    fun `M throw exception W validate() {safe nullable concrete entry subsumes non-nullable entry}`() {
        assertThrows<IllegalStateException> {
            validate(
                safeCalls = listOf(
                    "foo.Bar.baz(kotlin.String)",
                    "foo.Bar.baz(kotlin.String?)"
                )
            )
        }
    }

    @Test
    fun `M not throw W validate() {throwing nullable concrete entry does not subsume non-nullable entry}`() {
        validate(
            throwingCalls = listOf(
                "foo.Bar.baz(kotlin.String):java.lang.IllegalArgumentException",
                "foo.Bar.baz(kotlin.String?):java.lang.IllegalArgumentException"
            )
        )
    }

    @Test
    fun `M throw exception W validate() {wildcard subsumes literal in same throwing list}`() {
        assertThrows<IllegalStateException> {
            validate(
                throwingCalls = listOf(
                    "kotlin.collections.MutableList.add(*):java.lang.UnsupportedOperationException",
                    "kotlin.collections.MutableList.add(kotlin.String):java.lang.UnsupportedOperationException"
                )
            )
        }
    }

    @Test
    fun `M throw exception W validate() {safe calls for same class are not sorted}`() {
        assertThrows<IllegalStateException> {
            validate(
                safeCalls = listOf(
                    "kotlin.collections.MutableList.remove(kotlin.Int)",
                    "kotlin.collections.MutableList.add(kotlin.String)"
                )
            )
        }
    }

    @Test
    fun `M not throw W validate() {safe calls for same class are sorted}`() {
        validate(
            safeCalls = listOf(
                "kotlin.collections.MutableList.add(kotlin.String)",
                "kotlin.collections.MutableList.remove(kotlin.Int)"
            )
        )
    }

    @Test
    fun `M not throw W validate() {safe calls from different classes are in any order}`() {
        validate(
            safeCalls = listOf(
                "kotlin.collections.MutableSet.add(kotlin.String)",
                "kotlin.collections.MutableList.add(kotlin.String)"
            )
        )
    }

    private fun validate(
        safeCalls: List<String> = emptyList(),
        throwingCalls: List<String> = emptyList()
    ) {
        testedValidator.validate(
            knownSafeCalls = parser.parseSafeCalls(safeCalls),
            knownThrowingCalls = parser.parseThrowingCalls(throwingCalls)
        )
    }
}
