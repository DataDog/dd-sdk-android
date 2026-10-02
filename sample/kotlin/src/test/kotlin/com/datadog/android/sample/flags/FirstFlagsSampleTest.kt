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
    fun `M log keys and evaluate selected flag W callback receives event`() {
        prepareSelection()
        logAndEvaluateFirstFlags(client, event, selection, logs::add)
        assertSingleEvaluation()
    }

    @Test
    fun `M distinguish absent and empty keys W no saved selection`() {
        listOf(null, emptyList<String>()).forEach { keys ->
            logs.clear()
            logAndEvaluateFirstFlags(
                client,
                FlagsClientEvent(FlagsClientEventType.CONFIGURATION_CHANGED, keys),
                selection,
                logs::add
            )
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
