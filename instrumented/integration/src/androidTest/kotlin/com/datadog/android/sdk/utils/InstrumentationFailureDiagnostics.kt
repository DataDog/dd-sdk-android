/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.utils

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.Description
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal object InstrumentationFailureDiagnostics {
    private val captured = AtomicBoolean(false)

    fun capture(description: Description, failure: Throwable) {
        val enabled = InstrumentationRegistry.getArguments()
            .getString("captureFailureDiagnostics")
            .toBoolean()
        if (!enabled || !captured.compareAndSet(false, true)) return

        // This runs inside ActivityTestRule's statement, before its activity teardown.
        runCatching {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val directory = checkNotNull(instrumentation.targetContext.getExternalFilesDir("ci-diagnostics"))
            check(directory.isDirectory || directory.mkdirs())
            File(directory, "failure.txt").writeText(
                "timestampMs=${System.currentTimeMillis()}\n" +
                    "test=${description.displayName}\n${failure.stackTraceToString()}"
            )
            val automation = instrumentation.uiAutomation
            captureArtifact(directory, "screenshot.png") { output ->
                val screenshot = checkNotNull(automation.takeScreenshot())
                try {
                    output.outputStream().use { stream ->
                        check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    }
                } finally {
                    screenshot.recycle()
                }
            }
            mapOf(
                "window.txt" to "dumpsys -t 5 window",
                "activities.txt" to "dumpsys -t 5 activity activities",
                "input.txt" to "dumpsys -t 5 input",
                "power.txt" to "dumpsys -t 5 power"
            ).forEach { (name, command) ->
                captureArtifact(directory, name) { output ->
                    val descriptor = automation.executeShellCommand(command)
                    ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                        output.outputStream().use { stream -> input.copyTo(stream) }
                    }
                }
            }
        }.onFailure {
            Log.w("FailureDiagnostics", "Could not capture failure diagnostics", it)
        }
    }

    private fun captureArtifact(directory: File, name: String, capture: (File) -> Unit) {
        runCatching { capture(File(directory, name)) }
            .onFailure { Log.w("FailureDiagnostics", "Could not capture $name", it) }
    }
}
