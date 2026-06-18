/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import io.github.detekt.test.utils.KotlinCoreEnvironmentWrapper
import io.github.detekt.test.utils.createEnvironment
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class HotMethodIllegalCallTest {

    private lateinit var kotlinEnv: KotlinCoreEnvironmentWrapper

    @BeforeEach
    fun setup() {
        kotlinEnv = createEnvironment()
    }

    @AfterEach
    fun tearDown() {
        kotlinEnv.dispose()
    }

    // region findings — O(N) linear search

    @Test
    fun `M report error W find inside HotMethod`() {
        assertCallFindings(1, "list.find { it == \"x\" }", "kotlin.collections.List.find")
    }

    @Test
    fun `M report error W indexOf inside HotMethod`() {
        assertCallFindings(1, "list.indexOf(\"x\")", "kotlin.collections.List.indexOf")
    }

    @Test
    fun `M report error W indexOfFirst inside HotMethod`() {
        assertCallFindings(1, "list.indexOfFirst { it == \"x\" }", "kotlin.collections.List.indexOfFirst")
    }

    @Test
    fun `M report error W any inside HotMethod`() {
        assertCallFindings(1, "list.any { it == \"x\" }", "kotlin.collections.List.any")
    }

    @Test
    fun `M report error W count inside HotMethod`() {
        assertCallFindings(1, "list.count { it == \"x\" }", "kotlin.collections.List.count")
    }

    // endregion

    // region findings — O(N) full iteration

    @Test
    fun `M report error W forEach inside HotMethod`() {
        assertCallFindings(1, "list.forEach { println(it) }", "kotlin.collections.List.forEach")
    }

    @Test
    fun `M report error W forEachIndexed inside HotMethod`() {
        assertCallFindings(1, "list.forEachIndexed { _, v -> println(v) }", "kotlin.collections.List.forEachIndexed")
    }

    @Test
    fun `M report error W fold inside HotMethod`() {
        assertCallFindings(1, "list.fold(\"\") { acc, _ -> acc }", "kotlin.collections.List.fold")
    }

    @Test
    fun `M report error W reduce inside HotMethod`() {
        assertCallFindings(1, "list.reduce { acc, _ -> acc }", "kotlin.collections.List.reduce")
    }

    @Test
    fun `M report error W sumOf inside HotMethod`() {
        assertCallFindings(1, "list.sumOf { it.length }", "kotlin.collections.List.sumOf")
    }

    @Test
    fun `M report error W maxByOrNull inside HotMethod`() {
        assertCallFindings(1, "list.maxByOrNull { it.length }", "kotlin.collections.List.maxByOrNull")
    }

    // endregion

    // region findings — materialising

    @Test
    fun `M report error W filter inside HotMethod`() {
        assertCallFindings(1, "list.filter { it.isNotEmpty() }", "kotlin.collections.List.filter")
    }

    @Test
    fun `M report error W map inside HotMethod`() {
        assertCallFindings(1, "list.map { it.uppercase() }", "kotlin.collections.List.map")
    }

    @Test
    fun `M report error W flatMap inside HotMethod`() {
        assertCallFindings(1, "list.flatMap { emptyList<String>() }", "kotlin.collections.List.flatMap")
    }

    @Test
    fun `M report error W toList inside HotMethod`() {
        assertCallFindings(1, "list.toList()", "kotlin.collections.List.toList")
    }

    @Test
    fun `M report error W toSet inside HotMethod`() {
        assertCallFindings(1, "list.toSet()", "kotlin.collections.List.toSet")
    }

    @Test
    fun `M report error W groupBy inside HotMethod`() {
        assertCallFindings(1, "list.groupBy { it.first() }", "kotlin.collections.List.groupBy")
    }

    @Test
    fun `M report error W partition inside HotMethod`() {
        assertCallFindings(1, "list.partition { it.isNotEmpty() }", "kotlin.collections.List.partition")
    }

    // endregion

    // region findings — sorting

    @Test
    fun `M report error W sorted inside HotMethod`() {
        assertCallFindings(1, "list.sorted()", "kotlin.collections.List.sorted")
    }

    @Test
    fun `M report error W sortedBy inside HotMethod`() {
        assertCallFindings(1, "list.sortedBy { it.length }", "kotlin.collections.List.sortedBy")
    }

    @Test
    fun `M report error W sortedWith inside HotMethod`() {
        assertCallFindings(1, "list.sortedWith(compareBy { it })", "kotlin.collections.List.sortedWith")
    }

    // endregion

    // region findings — factory functions

    @Test
    fun `M report error W mutableListOf inside HotMethod`() {
        assertFactoryFindings(1, "val x = mutableListOf<String>()", "kotlin.collections.mutableListOf")
    }

    @Test
    fun `M report error W listOf inside HotMethod`() {
        assertFactoryFindings(1, "val x = listOf<String>()", "kotlin.collections.listOf")
    }

    @Test
    fun `M report error W mutableSetOf inside HotMethod`() {
        assertFactoryFindings(1, "val x = mutableSetOf<String>()", "kotlin.collections.mutableSetOf")
    }

    @Test
    fun `M report error W mutableMapOf inside HotMethod`() {
        assertFactoryFindings(1, "val x = mutableMapOf<String, String>()", "kotlin.collections.mutableMapOf")
    }

    @Test
    fun `M report error W hashMapOf inside HotMethod`() {
        assertFactoryFindings(1, "val x = hashMapOf<String, String>()", "kotlin.collections.hashMapOf")
    }

    @Test
    fun `M report error W buildString inside HotMethod`() {
        assertFactoryFindings(1, "val x = buildString { append(\"x\") }", "kotlin.text.buildString")
    }

    @Test
    fun `M report error W buildList inside HotMethod`() {
        assertFactoryFindings(1, "val x = buildList<String> { add(\"x\") }", "kotlin.collections.buildList")
    }

    @Test
    fun `M report error W buildMap inside HotMethod`() {
        assertFactoryFindings(1, "val x = buildMap<String, Int> { put(\"x\", 1) }", "kotlin.collections.buildMap")
    }

    // endregion

    // region findings — lambda and string allocation

    @Test
    fun `M report error W lambda literal inside HotMethod`() {
        val code = hotMethodCode("val f: () -> Unit = { println(\"x\") }")
        assertThat(HotMethodIllegalCall().compileAndLintWithContext(kotlinEnv.env, code)).hasSize(1)
    }

    @Test
    fun `M report error W trailing lambda to non-inline function inside HotMethod`() {
        val code = """
            annotation class HotMethod(val message: String)
            fun nonInlineReceiver(block: () -> Unit) { block() }
            class Foo {
                @HotMethod(message = "hot")
                fun hotFoo() {
                    nonInlineReceiver { println("x") }
                }
            }
        """.trimIndent()
        assertThat(HotMethodIllegalCall().compileAndLintWithContext(kotlinEnv.env, code)).hasSize(1)
    }

    @Test
    fun `M not report W lambda passed to allowedInlineFunction`() {
        val code = hotMethodCode("val x = \"hello\".let { it.length }")
        val rule = HotMethodIllegalCall(TestConfig("allowedInlineFunctions" to listOf("let")))
        assertThat(rule.compileAndLintWithContext(kotlinEnv.env, code)).isEmpty()
    }

    @Test
    fun `M not report W lambda outside HotMethod`() {
        val code = hotMethodCode("val f: () -> Unit = { println(\"x\") }", annotated = false)
        assertThat(HotMethodIllegalCall().compileAndLintWithContext(kotlinEnv.env, code)).isEmpty()
    }

    @Test
    fun `M report error W string template with interpolation inside HotMethod`() {
        val code = hotMethodCode("val tag = \"value=\$list\"")
        assertThat(HotMethodIllegalCall().compileAndLintWithContext(kotlinEnv.env, code)).hasSize(1)
    }

    @Test
    fun `M not report W plain string literal inside HotMethod`() {
        val code = hotMethodCode("val tag = \"plain literal\"")
        assertThat(HotMethodIllegalCall().compileAndLintWithContext(kotlinEnv.env, code)).isEmpty()
    }

    @Test
    fun `M not report W string template with interpolation outside HotMethod`() {
        val code = hotMethodCode("val tag = \"value=\$list\"", annotated = false)
        assertThat(HotMethodIllegalCall().compileAndLintWithContext(kotlinEnv.env, code)).isEmpty()
    }

    // endregion

    // region findings — object allocation

    @Test
    fun `M report error W constructor call inside HotMethod`() {
        val code = hotMethodCode("val sb = StringBuilder()")
        assertThat(HotMethodIllegalCall().compileAndLintWithContext(kotlinEnv.env, code)).hasSize(1)
    }

    @Test
    fun `M report error W anonymous object inside HotMethod`() {
        val code = """
            annotation class HotMethod(val message: String)
            interface Task { fun run() }
            class Foo {
                @HotMethod(message = "hot")
                fun hotFoo() {
                    val t = object : Task { override fun run() {} }
                }
            }
        """.trimIndent()
        assertThat(HotMethodIllegalCall().compileAndLintWithContext(kotlinEnv.env, code)).hasSize(1)
    }

    // endregion

    // region class-qualified precision — custom class with same name must NOT be flagged

    @Test
    fun `M not report O(N) W custom class method with same name as forbidden call`() {
        // The O(N) checks are class-qualified and must NOT match MyDomainQuery.
        // However, lambda arguments to non-inline functions DO still allocate and ARE flagged.
        val code = """
            annotation class HotMethod(val message: String)

            class MyDomainQuery {
                fun findByKey(key: String): String? = null
                fun processAll() {}
            }

            class Foo(private val query: MyDomainQuery) {
                @HotMethod(message = "hot")
                fun hotFoo() {
                    query.findByKey("x")
                    query.processAll()
                }
            }
        """.trimIndent()
        val rule = HotMethodIllegalCall(
            TestConfig(
                "forbiddenCalls" to listOf(
                    "kotlin.collections.List.find",
                    "kotlin.collections.List.forEach",
                    "kotlin.collections.List.filter"
                )
            )
        )
        assertThat(rule.compileAndLintWithContext(kotlinEnv.env, code)).isEmpty()
    }

    // endregion

    // region no findings — context and scope

    @Test
    fun `M not report W forbidden call outside HotMethod`() {
        val code = hotMethodCode("list.find { it == \"x\" }", annotated = false)
        val rule = HotMethodIllegalCall(TestConfig("forbiddenCalls" to listOf("kotlin.collections.List.find")))
        assertThat(rule.compileAndLintWithContext(kotlinEnv.env, code)).isEmpty()
    }

    @Test
    fun `M not report W index-based for-loop inside HotMethod`() {
        val code = hotMethodCode("for (i in list.indices) println(list[i])")
        assertThat(HotMethodIllegalCall().compileAndLintWithContext(kotlinEnv.env, code)).isEmpty()
    }

    @Test
    fun `M not report W local named function inside HotMethod uses forbidden call`() {
        val code = hotMethodCode("fun localHelper() { list.find { it == \"x\" } }\nlocalHelper()")
        val rule = HotMethodIllegalCall(
            TestConfig(
                "forbiddenCalls" to listOf("kotlin.collections.List.find"),
                "allowedInlineFunctions" to listOf("find")
            )
        )
        assertThat(rule.compileAndLintWithContext(kotlinEnv.env, code)).isEmpty()
    }

    @Test
    fun `M not report W constructor call outside HotMethod`() {
        val code = hotMethodCode("val sb = StringBuilder()", annotated = false)
        assertThat(HotMethodIllegalCall().compileAndLintWithContext(kotlinEnv.env, code)).isEmpty()
    }

    // endregion

    // region helpers

    private fun assertCallFindings(expected: Int, callCode: String, forbiddenCall: String) {
        // The stdlib functions in forbiddenCalls are inline — their lambdas do not allocate.
        // Add the method name to allowedInlineFunctions so the lambda is not also flagged.
        val method = forbiddenCall.substringAfterLast('.')
        val rule = HotMethodIllegalCall(
            TestConfig(
                "forbiddenCalls" to listOf(forbiddenCall),
                "allowedInlineFunctions" to listOf(method)
            )
        )
        val findings = rule.compileAndLintWithContext(kotlinEnv.env, hotMethodCode(callCode))
        assertThat(findings).hasSize(expected)
    }

    private fun assertFactoryFindings(expected: Int, callCode: String, forbiddenFactory: String) {
        // Factory functions like buildString/buildList are inline — their lambdas do not allocate.
        val method = forbiddenFactory.substringAfterLast('.')
        val rule = HotMethodIllegalCall(
            TestConfig(
                "forbiddenFactoryFunctions" to listOf(forbiddenFactory),
                "allowedInlineFunctions" to listOf(method)
            )
        )
        val findings = rule.compileAndLintWithContext(kotlinEnv.env, hotMethodCode(callCode))
        assertThat(findings).hasSize(expected)
    }

    private fun hotMethodCode(callCode: String, annotated: Boolean = true): String {
        val annotation = if (annotated) "@HotMethod(message = \"hot\")" else ""
        return """
            annotation class HotMethod(val message: String)
            class Foo(private val list: List<String>) {
                $annotation
                fun hotFoo() {
                    $callCode
                }
            }
        """.trimIndent()
    }

    // endregion
}
