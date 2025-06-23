/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.internal.thread.NamedRunnable
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class ObservableBlockingQueueTest {

    @Test
    fun `M return only once non-null map W try to dump multiple times in dump interval`(
        forge: Forge
    ) {
        // Given
        val fakeItemCount = forge.aSmallInt()
        val fakeFirstTimestamp = forge.aLong(min = 10000)
        val fakeSecondTimestamp = fakeFirstTimestamp + forge.aLong(max = 1000)
        val fakeThirdTimestamp = fakeSecondTimestamp + forge.aLong(max = 1000)
        val fakeTimestamps =
            listOf(fakeFirstTimestamp, fakeSecondTimestamp, fakeThirdTimestamp).iterator()
        val fakeTimeProvider: () -> Long = { fakeTimestamps.next() }
        val testedObservableLinkedBlockingQueue =
            ObservableLinkedBlockingQueue<Any>(fakeItemCount + 1, fakeTimeProvider)
        repeat(fakeItemCount) {
            val mockItem = mock<Any>()
            testedObservableLinkedBlockingQueue.offer(mockItem)
        }

        // When
        val firstMap = testedObservableLinkedBlockingQueue.dumpQueue()
        val secondMap = testedObservableLinkedBlockingQueue.dumpQueue()
        val thirdMap = testedObservableLinkedBlockingQueue.dumpQueue()

        // Then
        assertThat(firstMap).isNotNull
        assertThat(secondMap).isNullOrEmpty()
        assertThat(thirdMap).isNullOrEmpty()
    }

    @Test
    fun `M return only twice non-null map W try to dump twice times over dump interval`(
        forge: Forge
    ) {
        // Given
        val fakeItemCount = forge.aSmallInt()
        val fakeFirstTimestamp = forge.aLong(min = 10000)
        val fakeSecondTimestamp = fakeFirstTimestamp + forge.aLong(min = 5000)
        val fakeTimestamps = listOf(fakeFirstTimestamp, fakeSecondTimestamp).iterator()
        val fakeTimeProvider: () -> Long = { fakeTimestamps.next() }
        val testedObservableLinkedBlockingQueue =
            ObservableLinkedBlockingQueue<Any>(fakeItemCount + 1, fakeTimeProvider)
        repeat(fakeItemCount) {
            val mockItem = mock<Any>()
            testedObservableLinkedBlockingQueue.offer(mockItem)
        }

        // When
        val firstMap = testedObservableLinkedBlockingQueue.dumpQueue()
        val secondMap = testedObservableLinkedBlockingQueue.dumpQueue()

        // Then
        assertThat(firstMap).isNotNull
        assertThat(secondMap).isNotNull
    }

    @Test
    fun `M build correct map W try to dump named runnable`(
        forge: Forge
    ) {
        // Given
        val expectedMap = mutableMapOf<String, Int>()
        val fakeTimeProvider: () -> Long = { forge.aLong(min = 10000L) }
        val fakeRunnableCount = forge.anInt(min = 5, max = 100)
        val testedObservableLinkedBlockingQueue =
            ObservableLinkedBlockingQueue<Runnable>(fakeRunnableCount + 1, fakeTimeProvider)
        val fakeRunnableTypeCount = forge.anInt(min = 1, max = fakeRunnableCount)
        val fakeRunnableTypes = mutableListOf<String>()
        repeat(fakeRunnableTypeCount) {
            fakeRunnableTypes.add(forge.anAlphabeticalString())
        }
        repeat(fakeRunnableCount) {
            val fakeName = forge.anElementFrom(fakeRunnableTypes)
            val fakeNamedRunnable = NamedRunnable(fakeName, mock<Runnable>())
            testedObservableLinkedBlockingQueue.offer(fakeNamedRunnable)
            expectedMap[fakeName] = (expectedMap[fakeName] ?: 0) + 1
        }

        // When
        val map = testedObservableLinkedBlockingQueue.dumpQueue()

        // Then
        assertThat(map).isEqualTo(expectedMap)
    }
}
