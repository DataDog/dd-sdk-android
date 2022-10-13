/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.core.internal.persistence.file.advanced.ConsentAwareFileOrchestrator
import com.datadog.android.core.internal.persistence.file.advanced.ScheduledWriter
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.v2.core.internal.storage.Storage
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class BatchFilePersistenceStrategyTest {

    lateinit var testedStrategy: PersistenceStrategy<String>

    @Mock
    lateinit var mockFileOrchestrator: ConsentAwareFileOrchestrator

    @Mock
    lateinit var mockSerializer: Serializer<String>

    @Mock
    lateinit var mockLogHandler: LogHandler

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockFileReaderWriter: BatchFileReaderWriter

    @Mock
    lateinit var mockContextProvider: ContextProvider

    @Mock
    lateinit var mockStorage: Storage

    @BeforeEach
    fun `set up`() {
        whenever(mockSerializer.serialize(any())) doAnswer {
            (it.arguments[0] as String).reversed()
        }

        whenever(mockFileOrchestrator.grantedOrchestrator) doReturn mock()
        whenever(mockFileOrchestrator.pendingOrchestrator) doReturn mock()

        testedStrategy = BatchFilePersistenceStrategy(
            mockContextProvider,
            mockExecutorService,
            mockSerializer,
            Logger(mockLogHandler),
            mockStorage
        )
    }

    @Test
    fun `𝕄 return batch file writer 𝕎 getWriter()`() {
        // When
        val writer = testedStrategy.getWriter()

        // Then
        assertThat(writer).isInstanceOf(ScheduledWriter::class.java)
        val scheduledWriter = writer as ScheduledWriter
        assertThat(scheduledWriter.delegateWriter).isInstanceOf(BatchFileDataWriter::class.java)
        assertThat(scheduledWriter.executorService).isSameAs(mockExecutorService)
    }

    @Test
    fun `𝕄 return same batch file writer 𝕎 getWriter() twice`() {
        // When
        val writer1 = testedStrategy.getWriter()
        val writer2 = testedStrategy.getWriter()

        // Then
        assertThat(writer1).isSameAs(writer2)
    }
}
