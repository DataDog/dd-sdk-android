/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.domain.batching

import com.datadog.android.core.internal.domain.batching.processors.DataProcessor
import com.datadog.android.privacy.TrackingConsent
import com.nhaarman.mockitokotlin2.mock
import java.util.stream.Stream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class DataProcessorFactoryTest {

    lateinit var testedFactory: DataProcessorFactory<String>

    @ParameterizedTest
    @MethodSource("provideProcessorStatesData")
    fun `M generate the right processor W required`(
        consent: TrackingConsent,
        expected: DataProcessor<String>
    ) {

        // GIVEN
        testedFactory = DataProcessorFactory(
            permissionPendingProcessorFactory = {
                mockedPermissionPendingDataProcessor
            },
            permissionGrantedProcessorFactory = {
                mockedPermissionGrantedDataProcessor
            },
            noOpProcessorFactory = {
                mockedNoOpDataProcessor
            }
        )

        // WHEN
        val processor = testedFactory.resolveProcessor(consent)

        // THEN
        assertThat(processor).isEqualTo(expected)
    }

    companion object {

        val mockedPermissionGrantedDataProcessor: DataProcessor<String> = mock()
        val mockedPermissionPendingDataProcessor: DataProcessor<String> = mock()
        val mockedNoOpDataProcessor: DataProcessor<String> = mock()

        @JvmStatic
        fun provideProcessorStatesData(): Stream<Arguments> {
            return Stream.of(
                Arguments.arguments(
                    TrackingConsent.PENDING,
                    mockedPermissionPendingDataProcessor
                ),
                Arguments.arguments(
                    TrackingConsent.GRANTED,
                    mockedPermissionGrantedDataProcessor
                ),
                Arguments.arguments(
                    TrackingConsent.NOT_GRANTED,
                    mockedNoOpDataProcessor
                )
            )
        }
    }
}
