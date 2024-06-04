/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.rules.sdk

import android.webkit.JavascriptInterface
import androidx.annotation.MainThread
import io.github.detekt.test.utils.KotlinCoreEnvironmentWrapper
import io.github.detekt.test.utils.createEnvironment
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class ThreadSafetyTest {

    lateinit var kotlinEnv: KotlinCoreEnvironmentWrapper

    lateinit var fakeConfig: Config

    @BeforeEach
    fun setup() {
        fakeConfig = TestConfig()
        kotlinEnv = createEnvironment(
            // by some reason all Android-related classes are not discovered by DetektKt compiler,
            // so need to add them explicitly
            additionalRootPaths = listOf(
                MainThread::class,
                // alternatively we could get it by reading sdk.dir property and pulling android.jar
                // for the right API, but since we need only a single annotation symbol, let's just
                // import robolectric instead
                JavascriptInterface::class
            ).map {
                File(it.java.protectionDomain.codeSource.location.path)
            }
        )
    }

    @AfterEach
    fun tearDown() {
        kotlinEnv.dispose()
    }

    @Test
    fun `detekt call from javascript thread to javascript interface`() {
        val code =
            """
            import android.webkit.JavascriptInterface

            class Foo {
                @JavascriptInterface
                fun callee() {}

                @JavascriptInterface
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt call from worker thread to javascript interface`() {
        val code =
            """
            import android.webkit.JavascriptInterface
            import androidx.annotation.WorkerThread

            class Foo {
                @JavascriptInterface
                fun callee() {}

                @WorkerThread
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt call from main thread to javascript interface`() {
        val code =
            """
            import android.webkit.JavascriptInterface
            import androidx.annotation.MainThread

            class Foo {
                @JavascriptInterface
                fun callee() {}

                @MainThread
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt call from UI thread to javascript interface`() {
        val code =
            """
            import android.webkit.JavascriptInterface
            import androidx.annotation.UiThread

            class Foo {
                @JavascriptInterface
                fun callee() {}

                @UiThread
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt call from any thread to javascript interface`() {
        val code =
            """
            import android.webkit.JavascriptInterface
            import androidx.annotation.AnyThread

            class Foo {
                @JavascriptInterface
                fun callee() {}

                @AnyThread
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt call from unknown thread to javascript interface`() {
        val code =
            """
            import android.webkit.JavascriptInterface

            class Foo {
                @JavascriptInterface
                fun callee() {}

                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `ignore call from javascript thread to worker thread`() {
        val code =
            """
            import android.webkit.JavascriptInterface
            import androidx.annotation.WorkerThread

            class Foo {
                @WorkerThread
                fun callee() {}

                @JavascriptInterface
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignore call from worker thread to worker thread`() {
        val code =
            """
            import androidx.annotation.WorkerThread

            class Foo {
                @WorkerThread
                fun callee() {}

                @WorkerThread
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `detekt call from main thread to worker thread`() {
        val code =
            """
            import androidx.annotation.MainThread
            import androidx.annotation.WorkerThread

            class Foo {
                @WorkerThread
                fun callee() {}

                @MainThread
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt call from UI thread to worker thread`() {
        val code =
            """
            import androidx.annotation.UiThread
            import androidx.annotation.WorkerThread

            class Foo {
                @WorkerThread
                fun callee() {}

                @UiThread
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt call from any thread to worker thread`() {
        val code =
            """
            import androidx.annotation.AnyThread
            import androidx.annotation.WorkerThread

            class Foo {
                @WorkerThread
                fun callee() {}

                @AnyThread
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt call from unknown thread to worker thread`() {
        val code =
            """
            import androidx.annotation.WorkerThread

            class Foo {
                @WorkerThread
                fun callee() {}

                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt call from javascript thread to UI thread`() {
        val code =
            """
            import android.webkit.JavascriptInterface
            import androidx.annotation.UiThread

            class Foo {
                @UiThread
                fun callee() {}

                @JavascriptInterface
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt call from worker thread to UI thread`() {
        val code =
            """
            import androidx.annotation.UiThread
            import androidx.annotation.WorkerThread

            class Foo {
                @UiThread
                fun callee() {}

                @WorkerThread
                fun workerTask() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `ignore call from main thread to UI thread`() {
        val code =
            """
            import androidx.annotation.MainThread
            import androidx.annotation.UiThread

            class Foo {
                @UiThread
                fun callee() {}

                @MainThread
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignore call from UI thread to UI thread`() {
        val code =
            """
            import androidx.annotation.UiThread

            class Foo {
                @UiThread
                fun callee() {}

                @UiThread
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `detekt call from any thread to UI thread`() {
        val code =
            """
            import androidx.annotation.AnyThread
            import androidx.annotation.UiThread

            class Foo {
                @UiThread
                fun callee() {}

                @AnyThread
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt call from unknown thread to UI thread`() {
        val code =
            """
            import androidx.annotation.UiThread

            class Foo {
                @UiThread
                fun callee() {}

                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt call from javascript thread to main thread`() {
        val code =
            """
            import android.webkit.JavascriptInterface
            import androidx.annotation.MainThread

            class Foo {
                @MainThread
                fun callee() {}

                @JavascriptInterface
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt call from worker thread to main thread`() {
        val code =
            """
            import androidx.annotation.MainThread
            import androidx.annotation.WorkerThread

            class Foo {
                @MainThread
                fun callee() {}

                @WorkerThread
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `ignore call from main thread to main thread`() {
        val code =
            """
            import androidx.annotation.MainThread

            class Foo {
                @MainThread
                fun callee() {}

                @MainThread
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignore call from UI thread to main thread`() {
        val code =
            """
            import androidx.annotation.MainThread
            import androidx.annotation.UiThread

            class Foo {
                @MainThread
                fun callee() {}

                @UiThread
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `detekt call from any thread to main thread`() {
        val code =
            """
            import androidx.annotation.AnyThread
            import androidx.annotation.MainThread

            class Foo {
                @MainThread
                fun callee() {}

                @AnyThread
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `detekt call from unknown thread to main thread`() {
        val code =
            """
            import androidx.annotation.MainThread

            class Foo {
                @MainThread
                fun callee() {}

                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `ignore call from javascript thread to any thread`() {
        val code =
            """
            import android.webkit.JavascriptInterface
            import androidx.annotation.AnyThread

            class Foo {
                @AnyThread
                fun callee() {}

                @JavascriptInterface
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignore call from worker thread to any thread`() {
        val code =
            """
            import androidx.annotation.AnyThread
            import androidx.annotation.WorkerThread

            class Foo {
                @AnyThread
                fun callee() {}

                @WorkerThread
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignore call from main thread to any thread`() {
        val code =
            """
            import androidx.annotation.AnyThread
            import androidx.annotation.MainThread

            class Foo {
                @AnyThread
                fun callee() {}

                @MainThread
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignore call from UI thread to any thread`() {
        val code =
            """
            import androidx.annotation.AnyThread
            import androidx.annotation.UiThread

            class Foo {
                @AnyThread
                fun callee() {}

                @UiThread
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignore call from any thread to any thread`() {
        val code =
            """
            import androidx.annotation.AnyThread

            class Foo {
                @AnyThread
                fun callee() {}

                @AnyThread
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignore call from unknown thread to any thread`() {
        val code =
            """
            import androidx.annotation.AnyThread

            class Foo {
                @AnyThread
                fun callee() {}

                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignore call from javascript thread to unknown thread`() {
        val code =
            """
            import android.webkit.JavascriptInterface

            class Foo {
                fun callee() {}

                @JavascriptInterface
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignore call from worker thread to unknown thread`() {
        val code =
            """
            import androidx.annotation.WorkerThread

            class Foo {
                fun callee() {}

                @WorkerThread
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignore call from main thread to unknown thread`() {
        val code =
            """
            import androidx.annotation.MainThread

            class Foo {
                fun callee() {}

                @MainThread
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignore call from UI thread to unknown thread`() {
        val code =
            """
            import androidx.annotation.UiThread

            class Foo {
                fun callee() {}

                @UiThread
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignore call from any thread to unknown thread`() {
        val code =
            """
            import androidx.annotation.AnyThread

            class Foo {
                fun callee() {}

                @AnyThread
                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignore call from unknown thread to unknown thread`() {
        val code =
            """
            class Foo {
                fun callee() {}

                fun caller() {
                    callee()
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignore call from main thread to worker thread through threadSwitchingCall`() {
        fakeConfig = TestConfig("workerThreadSwitchingCalls" to listOf("java.util.concurrent.ExecutorService.submit"))
        val code =
            """
            import androidx.annotation.MainThread
            import androidx.annotation.WorkerThread
            import java.util.concurrent.ExecutorService

            class Foo(val executorService: ExecutorService) {
                @WorkerThread
                fun callee() {}

                @MainThread
                fun caller() {
                    executorService.submit {
                        callee()
                    }
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(0)
    }

    @Test
    fun `ignore call from worker thread to main thread through threadSwitchingCall`() {
        fakeConfig = TestConfig("mainThreadSwitchingCalls" to listOf("android.widget.LinearLayout.post"))
        val code =
            """
            import androidx.annotation.MainThread
            import androidx.annotation.WorkerThread
            import android.widget.LinearLayout

            class Foo(val linearLayout: LinearLayout) {
                @MainThread
                fun callee() {}

                @WorkerThread
                fun caller() {
                    linearLayout.post {
                        callee()
                    }
                }

            }
            """.trimIndent()

        val findings = ThreadSafety(fakeConfig).compileAndLintWithContext(kotlinEnv.env, code)
        assertThat(findings).hasSize(0)
    }
}
