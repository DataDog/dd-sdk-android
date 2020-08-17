/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.file

import android.os.Build
import com.datadog.android.core.internal.data.DataMigrator
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.getFieldValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DeferredWriterTest {

    lateinit var testedWriter: DeferredWriter<String>

    @Mock
    lateinit var mockDelegate: Writer<String>

    @Mock
    lateinit var mockDataMigrator: DataMigrator

    @Mock
    lateinit var mockExecutorService: ExecutorService

    lateinit var threadName: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        threadName = forge.anAlphabeticalString()
        whenever(mockExecutorService.submit(any())).doAnswer {
            (it.arguments[0] as Runnable).run()
            mock()
        }
        testedWriter = DeferredWriter(
            mockDelegate,
            mockExecutorService,
            mockDataMigrator
        )
    }

    @Test
    fun `migrates the data before doing anything else`(forge: Forge) {
        val model = forge.anAlphabeticalString()

        // When
        testedWriter.write(model)

        // Then
        val inOrder = inOrder(mockDataMigrator, mockDelegate)
        inOrder.verify(mockDataMigrator).migrateData()
        inOrder.verify(mockDelegate).write(model)
    }

    @Test
    fun `migrates the data before doing anything else in multi thread`(forge: Forge) {
        val model1 = forge.anAlphabeticalString()
        val model2 = forge.anAlphabeticalString()
        val model3 = forge.anAlphabeticalString()

        val countDownLatch = CountDownLatch(3)

        // When
        Thread {
            testedWriter.write(model1)
            countDownLatch.countDown()
        }.start()
        Thread {
            testedWriter.write(model2)
            countDownLatch.countDown()
        }.start()
        Thread {
            testedWriter.write(model3)
            countDownLatch.countDown()
        }.start()

        countDownLatch.await(3000, TimeUnit.SECONDS)

        // Then
        inOrder(mockDataMigrator, mockDelegate) {
            verify(mockDataMigrator).migrateData()
            argumentCaptor<String>() {
                verify(mockDelegate, times(3)).write(capture())
                assertThat(allValues).containsExactlyInAnyOrder(model1, model2, model3)
            }
        }
    }

    @Test
    fun `handles the data correctly even when write was called before migration`(forge: Forge) {
        val model1 = forge.anAlphabeticalString()
        val model2 = forge.anAlphabeticalString()
        val model3 = forge.anAlphabeticalString()

        val countDownLatch = CountDownLatch(3)
        val dataMigrated: AtomicBoolean = testedWriter.getFieldValue("dataMigrated")
        dataMigrated.set(false)

        // When
        Thread {
            testedWriter.write(model1)
            countDownLatch.countDown()
        }.start()

        Thread {
            testedWriter.write(model2)
            countDownLatch.countDown()
        }.start()

        // simulate data migration finalized
        dataMigrated.set(true)

        Thread {
            testedWriter.write(model3)
            countDownLatch.countDown()
        }.start()

        countDownLatch.await(3000, TimeUnit.SECONDS)

        // Then
        inOrder(mockDataMigrator, mockDelegate) {
            verify(mockDataMigrator).migrateData()
            argumentCaptor<String>() {
                verify(mockDelegate, times(3)).write(capture())
                assertThat(allValues).containsExactlyInAnyOrder(model1, model2, model3)
            }
        }
    }

    @Test
    fun `if no data migrator provided will not perform the migration step`(forge: Forge) {
        val model = forge.anAlphabeticalString()
        reset(mockExecutorService)
        testedWriter = DeferredWriter(
            mockDelegate,
            mockExecutorService
        )
        // When
        testedWriter.write(model)

        // Then
        verify(mockExecutorService, times(1)).submit(any())
        verifyNoMoreInteractions(mockExecutorService)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `run delegate in deferred handler when writing a model`(forge: Forge) {
        val model = forge.anAlphabeticalString()

        testedWriter.write(model)

        verify(mockDelegate).write(model)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `run delegate in deferred handler when writing a models list`(forge: Forge) {
        val models: List<String> = forge.aList(size = 10) { forge.anAlphabeticalString() }

        testedWriter.write(models)

        verify(mockDelegate).write(models)
    }
}
