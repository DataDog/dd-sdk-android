/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.domain

import android.content.Context
import com.datadog.android.core.internal.data.file.ImmediateFileWriter
import com.datadog.android.core.internal.domain.FilePersistenceConfig
import com.datadog.android.core.internal.domain.assertj.PersistenceStrategyAssert
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.util.concurrent.ExecutorService
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
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class LogFileStrategyTest {
    lateinit var testedStrategy: LogFileStrategy

    lateinit var mockedContext: Context

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockConsentProvider: ConsentProvider

    @Forgery
    lateinit var fakePersistenceConfig: FilePersistenceConfig

    @Forgery
    lateinit var fakeConsent: TrackingConsent

    @BeforeEach
    fun `set up`() {
        mockedContext = mockContext()
        whenever(mockConsentProvider.getConsent()) doReturn fakeConsent
        testedStrategy = LogFileStrategy(
            mockedContext,
            dataPersistenceExecutorService = mockExecutorService,
            trackingConsentProvider = mockConsentProvider,
            filePersistenceConfig = fakePersistenceConfig
        )
    }

    @Test
    fun `M correctly initialise the strategy W instantiated`() {
        val absolutePath = mockedContext.filesDir.absolutePath
        val expectedIntermediateFolderPath =
            absolutePath +
                File.separator +
                LogFileStrategy.INTERMEDIATE_DATA_FOLDER
        val expectedAuthorizedFolderPath =
            absolutePath +
                File.separator +
                LogFileStrategy.AUTHORIZED_FOLDER
        PersistenceStrategyAssert
            .assertThat(testedStrategy)
            .hasIntermediateStorageFolder(expectedIntermediateFolderPath)
            .hasAuthorizedStorageFolder(expectedAuthorizedFolderPath)
            .uploadsFrom(expectedAuthorizedFolderPath)
            .usesConsentAwareAsyncWriter()
            .hasConfig(fakePersistenceConfig)
    }

    @Test
    fun `M use the DefaultFileWriter factory W instantiating the writer`(forge: Forge) {
        // GIVEN
        // just to avoid using the NoOpWriter factory
        whenever(mockConsentProvider.getConsent()) doReturn forge.aValueFrom(
            TrackingConsent::class.java,
            exclude = listOf(TrackingConsent.NOT_GRANTED)
        )
        testedStrategy = LogFileStrategy(
            mockedContext,
            dataPersistenceExecutorService = mockExecutorService,
            trackingConsentProvider = mockConsentProvider,
            filePersistenceConfig = fakePersistenceConfig
        )

        // THEN
        PersistenceStrategyAssert
            .assertThat(testedStrategy)
            .hasFileInternalWriterInstanceOf(ImmediateFileWriter::class.java)
    }
}
