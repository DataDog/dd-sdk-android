/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.flags

import android.content.SharedPreferences
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.model.FlagsClientEvent
import com.datadog.android.flags.model.FlagsClientEventType
import com.datadog.android.flags.model.ResolutionDetails
import com.datadog.android.flags.model.ResolutionReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

internal class FirstFlagsSampleTest {
    private val client = mock<FlagsClient>()
    private val selection = mock<SharedPreferences>()
    private val logs = mutableListOf<String>()
    private val event = FlagsClientEvent(FlagsClientEventType.CONFIGURATION_CHANGED, listOf("selected", "another"))

    @Test
    fun `M retain early event W callback completes before client assignment`() = runBlocking {
        val firstFlags = CompletableDeferred<FlagsClientEvent>()
        val callback: (FlagsClientEvent) -> Unit = { firstFlags.complete(it) }
        callback(event)
        verifyNoInteractions(client)
        // The application registers this continuation only after build returns and assigns the client.
        prepareSelection()
        logAndEvaluateFirstFlags(client, firstFlags, selection, logs::add).join()
        assertSingleEvaluation()
    }

    @Test
    fun `M suspend only continuation W event arrives after client assignment`() = runBlocking {
        val firstFlags = CompletableDeferred<FlagsClientEvent>()
        prepareSelection()
        val job = logAndEvaluateFirstFlags(client, firstFlags, selection, logs::add)
        yield()
        assertThat(job.isCompleted).isFalse()
        assertThat(logs).isEmpty()
        verifyNoInteractions(client)
        firstFlags.complete(event)
        job.join()
        assertSingleEvaluation()
    }

    @Test
    fun `M distinguish absent and empty keys W no saved selection`() = runBlocking {
        listOf(null, emptyList<String>()).forEach { keys ->
            logs.clear()
            logAndEvaluateFirstFlags(
                client,
                CompletableDeferred(FlagsClientEvent(FlagsClientEventType.CONFIGURATION_CHANGED, keys)),
                selection,
                logs::add
            ).join()
            assertThat(logs).containsExactly(
                "Installed flag keys: ${keys ?: "<absent>"}",
                "Select and evaluate a Boolean flag in OpenFeature, then relaunch the app."
            )
        }
        verifyNoInteractions(client)
    }

    private fun prepareSelection() {
        whenever(selection.getString(OpenFeatureFragment.FIRST_FLAGS_KEY, null)).thenReturn("selected")
        whenever(selection.getBoolean(OpenFeatureFragment.FIRST_FLAGS_DEFAULT, false)).thenReturn(false)
        whenever(client.resolve("selected", false)).thenReturn(
            ResolutionDetails(value = true, reason = ResolutionReason.STATIC)
        )
    }

    private fun assertSingleEvaluation() {
        assertThat(logs).containsExactly(
            "Installed flag keys: [selected, another]",
            "selected = true (reason=STATIC)"
        )
        verify(client).resolve("selected", false)
        verifyNoMoreInteractions(client)
    }
}
