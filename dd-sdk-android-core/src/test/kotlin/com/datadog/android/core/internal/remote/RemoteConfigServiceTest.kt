/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.remote

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.remote.model.RemoteConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.google.gson.JsonParseException
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.util.concurrent.ExecutorService

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RemoteConfigServiceTest {

    @Mock
    lateinit var mockFetcher: RemoteConfigFetcher

    @Mock
    lateinit var mockExecutor: ExecutorService

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockFileReaderWriter: FileReaderWriter

    @TempDir
    lateinit var fakeStorageDir: File

    private lateinit var testedService: RemoteConfigServiceImpl

    private val fakeRemoteConfigurationId = "test-rc-id"
    private val fakeEndpoint: HttpUrl = "https://sdk-configuration.browser-intake-datadoghq.com".toHttpUrl()

    @BeforeEach
    fun `set up`() {
        // Run executor tasks synchronously
        whenever(mockExecutor.execute(any())).thenAnswer { invocation ->
            val runnable = invocation.getArgument<Runnable>(0)
            runnable.run()
        }

        // No cache on disk by default
        whenever(mockFileReaderWriter.readData(any(), any())).doReturn(ByteArray(0))
        whenever(mockFileReaderWriter.writeData(any(), any(), any(), any())).doReturn(true)
    }

    private fun buildService(): RemoteConfigServiceImpl {
        return RemoteConfigServiceImpl(
            remoteConfigurationId = fakeRemoteConfigurationId,
            remoteConfigurationEndpoint = fakeEndpoint,
            fetcher = mockFetcher,
            storageDir = fakeStorageDir,
            executor = mockExecutor,
            internalLogger = mockInternalLogger,
            fileReaderWriter = mockFileReaderWriter
        )
    }

    // region getCurrentConfig() — cold-start

    @Test
    fun `M return null W getCurrentConfig() { no cache on disk }`() {
        // Given
        testedService = buildService()

        // When
        val result = testedService.getCurrentConfig()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return cached config W getCurrentConfig() { valid cache on disk }`(
        @Forgery fakeRemoteConfiguration: RemoteConfiguration
    ) {
        // Given — write a real file so existsSafe() passes
        val fakeJson = fakeRemoteConfiguration.toJson().toString()
        val cacheFile = File(fakeStorageDir, "$fakeRemoteConfigurationId.json")
        cacheFile.writeText(fakeJson)
        testedService = RemoteConfigServiceImpl(
            remoteConfigurationId = fakeRemoteConfigurationId,
            remoteConfigurationEndpoint = fakeEndpoint,
            fetcher = mockFetcher,
            storageDir = fakeStorageDir,
            executor = mockExecutor,
            internalLogger = mockInternalLogger
            // use real FileReaderWriter to read the real file
        )

        // When
        val result = testedService.getCurrentConfig()

        // Then — compare JSON output: the generated model uses Number fields that deserialize
        // as LazilyParsedNumber (no equals() override), so object equality is unreliable.
        // Comparing serialized JSON verifies the full roundtrip without false negatives.
        assertThat(result?.toJson()?.toString()).isEqualTo(fakeJson)
    }

    @Test
    fun `M return null and delete corrupt file W getCurrentConfig() { invalid JSON on disk }`() {
        // Given — write a real file with invalid JSON
        val cacheFile = File(fakeStorageDir, "$fakeRemoteConfigurationId.json")
        cacheFile.writeText("not-valid-json{{{")
        testedService = RemoteConfigServiceImpl(
            remoteConfigurationId = fakeRemoteConfigurationId,
            remoteConfigurationEndpoint = fakeEndpoint,
            fetcher = mockFetcher,
            storageDir = fakeStorageDir,
            executor = mockExecutor,
            internalLogger = mockInternalLogger
        )

        // When
        val result = testedService.getCurrentConfig()

        // Then
        assertThat(result).isNull()
        assertThat(cacheFile).doesNotExist()
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            message = RemoteConfigServiceImpl.ERROR_PARSE,
            throwableClass = JsonParseException::class.java
        )
    }

    // endregion

    // region syncWithRemote() — success

    @Test
    fun `M update cachedConfig and write to disk W syncWithRemote() { successful fetch }`(
        @Forgery fakeRemoteConfiguration: RemoteConfiguration
    ) {
        // Given
        testedService = buildService()
        val fakeJson = fakeRemoteConfiguration.toJson().toString()
        whenever(mockFetcher.fetch(any())).doReturn(fakeJson)

        // When
        testedService.syncWithRemote()

        // Then — compare JSON output: the generated model uses Number fields that deserialize
        // as LazilyParsedNumber (no equals() override), so object equality is unreliable.
        // Comparing serialized JSON verifies the full roundtrip without false negatives.
        assertThat(testedService.getCurrentConfig()?.toJson()?.toString()).isEqualTo(fakeJson)
        verify(mockFileReaderWriter).writeData(
            file = any(),
            data = eq(fakeJson.toByteArray(Charsets.UTF_8)),
            append = eq(false),
            telemetryContext = any()
        )
    }

    @Test
    fun `M build correct URL W syncWithRemote()`(
        @Forgery fakeRemoteConfiguration: RemoteConfiguration
    ) {
        // Given
        testedService = buildService()
        val expectedUrl = fakeEndpoint.newBuilder()
            .addPathSegment("v1")
            .addPathSegment("$fakeRemoteConfigurationId.json")
            .build()
        whenever(mockFetcher.fetch(expectedUrl)).doReturn(fakeRemoteConfiguration.toJson().toString())

        // When
        testedService.syncWithRemote()

        // Then
        verify(mockFetcher).fetch(expectedUrl)
    }

    @Test
    fun `M create storageDir and write config W syncWithRemote() { storageDir does not exist yet }`(
        @Forgery fakeRemoteConfiguration: RemoteConfiguration
    ) {
        // Given — use a non-existent subdirectory as storageDir
        val nonExistentStorageDir = File(fakeStorageDir, "not-yet-created")
        assertThat(nonExistentStorageDir).doesNotExist()
        val fakeJson = fakeRemoteConfiguration.toJson().toString()
        whenever(mockFetcher.fetch(any())).doReturn(fakeJson)
        testedService = RemoteConfigServiceImpl(
            remoteConfigurationId = fakeRemoteConfigurationId,
            remoteConfigurationEndpoint = fakeEndpoint,
            fetcher = mockFetcher,
            storageDir = nonExistentStorageDir,
            executor = mockExecutor,
            internalLogger = mockInternalLogger
            // use real FileReaderWriter so the actual disk write path is exercised
        )

        // When
        testedService.syncWithRemote()

        // Then
        val cacheFile = File(nonExistentStorageDir, "$fakeRemoteConfigurationId.json")
        assertThat(cacheFile).exists()
        assertThat(testedService.getCurrentConfig()?.toJson()?.toString()).isEqualTo(fakeJson)
    }

    // endregion

    // region syncWithRemote() — fetch failure

    @Test
    fun `M not update cache W syncWithRemote() { fetch returns null }`() {
        // Given
        testedService = buildService()
        whenever(mockFetcher.fetch(any())).doReturn(null)

        // When
        testedService.syncWithRemote()

        // Then
        assertThat(testedService.getCurrentConfig()).isNull()
        verify(mockFileReaderWriter, never()).writeData(any(), any(), any(), any())
    }

    // endregion

    // region syncWithRemote() — parse failure

    @Test
    fun `M not update cache or disk W syncWithRemote() { response with unknown enum value }`() {
        // Given — valid JSON but with an unknown vitalsUpdateFrequency enum value
        testedService = buildService()
        val fakeJson = """
            {
              "platform": "android",
              "rum": {
                "applicationId": "38030dde-f9f9-4e52-9443-b9804a030080",
                "vitalsUpdateFrequency": "supersonic"
              }
            }
        """.trimIndent()
        whenever(mockFetcher.fetch(any())).doReturn(fakeJson)

        // When
        testedService.syncWithRemote()

        // Then
        assertThat(testedService.getCurrentConfig()).isNull()
        verify(mockFileReaderWriter, never()).writeData(any(), any(), any(), any())
        verify(mockFetcher).evictCache()
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            message = RemoteConfigServiceImpl.ERROR_PARSE,
            throwableClass = NoSuchElementException::class.java
        )
    }

    @Test
    fun `M return null W getCurrentConfig() { cached file has unknown enum value }`() {
        // Given — write a real file with an unknown enum value
        val cacheFile = File(fakeStorageDir, "$fakeRemoteConfigurationId.json")
        cacheFile.writeText(
            """
            {
              "platform": "android",
              "rum": {
                "applicationId": "38030dde-f9f9-4e52-9443-b9804a030080",
                "vitalsUpdateFrequency": "supersonic"
              }
            }
            """.trimIndent()
        )
        testedService = RemoteConfigServiceImpl(
            remoteConfigurationId = fakeRemoteConfigurationId,
            remoteConfigurationEndpoint = fakeEndpoint,
            fetcher = mockFetcher,
            storageDir = fakeStorageDir,
            executor = mockExecutor,
            internalLogger = mockInternalLogger
        )

        // When
        val result = testedService.getCurrentConfig()

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            message = RemoteConfigServiceImpl.ERROR_PARSE,
            throwableClass = NoSuchElementException::class.java
        )
    }

    @Test
    fun `M not update cache or disk W syncWithRemote() { invalid JSON response }`() {
        // Given
        testedService = buildService()
        whenever(mockFetcher.fetch(any())).doReturn("not-valid-json{{{")

        // When
        testedService.syncWithRemote()

        // Then
        assertThat(testedService.getCurrentConfig()).isNull()
        verify(mockFileReaderWriter, never()).writeData(any(), any(), any(), any())
        verify(mockFetcher).evictCache()
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            message = RemoteConfigServiceImpl.ERROR_PARSE,
            throwableClass = JsonParseException::class.java
        )
    }

    // endregion

    // region syncWithRemote() — disk write failure

    @Test
    fun `M not update cachedConfig W syncWithRemote() { disk write fails }`(
        @Forgery fakeRemoteConfiguration: RemoteConfiguration
    ) {
        // Given
        testedService = buildService()
        val fakeJson = fakeRemoteConfiguration.toJson().toString()
        whenever(mockFetcher.fetch(any())).doReturn(fakeJson)
        whenever(mockFileReaderWriter.writeData(any(), any(), any(), any())).doReturn(false)

        // When
        testedService.syncWithRemote()

        // Then
        assertThat(testedService.getCurrentConfig()).isNull()
    }

    // endregion

    // region stop()

    @Test
    fun `M release fetcher resources W stop()`() {
        // Given
        testedService = buildService()

        // When
        testedService.stop()

        // Then
        verify(mockFetcher).release()
    }

    // endregion
}
