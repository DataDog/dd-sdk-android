/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.telemetry

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.datadog.android.Datadog
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.utils.getTrackingConsent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryException
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOError
import java.io.IOException
import java.io.UncheckedIOException
import java.lang.ArithmeticException
import java.lang.IndexOutOfBoundsException
import java.lang.RuntimeException

internal class TelemetryPlaygroundActivity : AppCompatActivity(R.layout.main_activity_layout) {

    private val forge = Forge()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val credentials = RuntimeConfig.credentials()
        // we will use a large long task threshold to make sure we will not have LongTask events
        // noise in our integration tests.
        val config = RuntimeConfig.configBuilder()
            .sampleTelemetry(100f)
            .trackLongTasks(RuntimeConfig.LONG_TASK_LARGE_THRESHOLD)
            .useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
            .build()

        val trackingConsent = intent.getTrackingConsent()

        Datadog.initialize(this, credentials, config, trackingConsent)
        Datadog.setVerbosity(Log.VERBOSE)

        GlobalRum.registerIfAbsent(RumMonitor.Builder().build())
    }

    override fun onPostResume() {
        super.onPostResume()
        submitTelemetry(intent)
    }

    @Suppress("ThrowingInternalException")
    private fun submitTelemetry(intent: Intent) {
        val debugMessage = intent.getStringExtra(TELEMETRY_DEBUG_MESSAGE_KEY)
            ?: throw IllegalArgumentException("Telemetry debug message should be provided")

        val errorMessage = intent.getStringExtra(TELEMETRY_ERROR_MESSAGE_KEY)
            ?: throw IllegalArgumentException("Telemetry error message should be provided")

        GlobalRum.get().sendDebugTelemetry(debugMessage)
        GlobalRum.get().sendErrorTelemetry(errorMessage)
        GlobalRum.get()
            .sendErrorTelemetry(errorMessage, forge.aThrowable())
    }

    private fun RumMonitor.sendDebugTelemetry(message: String) {
        this.invokeMethod("sendDebugTelemetryEvent", message)
    }

    private fun RumMonitor.sendErrorTelemetry(message: String, throwable: Throwable? = null) {
        this.invokeMethod("sendErrorTelemetryEvent", message, throwable)
    }

    private fun RumMonitor.invokeMethod(methodName: String, vararg args: Any?) {
        val method = this::class.java.declaredMethods.first { it.name == methodName }
        method.isAccessible = true
        method.invoke(this, *args)
    }

    private fun Forge.aThrowable() = anElementFrom(anError(), anException())

    private fun Forge.anError(): Error {
        val errorMessage = anAlphabeticalString()
        return anElementFrom(
            UnknownError(errorMessage),
            IOError(RuntimeException(errorMessage)),
            NotImplementedError(errorMessage),
            StackOverflowError(errorMessage),
            OutOfMemoryError(errorMessage)
        )
    }

    @SuppressLint("NewApi")
    private fun Forge.anException(): Exception {
        val errorMessage = anAlphabeticalString()
        return anElementFrom(
            IndexOutOfBoundsException(errorMessage),
            ArithmeticException(errorMessage),
            IllegalStateException(errorMessage),
            ArrayIndexOutOfBoundsException(errorMessage),
            NullPointerException(errorMessage),
            ForgeryException(errorMessage),
            UnsupportedOperationException(errorMessage),
            SecurityException(errorMessage),
            UncheckedIOException(IOException(errorMessage)),
            UncheckedIOException(FileNotFoundException(errorMessage)),
            UncheckedIOException(EOFException(errorMessage))
        )
    }

    companion object {
        internal const val TELEMETRY_DEBUG_MESSAGE_KEY = "telemetry_debug_message"
        internal const val TELEMETRY_ERROR_MESSAGE_KEY = "telemetry_error_message"
    }
}
