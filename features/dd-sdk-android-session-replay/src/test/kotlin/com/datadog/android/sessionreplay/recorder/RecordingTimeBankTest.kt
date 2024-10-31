/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.RecordingTimeBank
import com.datadog.android.sessionreplay.internal.recorder.TimeBank
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
class RecordingTimeBankTest {

    private lateinit var recordingTimeBank: TimeBank

    @BeforeEach
    fun `set up`() {
        recordingTimeBank = RecordingTimeBank(TEST_MAX_BALANCE_IN_MS)
    }

    @Test
    fun `M allow the first execution W check`(forge: Forge) {
        // Given
        val timestamp = forge.aLong(min = 0)

        // When
        val actual = recordingTimeBank.updateAndCheck(timestamp)

        // Then
        assertThat(actual).isTrue()
    }

    @Test
    fun `M skip the next execution W previous consume out the balance`(forge: Forge) {
        // Given
        val firstTimestamp = forge.aLong(min = 0)
        val firstExecutionTime = forge.aLong(
            min = TimeUnit.MILLISECONDS.toNanos(TEST_MAX_BALANCE_IN_MS),
            max = TimeUnit.MILLISECONDS.toNanos(TEST_MAX_BALANCE_IN_MS) * 100
        )
        val interval = forge.aLong(min = 0, max = firstExecutionTime)
        val secondTimestamp = forge.aLong(
            min = firstTimestamp + firstExecutionTime,
            max = firstTimestamp + firstExecutionTime + interval
        )

        // When
        recordingTimeBank.updateAndCheck(firstTimestamp)
        recordingTimeBank.consume(firstExecutionTime)
        val actual = recordingTimeBank.updateAndCheck(secondTimestamp)

        // Then
        assertThat(actual).isFalse()
    }

    @Test
    fun `M allow the next execution W balance is recovery`(forge: Forge) {
        // Given
        val firstTimestamp = forge.aLong(min = 0)
        val firstExecutionTime = forge.aLong(
            min = TimeUnit.MILLISECONDS.toNanos(TEST_MAX_BALANCE_IN_MS),
            max = TimeUnit.MILLISECONDS.toNanos(TEST_MAX_BALANCE_IN_MS) * 100
        )
        val interval = forge.aLong(min = 0, max = firstExecutionTime)
        val secondTimestamp = forge.aLong(
            min = firstTimestamp + firstExecutionTime,
            max = firstTimestamp + firstExecutionTime + interval
        )

        val thirdTimestamp =
            forge.aLong(
                min = secondTimestamp + firstExecutionTime /
                    ((TimeUnit.SECONDS.toMillis(1) / TEST_MAX_BALANCE_IN_MS))
            )

        // When
        recordingTimeBank.updateAndCheck(firstTimestamp)
        recordingTimeBank.consume(firstExecutionTime)
        recordingTimeBank.updateAndCheck(secondTimestamp)
        check(!recordingTimeBank.updateAndCheck(secondTimestamp))
        val actual = recordingTimeBank.updateAndCheck(thirdTimestamp)

        // Then
        assertThat(actual).isTrue()
    }

    @Test
    fun `M allow everything W set max balance more than 1000ms per sec`(forge: Forge) {
        val maxBalancePerSecondInMs = forge.aLong(min = 1000)
        recordingTimeBank = RecordingTimeBank(maxBalancePerSecondInMs)

        // Given
        val firstTimestamp = forge.aLong(min = 0)
        val firstExecutionTime = forge.aLong(
            min = TimeUnit.MILLISECONDS.toNanos(TEST_MAX_BALANCE_IN_MS),
            max = TimeUnit.MILLISECONDS.toNanos(TEST_MAX_BALANCE_IN_MS) * 100
        )
        val interval = forge.aLong(min = 0, max = firstExecutionTime)
        val secondTimestamp = forge.aLong(
            min = firstTimestamp + firstExecutionTime,
            max = firstTimestamp + firstExecutionTime + interval
        )

        // When
        recordingTimeBank.updateAndCheck(firstTimestamp)
        recordingTimeBank.consume(firstExecutionTime)
        val actual = recordingTimeBank.updateAndCheck(secondTimestamp)

        // Then
        assertThat(actual).isEqualTo(true)
    }

    companion object {
        private const val TEST_MAX_BALANCE_IN_MS = 100L
    }
}
