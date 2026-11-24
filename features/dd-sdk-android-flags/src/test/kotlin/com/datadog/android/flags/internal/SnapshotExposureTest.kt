/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.flags._FlagsInternalProxy
import com.datadog.android.flags._FlagsInternalProxy.SnapshotExposureDecision.INELIGIBLE
import com.datadog.android.flags._FlagsInternalProxy.SnapshotExposureDecision.UNAVAILABLE
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SnapshotExposureTest {
    @StringForgery
    lateinit var flagKey: String

    @Forgery
    lateinit var assignment: PrecomputedFlag

    @ParameterizedTest
    @ValueSource(strings = ["allocation", "variant", "unlogged", "missing subject"])
    fun `M skip an ineligible assignment W admitSnapshotExposure()`(condition: String) {
        // Given
        val client = mock<DatadogFlagsClient>()
        val flag = assignment.copy(
            allocationKey = if (condition == "allocation") " " else "allocation",
            variationKey = if (condition == "variant") "\t" else "variant",
            doLog = condition != "unlogged"
        )
        val context = if (condition == "missing subject") null else EvaluationContext(flagKey)

        // When
        val decision = _FlagsInternalProxy(client).admitSnapshotExposure(flagKey, flag, context, true)

        // Then
        assertThat(decision).isEqualTo(INELIGIBLE)
        verifyNoInteractions(client)
    }

    @Test
    fun `M return unavailable for a foreign client W admitSnapshotExposure()`() {
        // Given
        val client = _FlagsInternalProxy(mock())

        // When
        val decision = client.admitSnapshotExposure(flagKey, assignment, EvaluationContext(flagKey), true)

        // Then
        assertThat(decision).isEqualTo(UNAVAILABLE)
    }
}
