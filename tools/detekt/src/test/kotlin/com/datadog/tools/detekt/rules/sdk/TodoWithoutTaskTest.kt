/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
class TodoWithoutTaskTest {

    lateinit var config: Config

    @BeforeEach
    fun `set up`() {
        config = TestConfig()
    }

    // region no task

    @Test
    fun `detekt todo without task number`(
        @StringForgery(regex = "[\\w ]+") commentText: String
    ) {
        val code =
            """
            class Foo {
                fun bar() {
                    // TODO $commentText
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt todo without task number in multiline comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String
    ) {
        val code =
            """
            class Foo {
                fun bar() {
                    /*
                     * TODO $commentText
                     */
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt todo without task number in property doc comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String
    ) {
        val code =
            """
            class Foo {
            
                /**
                 * Do Something.
                 * TODO $commentText
                 */
                lateinit var property : String 
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt todo without task number in method doc comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String
    ) {
        val code =
            """
            class Foo {
            
                /**
                 * Do Something.
                 * TODO $commentText
                 */
                fun bar() {
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt todo without task number in class doc comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String
    ) {
        val code =
            """
            /**
             * TODO $commentText
             */
            class Foo {
                fun bar() {
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt todo without task number in object doc comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String
    ) {
        val code =
            """
            /**
             * TODO $commentText
             */
            object Foo {
                fun bar() {
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    // endregion

    // region invalid task number

    @Test
    fun `detekt todo with invalid task number`(
        @StringForgery(regex = "[\\w ]+") commentText: String,
        @StringForgery(regex = "[A-Z]+-0{1,8}") task: String
    ) {
        val code =
            """
            class Foo {
                fun bar() {
                    // TODO $task $commentText
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt todo with invalid task number in multiline comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String,
        @StringForgery(regex = "[A-Z]+-0{1,8}") task: String
    ) {
        val code =
            """
            class Foo {
                fun bar() {
                    /*
                     * TODO $task $commentText
                     */
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt todo with invalid task number in property doc comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String,
        @StringForgery(regex = "[A-Z]+-0{1,8}") task: String
    ) {
        val code =
            """
            class Foo {
            
                /**
                 * Do Something.
                 * TODO $task $commentText
                 */
                lateinit var property : String 
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt todo with invalid task number in method doc comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String,
        @StringForgery(regex = "[A-Z]+-0{1,8}") task: String
    ) {
        val code =
            """
            class Foo {
            
                /**
                 * Do Something.
                 * TODO $task $commentText
                 */
                fun bar() {
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt todo with invalid task number in class doc comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String,
        @StringForgery(regex = "[A-Z]+-0{1,8}") task: String
    ) {
        val code =
            """
            /**
             * TODO $task $commentText
             */
            class Foo {
                fun bar() {
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt todo with invalid task number in object doc comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String,
        @StringForgery(regex = "[A-Z]+-²") task: String
    ) {
        val code =
            """
            /**
             * TODO $task $commentText
             */
            object Foo {
                fun bar() {
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    // endregion

    // region invalid task number

    @Test
    fun `detekt todo with obsolete project prefix`(
        @StringForgery(regex = "[\\w ]+") commentText: String,
        @StringForgery(regex = "[A-Z]+") deprecatedProject: String,
        @IntForgery(min = 1) taskNumber: Int
    ) {
        config = TestConfig(mapOf("deprecatedPrefixes" to listOf("FOOPROJECT", deprecatedProject, "BARPROJECT")))
        val code =
            """
            class Foo {
                fun bar() {
                    // TODO $deprecatedProject-$taskNumber $commentText
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt todo with obsolete project prefix in multiline comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String,
        @StringForgery(regex = "[A-Z]+") deprecatedProject: String,
        @IntForgery(min = 1) taskNumber: Int
    ) {
        config = TestConfig(mapOf("deprecatedPrefixes" to listOf("FOOPROJECT", deprecatedProject, "BARPROJECT")))
        val code =
            """
            class Foo {
                fun bar() {
                    /*
                     * TODO $deprecatedProject-$taskNumber $commentText
                     */
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt todo with obsolete project prefix in property doc comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String,
        @StringForgery(regex = "[A-Z]+") deprecatedProject: String,
        @IntForgery(min = 1) taskNumber: Int
    ) {
        config = TestConfig(mapOf("deprecatedPrefixes" to listOf("FOOPROJECT", deprecatedProject, "BARPROJECT")))
        val code =
            """
            class Foo {
            
                /**
                 * Do Something.
                 * TODO $deprecatedProject-$taskNumber $commentText
                 */
                lateinit var property : String 
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt todo with obsolete project prefix in method doc comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String,
        @StringForgery(regex = "[A-Z]+") deprecatedProject: String,
        @IntForgery(min = 1) taskNumber: Int
    ) {
        config = TestConfig(mapOf("deprecatedPrefixes" to listOf("FOOPROJECT", deprecatedProject, "BARPROJECT")))
        val code = """
            class Foo {
            
                /**
                 * Do Something.
                 * TODO $deprecatedProject-$taskNumber $commentText
                 */
                fun bar() {
                }
            }
        """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt todo with obsolete project prefix in class doc comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String,
        @StringForgery(regex = "[A-Z]+") deprecatedProject: String,
        @IntForgery(min = 1) taskNumber: Int
    ) {
        config = TestConfig(mapOf("deprecatedPrefixes" to listOf("FOOPROJECT", deprecatedProject, "BARPROJECT")))
        val code = """
            /**
             * TODO $deprecatedProject-$taskNumber $commentText
             */
            class Foo {
                fun bar() {
                }
            }
        """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt todo with obsolete project prefix in object doc comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String,
        @StringForgery(regex = "[A-Z]+") deprecatedProject: String,
        @IntForgery(min = 1) taskNumber: Int
    ) {
        config = TestConfig(mapOf("deprecatedPrefixes" to listOf("FOOPROJECT", deprecatedProject, "BARPROJECT")))
        val code =
            """
            /**
             * TODO $deprecatedProject-$taskNumber $commentText
             */
            object Foo {
                fun bar() {
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(1)
    }

    // endregion

    // region valid task number

    @Test
    fun `detekt todo with valid task number`(
        @StringForgery(regex = "[\\w ]+") commentText: String,
        @StringForgery(regex = "[A-Z]+-\\d{1,8}") task: String
    ) {
        val code = """
            class Foo {
                fun bar() {
                    // TODO $task $commentText
                }
            }
        """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `detekt todo with valid task number in multiline comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String,
        @StringForgery(regex = "[A-Z]+-\\d{1,8}") task: String
    ) {
        val code =
            """
            class Foo {
                fun bar() {
                    /*
                     * TODO $task $commentText
                     */
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `detekt todo with valid task number in property doc comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String,
        @StringForgery(regex = "[A-Z]+-\\d{1,8}") task: String
    ) {
        val code =
            """
            class Foo {
            
                /**
                 * Do Something.
                 * TODO $task $commentText
                 */
                lateinit var property : String 
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `detekt todo with valid task number in method doc comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String,
        @StringForgery(regex = "[A-Z]+-\\d{1,8}") task: String
    ) {
        val code =
            """
            class Foo {
            
                /**
                 * Do Something.
                 * TODO $task $commentText
                 */
                fun bar() {
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `detekt todo with valid task number in class doc comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String,
        @StringForgery(regex = "[A-Z]+-\\d{1,8}") task: String
    ) {
        val code =
            """
            /**
             * TODO $task $commentText
             */
            class Foo {
                fun bar() {
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `detekt todo with valid task number in object doc comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String,
        @StringForgery(regex = "[A-Z]+-\\d{1,8}") task: String
    ) {
        val code =
            """
            /**
             * TODO $task $commentText
             */
            object Foo {
                fun bar() {
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(0)
    }

    // endregion

    // region no todo

    @Test
    fun `detekt no todo`(
        @StringForgery(regex = "[\\w ]+") commentText: String
    ) {
        val code =
            """
            class Foo {
                fun bar() {
                    // $commentText
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `detekt no todo in multiline comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String
    ) {
        val code =
            """
            class Foo {
                fun bar() {
                    /*
                     * $commentText
                     */
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `detekt no todo in property doc comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String
    ) {
        val code =
            """
            class Foo {
            
                /**
                 * Do Something.
                 *  $commentText
                 */
                lateinit var property : String 
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `detekt no todo in method doc comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String
    ) {
        val code =
            """
            class Foo {
            
                /**
                 * Do Something.
                 * $commentText
                 */
                fun bar() {
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `detekt no todo in class doc comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String
    ) {
        val code =
            """
            /**
             * $commentText
             */
            class Foo {
                fun bar() {
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `detekt no todo in object doc comment`(
        @StringForgery(regex = "[\\w ]+") commentText: String
    ) {
        val code =
            """
            /**
             * $commentText
             */
            object Foo {
                fun bar() {
                }
            }
            """.trimIndent()

        val findings = TodoWithoutTask(config).lint(code)
        assertThat(findings).hasSize(0)
    }

    // endregion
}
