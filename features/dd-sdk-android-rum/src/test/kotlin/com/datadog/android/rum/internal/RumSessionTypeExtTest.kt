/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.TimeseriesCpuEvent
import com.datadog.android.rum.model.TimeseriesMemoryEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.model.VitalAppLaunchEvent
import com.datadog.android.rum.model.VitalOperationStepEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class RumSessionTypeExtTest {

    @ParameterizedTest
    @MethodSource("toActionMappings")
    fun `M map session type W toAction()`(input: RumSessionType, expected: ActionEvent.ActionEventSessionType) {
        assertThat(input.toAction()).isEqualTo(expected)
    }

    @ParameterizedTest
    @MethodSource("toResourceMappings")
    fun `M map session type W toResource()`(input: RumSessionType, expected: ResourceEvent.ResourceEventSessionType) {
        assertThat(input.toResource()).isEqualTo(expected)
    }

    @ParameterizedTest
    @MethodSource("toErrorMappings")
    fun `M map session type W toError()`(input: RumSessionType, expected: ErrorEvent.ErrorEventSessionType) {
        assertThat(input.toError()).isEqualTo(expected)
    }

    @ParameterizedTest
    @MethodSource("toViewMappings")
    fun `M map session type W toView()`(input: RumSessionType, expected: ViewEvent.ViewEventSessionType) {
        assertThat(input.toView()).isEqualTo(expected)
    }

    @ParameterizedTest
    @MethodSource("toLongTaskMappings")
    fun `M map session type W toLongTask()`(input: RumSessionType, expected: LongTaskEvent.LongTaskEventSessionType) {
        assertThat(input.toLongTask()).isEqualTo(expected)
    }

    @ParameterizedTest
    @MethodSource("toVitalMappings")
    fun `M map session type W toVital()`(
        input: RumSessionType,
        expected: VitalOperationStepEvent.VitalOperationStepEventSessionType
    ) {
        assertThat(input.toVital()).isEqualTo(expected)
    }

    @ParameterizedTest
    @MethodSource("toVitalAppLaunchMappings")
    fun `M map session type W toVitalAppLaunch()`(
        input: RumSessionType,
        expected: VitalAppLaunchEvent.VitalAppLaunchEventSessionType
    ) {
        assertThat(input.toVitalAppLaunch()).isEqualTo(expected)
    }

    @ParameterizedTest
    @MethodSource("toTimeseriesMemorySessionTypeMappings")
    fun `M map session type W toTimeseriesMemorySessionType()`(
        input: RumSessionType,
        expected: TimeseriesMemoryEvent.Type
    ) {
        assertThat(input.toTimeseriesMemorySessionType()).isEqualTo(expected)
    }

    @ParameterizedTest
    @MethodSource("toTimeseriesCpuSessionTypeMappings")
    fun `M map session type W toTimeseriesCpuSessionType()`(input: RumSessionType, expected: TimeseriesCpuEvent.Type) {
        assertThat(input.toTimeseriesCpuSessionType()).isEqualTo(expected)
    }

    companion object {

        @JvmStatic
        fun toActionMappings(): List<Arguments> = listOf(
            Arguments.of(RumSessionType.USER, ActionEvent.ActionEventSessionType.USER),
            Arguments.of(RumSessionType.SYNTHETICS, ActionEvent.ActionEventSessionType.SYNTHETICS)
        )

        @JvmStatic
        fun toResourceMappings(): List<Arguments> = listOf(
            Arguments.of(RumSessionType.USER, ResourceEvent.ResourceEventSessionType.USER),
            Arguments.of(RumSessionType.SYNTHETICS, ResourceEvent.ResourceEventSessionType.SYNTHETICS)
        )

        @JvmStatic
        fun toErrorMappings(): List<Arguments> = listOf(
            Arguments.of(RumSessionType.USER, ErrorEvent.ErrorEventSessionType.USER),
            Arguments.of(RumSessionType.SYNTHETICS, ErrorEvent.ErrorEventSessionType.SYNTHETICS)
        )

        @JvmStatic
        fun toViewMappings(): List<Arguments> = listOf(
            Arguments.of(RumSessionType.USER, ViewEvent.ViewEventSessionType.USER),
            Arguments.of(RumSessionType.SYNTHETICS, ViewEvent.ViewEventSessionType.SYNTHETICS)
        )

        @JvmStatic
        fun toLongTaskMappings(): List<Arguments> = listOf(
            Arguments.of(RumSessionType.USER, LongTaskEvent.LongTaskEventSessionType.USER),
            Arguments.of(RumSessionType.SYNTHETICS, LongTaskEvent.LongTaskEventSessionType.SYNTHETICS)
        )

        @JvmStatic
        fun toVitalMappings(): List<Arguments> = listOf(
            Arguments.of(
                RumSessionType.USER,
                VitalOperationStepEvent.VitalOperationStepEventSessionType.USER
            ),
            Arguments.of(
                RumSessionType.SYNTHETICS,
                VitalOperationStepEvent.VitalOperationStepEventSessionType.SYNTHETICS
            )
        )

        @JvmStatic
        fun toVitalAppLaunchMappings(): List<Arguments> = listOf(
            Arguments.of(
                RumSessionType.USER,
                VitalAppLaunchEvent.VitalAppLaunchEventSessionType.USER
            ),
            Arguments.of(
                RumSessionType.SYNTHETICS,
                VitalAppLaunchEvent.VitalAppLaunchEventSessionType.SYNTHETICS
            )
        )

        @JvmStatic
        fun toTimeseriesMemorySessionTypeMappings(): List<Arguments> = listOf(
            Arguments.of(RumSessionType.USER, TimeseriesMemoryEvent.Type.USER),
            Arguments.of(RumSessionType.SYNTHETICS, TimeseriesMemoryEvent.Type.SYNTHETICS)
        )

        @JvmStatic
        fun toTimeseriesCpuSessionTypeMappings(): List<Arguments> = listOf(
            Arguments.of(RumSessionType.USER, TimeseriesCpuEvent.Type.USER),
            Arguments.of(RumSessionType.SYNTHETICS, TimeseriesCpuEvent.Type.SYNTHETICS)
        )
    }
}
