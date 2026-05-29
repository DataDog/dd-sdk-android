/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk.rule.thirdparty

import com.datadog.tools.detekt.rules.sdk.rule.thirdparty.CodeParser.KtMethodParameter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class SignatureRuleTest {

    @Test
    fun `M match W matches() {exact signature}`() {
        val rule = "foo.Bar.baz(kotlin.String)".rule()
        assertThat(rule.matches("foo.Bar.baz(kotlin.String)")).isTrue()
    }

    @Test
    fun `M not match W matches() {different method name}`() {
        val rule = "foo.Bar.baz(kotlin.String)".rule()
        assertThat(rule.matches("foo.Bar.qux(kotlin.String)")).isFalse()
    }

    @Test
    fun `M not match W matches() {different param type}`() {
        val rule = "foo.Bar.baz(kotlin.String)".rule()
        assertThat(rule.matches("foo.Bar.baz(kotlin.Int)")).isFalse()
    }

    @Test
    fun `M match W matches() {nullable concrete param covers non-nullable call}`() {
        val rule = "foo.Bar.baz(kotlin.String?)".rule()
        assertThat(rule.matches("foo.Bar.baz(kotlin.String)")).isTrue()
    }

    @Test
    fun `M not match W matches() {non-nullable concrete param does not cover nullable call}`() {
        val rule = "foo.Bar.baz(kotlin.String)".rule()
        assertThat(rule.matches("foo.Bar.baz(kotlin.String?)")).isFalse()
    }

    @Test
    fun `M not match W matches() {different arity}`() {
        val rule = "foo.Bar.baz(kotlin.String)".rule()
        assertThat(rule.matches("foo.Bar.baz(kotlin.String, kotlin.Int)")).isFalse()
    }

    @Test
    fun `M match W matches() {NON_NULL_WILDCARD matches non-nullable type}`() {
        val rule = "foo.Bar.baz(*)".rule()
        assertThat(rule.matches("foo.Bar.baz(kotlin.String)")).isTrue()
    }

    @Test
    fun `M not match W matches() {NON_NULL_WILDCARD rejects nullable type}`() {
        val rule = "foo.Bar.baz(*)".rule()
        assertThat(rule.matches("foo.Bar.baz(kotlin.String?)")).isFalse()
    }

    @Test
    fun `M match W matches() {NULLABLE_WILDCARD matches non-nullable type}`() {
        val rule = "foo.Bar.baz(?)".rule()
        assertThat(rule.matches("foo.Bar.baz(kotlin.String)")).isTrue()
    }

    @Test
    fun `M match W matches() {NULLABLE_WILDCARD matches nullable type}`() {
        val rule = "foo.Bar.baz(?)".rule()
        assertThat(rule.matches("foo.Bar.baz(kotlin.String?)")).isTrue()
    }

    @Test
    fun `M match W matches() {no params}`() {
        val rule = "foo.Bar.baz()".rule()
        assertThat(rule.matches("foo.Bar.baz()")).isTrue()
    }

    @Test
    fun `M match W intersects() {identical exact signatures overlap}`() {
        val a = "foo.Bar.baz(kotlin.String)".rule()
        val b = "foo.Bar.baz(kotlin.String)".rule()
        assertThat(a.intersects(b)).isTrue()
    }

    @Test
    fun `M match W intersects() {nullable concrete param overlaps with non-nullable concrete param}`() {
        val a = "foo.Bar.baz(kotlin.String?)".rule()
        val b = "foo.Bar.baz(kotlin.String)".rule()
        assertThat(a.intersects(b)).isTrue()
    }

    @Test
    fun `M not match W intersects() {throwing nullable concrete param and non-nullable concrete param}`() {
        val a = "foo.Bar.baz(kotlin.String?)".throwingRule()
        val b = "foo.Bar.baz(kotlin.String)".rule()
        assertThat(a.intersects(b)).isFalse()
    }

    @Test
    fun `M not match W intersects() {different method names do not overlap}`() {
        val a = "foo.Bar.baz(kotlin.String)".rule()
        val b = "foo.Bar.qux(kotlin.String)".rule()
        assertThat(a.intersects(b)).isFalse()
    }

    @Test
    fun `M not match W intersects() {different arity does not overlap}`() {
        val a = "foo.Bar.baz(kotlin.String)".rule()
        val b = "foo.Bar.baz(kotlin.String, kotlin.Int)".rule()
        assertThat(a.intersects(b)).isFalse()
    }

    @Test
    fun `M match W intersects() {NON_NULL_WILDCARD overlaps with concrete non-nullable type}`() {
        val a = "foo.Bar.baz(*)".rule()
        val b = "foo.Bar.baz(kotlin.String)".rule()
        assertThat(a.intersects(b)).isTrue()
    }

    @Test
    fun `M not match W intersects() {NON_NULL_WILDCARD does not overlap with nullable concrete type}`() {
        val a = "foo.Bar.baz(*)".rule()
        val b = "foo.Bar.baz(kotlin.String?)".rule()
        assertThat(a.intersects(b)).isFalse()
    }

    @Test
    fun `M match W intersects() {NULLABLE_WILDCARD overlaps with nullable concrete type}`() {
        val a = "foo.Bar.baz(?)".rule()
        val b = "foo.Bar.baz(kotlin.String?)".rule()
        assertThat(a.intersects(b)).isTrue()
    }

    @Test
    fun `M match W intersects() {NULLABLE_WILDCARD overlaps with NON_NULL_WILDCARD}`() {
        val a = "foo.Bar.baz(?)".rule()
        val b = "foo.Bar.baz(*)".rule()
        assertThat(a.intersects(b)).isTrue()
    }

    @Test
    fun `M match W intersects() {NON_NULL_WILDCARD and NULLABLE_WILDCARD overlap with each other}`() {
        val a = "foo.Bar.baz(*, ?)".rule()
        val b = "foo.Bar.baz(*, *)".rule()
        assertThat(a.intersects(b)).isTrue()
    }

    @Test
    fun `M not throw W validate() {NON_NULL_WILDCARD targets generic param}`() {
        val rule = "foo.Bar.baz(*)".rule()
        val params = listOf(KtMethodParameter("t", "T", isGeneric = true))
        assertDoesNotThrow { rule.validate(params) }
    }

    @Test
    fun `M throw W validate() {NON_NULL_WILDCARD targets non-generic param}`() {
        val rule = "foo.Bar.baz(*)".rule()
        val params = listOf(KtMethodParameter("s", "kotlin.String", isGeneric = false))
        assertThrows<IllegalStateException> { rule.validate(params) }
    }

    @Test
    fun `M not throw W validate() {NULLABLE_WILDCARD targets Any param}`() {
        val rule = "foo.Bar.baz(?)".rule()
        val params = listOf(KtMethodParameter("value", "kotlin.Any?", isGeneric = false))
        assertDoesNotThrow { rule.validate(params) }
    }

    // Kotlin can add `EnhancedForWarnings` to Java types whose enhanced nullability is warning-only.
    // Detekt exposed this shape while analyzing `java.lang.reflect.Field.get(java.lang.Object)`.
    @Test
    fun `M not throw W validate() {NULLABLE_WILDCARD targets enhanced Any param}`() {
        val rule = "foo.Bar.baz(?)".rule()
        val params = listOf(KtMethodParameter("value", "[@EnhancedForWarnings(Any?)] (Any..Any?)", isGeneric = false))
        assertDoesNotThrow { rule.validate(params) }
    }

    @Test
    fun `M not throw W validate() {NON_NULL_WILDCARD targets java Object param}`() {
        val rule = "foo.Bar.baz(*)".rule()
        val params = listOf(KtMethodParameter("value", "java.lang.Object", isGeneric = false))
        assertDoesNotThrow { rule.validate(params) }
    }

    @Test
    fun `M return parsed reference W memberReference {qualified member}`() {
        // Given
        val rule = "foo.Bar.baz(kotlin.String, kotlin.Int)".rule()

        // Then
        assertThat(rule.memberReference).isEqualTo(
            SignatureRule.MemberReference(
                className = "foo.Bar",
                memberName = "baz",
                parameterCount = 2
            )
        )
    }

    @Test
    fun `M return parsed reference W memberReference {unqualified member}`() {
        // Given
        val rule = "baz()".rule()

        // Then
        assertThat(rule.memberReference).isEqualTo(
            SignatureRule.MemberReference(
                className = "",
                memberName = "baz",
                parameterCount = 0
            )
        )
    }

    companion object {
        private fun String.rule(exceptions: List<String> = emptyList()) =
            DetektConfigParser.parseEntry(this, exceptions)

        private fun String.throwingRule(exceptions: List<String> = emptyList()) =
            DetektConfigParser.parseEntry(
                source = this,
                exceptions = exceptions,
                nullableConcreteMatchesNonNullable = false
            )
    }
}
