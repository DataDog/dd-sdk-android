/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.batching

import com.datadog.android.core.internal.data.batching.processors.DataProcessor
import com.datadog.android.core.internal.data.privacy.Consent
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.reset
import java.util.stream.Stream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class DataProcessorFactoryTest {

    lateinit var underTest: DataProcessorFactory<String>

    @ParameterizedTest
    @MethodSource("provideProcessorStatesData")
    fun `M generate the right processor W required`(
        consent: Consent,
        expected: DataProcessor<String>
    ) {

        // GIVEN
        resetMocks()
        underTest = DataProcessorFactory(
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
        val processor = underTest.resolveProcessor(consent)

        // THEN
        assertThat(processor).isEqualTo(expected)
    }

    companion object {

        lateinit var mockedPermissionGrantedDataProcessor: DataProcessor<String>
        lateinit var mockedPermissionPendingDataProcessor: DataProcessor<String>
        lateinit var mockedNoOpDataProcessor: DataProcessor<String>

        @JvmStatic
        fun provideProcessorStatesData(): Stream<Arguments> {
            initMocks()
            return Stream.of(
                Arguments.arguments(
                    Consent.PENDING,
                    mockedPermissionPendingDataProcessor
                ),
                Arguments.arguments(
                    Consent.GRANTED,
                    mockedPermissionGrantedDataProcessor
                ),
                Arguments.arguments(
                    Consent.NOT_GRANTED,
                    mockedNoOpDataProcessor
                )
            )
        }

        fun resetMocks() {
            reset(mockedPermissionGrantedDataProcessor)
            reset(mockedPermissionPendingDataProcessor)
            reset(mockedNoOpDataProcessor)
        }

        // We need this workaround as Parameterised tests need static arguments and they do not
        // work well with @BeforeEach setup methods.
        private fun initMocks() {
            mockedPermissionGrantedDataProcessor = mock()
            mockedPermissionPendingDataProcessor = mock()
            mockedNoOpDataProcessor = mock()
        }
    }
}
