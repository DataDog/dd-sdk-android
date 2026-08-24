/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.apisurface

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class KotlinFileVisitorTest {

    @TempDir
    lateinit var tempDir: File
    lateinit var tempFile: File

    lateinit var testedVisitor: KotlinFileVisitor

    @BeforeEach
    fun `set up`() {
        tempFile = File(tempDir, FILE_NAME)
        testedVisitor = KotlinFileVisitor()
    }

    @Test
    fun `describes public class`() {
        tempFile.writeText(
            """
            package foo.bar
            class Spam {
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(testedVisitor.description.toString())
            .isEqualTo("class foo.bar.Spam\n")
    }

    @Test
    fun `describes public class in root package`() {
        tempFile.writeText(
            """
            class Spam {
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo("class Spam\n")
    }

    @Test
    fun `describes public object`() {
        tempFile.writeText(
            """
            package foo.bar
            object Spam {
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo("object foo.bar.Spam\n")
    }

    @Test
    fun `describes public interface`() {
        tempFile.writeText(
            """
            package foo.bar
            interface Spam {
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo("interface foo.bar.Spam\n")
    }

    @Test
    fun `describes public enums`() {
        tempFile.writeText(
            """
            package foo.bar
            enum class Spam {
                A, B, C;
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "enum foo.bar.Spam\n" +
                "  - A\n" +
                "  - B\n" +
                "  - C\n"
        )
    }

    @Test
    fun `describes public annotation`() {
        tempFile.writeText(
            """
            package foo.bar
            annotation class Spam(
                val A: String,
                val B: String,
                val C: String
            )
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "annotation foo.bar.Spam\n" +
                "  constructor(String, String, String)\n"
        )
    }

    @Test
    fun `describes public sealed class`() {
        tempFile.writeText(
            """
            package foo.bar
            sealed class Spam {
                class A : Spam()
                class B : Spam()
                class C : Spam()
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "sealed class foo.bar.Spam\n" +
                "  class A : Spam\n" +
                "  class B : Spam\n" +
                "  class C : Spam\n"
        )
    }

    @Test
    fun `ignores non public classes`() {
        tempFile.writeText(
            """
            package foo.bar
            class Spam {
                internal class C {}
                private class D {}
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo("class foo.bar.Spam\n")
    }

    @Test
    fun `ignores init block`() {
        tempFile.writeText(
            """
            package foo.bar
            class Spam {
                init {}
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo("class foo.bar.Spam\n")
    }

    @Test
    fun `ignores non public objects`() {
        tempFile.writeText(
            """
            package foo.bar
            object Spam {
                internal object C {}
                private object D {}
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo("object foo.bar.Spam\n")
    }

    @Test
    fun `ignores non public interfaces`() {
        tempFile.writeText(
            """
            package foo.bar
            interface Spam {
                internal interface C {}
                private interface D {}
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo("interface foo.bar.Spam\n")
    }

    @Test
    fun `ignores non public methods`() {
        tempFile.writeText(
            """
            package foo.bar
            class Spam {
                fun doSomething() {}
                internal fun youCantSeeThis() {}
                private fun youCantSeeThisEither() {}
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam\n" +
                "  fun doSomething()\n"
        )
    }

    @Test
    fun `ignores non public methods in interfaces`() {
        tempFile.writeText(
            """
            package foo.bar
            interface Spam {
                fun doSomething()
                internal fun youCantSeeThis()
                private fun youCantSeeThisEither()
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "interface foo.bar.Spam\n" +
                "  fun doSomething()\n"
        )
    }

    @Test
    fun `ignores non public properties`() {
        tempFile.writeText(
            """
            package foo.bar
            class Spam {
                val s : String = "FOO"
                internal val youCantSeeThis = 0
                private val youCantSeeThisEither = false
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam\n" +
                "  val s: String\n"
        )
    }

    @Test
    fun `ignores non public constructors`() {
        tempFile.writeText(
            """
            package foo.bar
            class Spam(i : Int) {
                private constructor(s : String) : this(s.length)
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam\n" +
                "  constructor(Int)\n"
        )
    }

    @Test
    fun `ignores non public default constructor`() {
        tempFile.writeText(
            """
            package foo.bar
            class Spam
                internal constructor(i : Int) {
                constructor(s : String) : this(s.length)
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam\n" +
                "  constructor(String)\n"
        )
    }

    @Test
    fun `describes abstract and open classes`() {
        tempFile.writeText(
            """
            package foo.bar
            class Spam {
                open class C {}
                abstract class D {}
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam\n" +
                "  open class C\n" +
                "  abstract class D\n"
        )
    }

    @Test
    fun `describes abstract and open methods`() {
        tempFile.writeText(
            """
            package foo.bar
            class Spam {
                open fun doSomething() : String = ""
                abstract fun doSomethingElse() {}
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam\n" +
                "  open fun doSomething(): String\n" +
                "  abstract fun doSomethingElse()\n"
        )
    }

    @Test
    fun `describes protected abstract and open methods`() {
        tempFile.writeText(
            """
            package foo.bar
            class Spam {
                protected open fun doSomething() {}
                protected abstract fun doSomethingElse() {}
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam\n" +
                "  protected open fun doSomething()\n" +
                "  protected abstract fun doSomethingElse()\n"
        )
    }

    @Test
    fun `describes abstract and open properties`() {
        tempFile.writeText(
            """
            package foo.bar
            class Spam {
                open val s:String = ""
                abstract var i : Int = 0
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam\n" +
                "  open val s: String\n" +
                "  abstract var i: Int\n"
        )
    }

    @Test
    fun `describes generics in class types`() {
        tempFile.writeText(
            """
            package foo.bar
            import java.io.IOException
            class Spam<T:IOException, R> {
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo("class foo.bar.Spam<T: java.io.IOException, R>\n")
    }

    @Test
    fun `describes generics in functions`() {
        tempFile.writeText(
            """
            package foo.bar
            import java.io.IOException
            class Spam {
                fun <T: IOException> doSomething(input: T) : IOException {
                    return IOException(input)
                }

                fun <K, V> doSomethingElse(map : Map<K, V>) : Pair<List<K>, List<V>> {
                    TODO()
                }
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam\n" +
                "  fun <T: java.io.IOException> doSomething(T): java.io.IOException\n" +
                "  fun <K, V> doSomethingElse(Map<K, V>): Pair<List<K>, List<V>>\n"
        )
    }

    @Test
    fun `describes wildcard generics in functions`() {
        tempFile.writeText(
            """
            package foo.bar
            import java.io.IOException
            class Spam {
                fun <K, V> doSomethingElse(map : Map<*, *>) : Pair<List<*>, List<*>> {
                    TODO()
                }
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam\n" +
                "  fun <K, V> doSomethingElse(Map<*, *>): Pair<List<*>, List<*>>\n"
        )
    }

    @Test
    fun `describes parent class and interfaces in class types`() {
        tempFile.writeText(
            """
            package foo.bar
            import java.io.IOException
            import java.lang.Runnable
            import java.util.Comparator
            class Spam:IOException, Runnable, Comparator<String> {
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam : java.io.IOException, java.lang.Runnable, java.util.Comparator<String>\n"
        )
    }

    @Test
    fun `describes nested parent class and interfaces in class types`() {
        tempFile.writeText(
            """
            package foo.bar
            class Spam : Bar.AbstractCallback(), Baz.Listener<String> {
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam : Bar.AbstractCallback, Baz.Listener<String>\n"
        )
    }

    @Test
    fun `describes function`() {
        tempFile.writeText(
            """
            package foo.bar
            class Spam {
                fun doSomething(i: Int, s: String?, l : List<String?>) {}
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam\n" +
                "  fun doSomething(Int, String?, List<String?>)\n"
        )
    }

    @Test
    fun `describes constructor with default parameters`() {
        tempFile.writeText(
            """
            package foo.bar
            class Spam (i: Int, s: String? = null, l : List<String> = emptyList()) {
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam\n" +
                "  constructor(Int, String? = null, List<String> = emptyList())\n"
        )
    }

    @Test
    fun `describes function with default parameters`() {
        tempFile.writeText(
            """
            package foo.bar
            class Spam {
                fun doSomething(i: Int, s: String? = null, l : List<String> = emptyList()) {}
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam\n" +
                "  fun doSomething(Int, String? = null, List<String> = emptyList())\n"
        )
    }

    @Test
    fun `describes overriden functions`() {
        tempFile.writeText(
            """
            package foo.bar
            class Spam : Runnable{
                override fun run ( ) { }
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam : Runnable\n" +
                "  override fun run()\n"
        )
    }

    @Test
    fun `describes function with lambda parameters`() {
        tempFile.writeText(
            """
            package foo.bar
            class Spam {
                fun withInputs(block : (String, Char) -> Int) {}
                fun unit(block : (String) -> Unit) {}
                fun nullable(block : (String) -> Any?) {}
                fun withReceiver(block : String.() -> Int) {}
                fun withNamedArgumentOfLambda(block : (name: String) -> Unit) {}
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam\n" +
                "  fun withInputs((String, Char) -> Int)\n" +
                "  fun unit((String) -> Unit)\n" +
                "  fun nullable((String) -> Any?)\n" +
                "  fun withReceiver(String.() -> Int)\n" +
                "  fun withNamedArgumentOfLambda((String) -> Unit)\n"
        )
    }

    @Test
    fun `describes constructor with lambda parameters`() {
        tempFile.writeText(
            """
            package foo.bar
            class Spam (block : (String, Char) -> Int) {
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam\n" +
                "  constructor((String, Char) -> Int)\n"
        )
    }

    @Test
    fun `describes object`() {
        tempFile.writeText(
            """
            package foo.bar
            object Global {
                const val DATA : String= "Something"
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "object foo.bar.Global\n" +
                "  const val DATA: String\n"
        )
    }

    @Test
    fun `describes companion object`() {
        tempFile.writeText(
            """
            package foo.bar
            class Spam {
                companion object {
                    const val DATA : String= "Something"
                }
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam\n" +
                "  companion object \n" +
                "    const val DATA: String\n"
        )
    }

    @Test
    fun `throw error on implicit type`() {
        tempFile.writeText(
            """
            package foo.bar
            class Spam {
                companion object {
                    const val DATA = "Something"
                }
            }
            """.trimIndent()
        )

        assertThrows<IllegalStateException> {
            testedVisitor.visitFile(tempFile)
        }
    }

    @Test
    fun `replace alias type with full name`() {
        tempFile.writeText(
            """
            package foo.bar
            import com.example.Data as FooData
            class Spam {
                fun doSomething() : FooData
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam\n" +
                "  fun doSomething(): com.example.Data\n"
        )
    }

    @Test
    fun `replace imported type with canonical name`() {
        tempFile.writeText(
            """
            package foo.bar
            import com.example.Data
            class Spam {
                fun doSomething() : Data
                fun doSomethingElse() : not.same.Data
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam\n" +
                "  fun doSomething(): com.example.Data\n" +
                "  fun doSomethingElse(): not.same.Data\n"
        )
    }

    @Test
    fun `describes function type alias`() {
        tempFile.writeText(
            """
            typealias StringTransform = (String) -> String?
            typealias StringRepeat = (String, Int) -> String
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "typealias StringTransform = (String) -> String?\n" +
                "typealias StringRepeat = (String, Int) -> String\n"
        )
    }

    @Test
    fun `describes class type alias`() {
        tempFile.writeText(
            """
            typealias PowerfulString = String
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo("typealias PowerfulString = String\n")
    }

    @Test
    fun `ignores non public type alias`() {
        tempFile.writeText(
            """
            internal typealias StringTransform = (String) -> String?
            private typealias IntTransform = (Int) -> Int
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEmpty()
    }

    @Test
    fun `describes deprecated class`() {
        tempFile.writeText(
            """
            package foo.bar
            @Deprecated
            class Spam {
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo("DEPRECATED class foo.bar.Spam\n")
    }

    @Test
    fun `describes deprecated function`() {
        tempFile.writeText(
            """
            package foo.bar
            import kotlin.Deprecated

            class Spam {
                @Deprecated
                fun doSomething() {}
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam\n" +
                "  DEPRECATED fun doSomething()\n"
        )
    }

    @Test
    fun `describes deprecated constructor`() {
        tempFile.writeText(
            """
            package foo.bar
            import kotlin.Deprecated

            class Spam(i : Int) {
                @Deprecated
                constructor(s : String) : this(s.length)
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam\n" +
                "  constructor(Int)\n" +
                "  DEPRECATED constructor(String)\n"
        )
    }

    @Test
    fun `describes deprecated field`() {
        tempFile.writeText(
            """
            package foo.bar
            import java.lang.Deprecated

            class Spam {
                @Deprecated
                val foo : String = ""
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.Spam\n" +
                "  DEPRECATED val foo: String\n"
        )
    }

    @Test
    fun `describes deprecated const`() {
        tempFile.writeText(
            """
            package foo.bar
            import java.lang.Deprecated

            object Spam {
                @Deprecated("Don't use anymore")
                const val foo : String = ""
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "object foo.bar.Spam\n" +
                "  DEPRECATED const val foo: String\n"
        )
    }

    @Test
    fun `describes identifiers with keywords field`() {
        tempFile.writeText(
            """
            package foo.bar.internal
            import java.lang.Deprecated

            class Spam {
                val data : String = ""
            }
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo(
            "class foo.bar.internal.Spam\n" +
                "  val data: String\n"
        )
    }

    @Test
    fun `describes extension functions`() {
        tempFile.writeText(
            """
            fun String.withFooBar(): String = this + "foobar"
            """.trimIndent()
        )

        testedVisitor.visitFile(tempFile)

        assertThat(
            testedVisitor.description.toString()
        ).isEqualTo("fun String.withFooBar(): String\n")
    }

    companion object {
        const val FILE_NAME = "file.kt"
    }
}
