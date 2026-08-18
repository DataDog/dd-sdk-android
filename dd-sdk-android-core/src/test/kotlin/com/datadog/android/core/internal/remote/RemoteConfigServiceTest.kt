/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.remote

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.remote.model.RemoteConfigSyncMetadata
import com.datadog.android.core.internal.remote.model.RemoteConfiguration
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.google.gson.JsonParseException
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
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
import org.mockito.kotlin.times
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

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @TempDir
    lateinit var fakeStorageDir: File

    @LongForgery(min = 1_000_000L)
    var fakeNow: Long = 0L

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

        whenever(mockTimeProvider.getDeviceTimestampMillis()).doReturn(fakeNow)
    }

    private fun buildService(): RemoteConfigServiceImpl {
        return RemoteConfigServiceImpl(
            remoteConfigurationId = fakeRemoteConfigurationId,
            remoteConfigurationEndpoint = fakeEndpoint,
            fetcher = mockFetcher,
            storageDir = fakeStorageDir,
            executor = mockExecutor,
            internalLogger = mockInternalLogger,
            timeProvider = mockTimeProvider,
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
            internalLogger = mockInternalLogger,
            timeProvider = mockTimeProvider
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
            internalLogger = mockInternalLogger,
            timeProvider = mockTimeProvider
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
        whenever(mockFetcher.fetch(any())).doReturn(
            RemoteConfigFetcher.FetchResult(body = fakeJson, versionId = null, lastModified = null)
        )

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
        whenever(mockFetcher.fetch(expectedUrl)).doReturn(
            RemoteConfigFetcher.FetchResult(
                body = fakeRemoteConfiguration.toJson().toString(),
                versionId = null,
                lastModified = null
            )
        )

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
        whenever(mockFetcher.fetch(any())).doReturn(
            RemoteConfigFetcher.FetchResult(body = fakeJson, versionId = null, lastModified = null)
        )
        testedService = RemoteConfigServiceImpl(
            remoteConfigurationId = fakeRemoteConfigurationId,
            remoteConfigurationEndpoint = fakeEndpoint,
            fetcher = mockFetcher,
            storageDir = nonExistentStorageDir,
            executor = mockExecutor,
            internalLogger = mockInternalLogger,
            timeProvider = mockTimeProvider
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
        whenever(mockFetcher.fetch(any())).doReturn(
            RemoteConfigFetcher.FetchResult(body = fakeJson, versionId = null, lastModified = null)
        )

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
            internalLogger = mockInternalLogger,
            timeProvider = mockTimeProvider
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
        whenever(mockFetcher.fetch(any())).doReturn(
            RemoteConfigFetcher.FetchResult(body = "not-valid-json{{{", versionId = null, lastModified = null)
        )

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
        whenever(mockFetcher.fetch(any())).doReturn(
            RemoteConfigFetcher.FetchResult(body = fakeJson, versionId = null, lastModified = null)
        )
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

    // region getSyncMetadata() — cold start

    @Test
    fun `M return null W getSyncMetadata() { no metadata on disk }`() {
        // Given
        testedService = buildService()

        // When / Then
        assertThat(testedService.getSyncMetadata()).isNull()
    }

    // endregion

    // region getSyncMetadata() — warm start with metadata on disk

    @Test
    fun `M stamp firstApplied and return it W getSyncMetadata() { metadata on disk, firstApplied null }`(
        @Forgery fakeMetadata: RemoteConfigSyncMetadata
    ) {
        // Given — config and metadata on disk, but firstApplied is null
        val configFile = File(fakeStorageDir, "$fakeRemoteConfigurationId.json")
        configFile.writeText(RemoteConfiguration().toJson().toString())
        val metadataNoFirstApplied = fakeMetadata.copy(firstApplied = null)
        val metadataFile = File(fakeStorageDir, "$fakeRemoteConfigurationId.metadata.json")
        metadataFile.writeText(metadataNoFirstApplied.toJson().toString())
        whenever(mockTimeProvider.getDeviceTimestampMillis()).doReturn(fakeNow)

        // executor runs tasks inline (set up in @BeforeEach)
        testedService = RemoteConfigServiceImpl(
            remoteConfigurationId = fakeRemoteConfigurationId,
            remoteConfigurationEndpoint = fakeEndpoint,
            fetcher = mockFetcher,
            storageDir = fakeStorageDir,
            executor = mockExecutor,
            internalLogger = mockInternalLogger,
            timeProvider = mockTimeProvider
        )

        // When
        val result = testedService.getSyncMetadata()

        // Then — firstApplied is stamped with fakeNow and persisted
        assertThat(result).isNotNull()
        assertThat(result!!.firstApplied).isEqualTo(fakeNow)
        assertThat(metadataFile.readText()).contains("\"firstApplied\":$fakeNow")
    }

    @Test
    fun `M return metadata without re-stamping W getSyncMetadata() { firstApplied already set }`(
        @Forgery fakeMetadata: RemoteConfigSyncMetadata,
        @LongForgery(min = 1L) fakeFirstApplied: Long
    ) {
        // Given — metadata on disk with firstApplied already set
        val configFile = File(fakeStorageDir, "$fakeRemoteConfigurationId.json")
        configFile.writeText(RemoteConfiguration().toJson().toString())
        val metadataWithFirstApplied = fakeMetadata.copy(firstApplied = fakeFirstApplied)
        val metadataFile = File(fakeStorageDir, "$fakeRemoteConfigurationId.metadata.json")
        metadataFile.writeText(metadataWithFirstApplied.toJson().toString())

        testedService = RemoteConfigServiceImpl(
            remoteConfigurationId = fakeRemoteConfigurationId,
            remoteConfigurationEndpoint = fakeEndpoint,
            fetcher = mockFetcher,
            storageDir = fakeStorageDir,
            executor = mockExecutor,
            internalLogger = mockInternalLogger,
            timeProvider = mockTimeProvider
        )

        // When
        val result = testedService.getSyncMetadata()

        // Then — firstApplied is NOT overwritten
        assertThat(result!!.firstApplied).isEqualTo(fakeFirstApplied)
        // timeProvider was never called (no stamp needed)
        verify(mockTimeProvider, never()).getDeviceTimestampMillis()
    }

    @Test
    fun `M return null and delete file W getSyncMetadata() { corrupt metadata file }`() {
        // Given
        val configFile = File(fakeStorageDir, "$fakeRemoteConfigurationId.json")
        configFile.writeText(RemoteConfiguration().toJson().toString())
        val metadataFile = File(fakeStorageDir, "$fakeRemoteConfigurationId.metadata.json")
        metadataFile.writeText("not-valid-json{{{")

        testedService = RemoteConfigServiceImpl(
            remoteConfigurationId = fakeRemoteConfigurationId,
            remoteConfigurationEndpoint = fakeEndpoint,
            fetcher = mockFetcher,
            storageDir = fakeStorageDir,
            executor = mockExecutor,
            internalLogger = mockInternalLogger,
            timeProvider = mockTimeProvider
        )

        // When
        val result = testedService.getSyncMetadata()

        // Then
        assertThat(result).isNull()
        assertThat(metadataFile).doesNotExist()
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            message = RemoteConfigServiceImpl.ERROR_PARSE_METADATA,
            throwableClass = JsonParseException::class.java
        )
    }

    // endregion

    // region syncWithRemote() — metadata written on fetch

    @Test
    fun `M write metadata file W syncWithRemote() { successful fetch }`(
        @Forgery fakeRemoteConfiguration: RemoteConfiguration,
        @StringForgery fakeVersionId: String
    ) {
        // Given
        testedService = buildService()
        val fakeJson = fakeRemoteConfiguration.toJson().toString()
        val fakeFetchResult = RemoteConfigFetcher.FetchResult(
            body = fakeJson,
            versionId = fakeVersionId,
            lastModified = 1_700_000_000_000L
        )
        whenever(mockFetcher.fetch(any())).doReturn(fakeFetchResult)
        whenever(mockTimeProvider.getDeviceTimestampMillis()).doReturn(fakeNow)

        // When
        testedService.syncWithRemote()

        // Then — metadata written with firstApplied = null and correct fields
        // writeData called twice: once for config, once for metadata
        verify(mockFileReaderWriter, times(2)).writeData(any(), any(), any(), any())
        val meta = testedService.getSyncMetadata()
        assertThat(meta).isNotNull()
        assertThat(meta!!.configId).isEqualTo(fakeRemoteConfigurationId)
        assertThat(meta.versionId).isEqualTo(fakeVersionId)
        assertThat(meta.lastModified).isEqualTo(1_700_000_000_000L)
        assertThat(meta.lastSynced).isEqualTo(fakeNow)
        assertThat(meta.firstApplied).isNull()
        assertThat(meta.syncId).isNotEmpty()
    }

    @Test
    fun `M carry over firstApplied W syncWithRemote() { re-fetch returns same versionId }`(
        @Forgery fakeRemoteConfiguration: RemoteConfiguration,
        @Forgery fakeMetadata: RemoteConfigSyncMetadata,
        @LongForgery(min = 1L) fakeFirstApplied: Long,
        @StringForgery fakeVersionId: String
    ) {
        // Given — a previously stamped metadata on disk for versionId X, and the CDN re-serves X
        val configFile = File(fakeStorageDir, "$fakeRemoteConfigurationId.json")
        configFile.writeText(RemoteConfiguration().toJson().toString())
        val stamped = fakeMetadata.copy(versionId = fakeVersionId, firstApplied = fakeFirstApplied)
        val metadataFile = File(fakeStorageDir, "$fakeRemoteConfigurationId.metadata.json")
        metadataFile.writeText(stamped.toJson().toString())
        whenever(mockFetcher.fetch(any())).doReturn(
            RemoteConfigFetcher.FetchResult(
                body = fakeRemoteConfiguration.toJson().toString(),
                versionId = fakeVersionId,
                lastModified = null
            )
        )
        testedService = RemoteConfigServiceImpl(
            remoteConfigurationId = fakeRemoteConfigurationId,
            remoteConfigurationEndpoint = fakeEndpoint,
            fetcher = mockFetcher,
            storageDir = fakeStorageDir,
            executor = mockExecutor,
            internalLogger = mockInternalLogger,
            timeProvider = mockTimeProvider
        )

        // When
        testedService.syncWithRemote()

        // Then — firstApplied survives the re-fetch of the same version
        val meta = testedService.getSyncMetadata()
        assertThat(meta).isNotNull()
        assertThat(meta!!.versionId).isEqualTo(fakeVersionId)
        assertThat(meta.firstApplied).isEqualTo(fakeFirstApplied)
    }

    @Test
    fun `M reset firstApplied W syncWithRemote() { fetch returns different versionId }`(
        @Forgery fakeRemoteConfiguration: RemoteConfiguration,
        @Forgery fakeMetadata: RemoteConfigSyncMetadata,
        @LongForgery(min = 1L) fakeFirstApplied: Long,
        @StringForgery fakeOldVersionId: String,
        @StringForgery fakeNewVersionId: String
    ) {
        // Given — a stamped metadata for the old version, and the CDN publishes a new version
        val configFile = File(fakeStorageDir, "$fakeRemoteConfigurationId.json")
        configFile.writeText(RemoteConfiguration().toJson().toString())
        val stamped = fakeMetadata.copy(versionId = fakeOldVersionId, firstApplied = fakeFirstApplied)
        val metadataFile = File(fakeStorageDir, "$fakeRemoteConfigurationId.metadata.json")
        metadataFile.writeText(stamped.toJson().toString())
        whenever(mockFetcher.fetch(any())).doReturn(
            RemoteConfigFetcher.FetchResult(
                body = fakeRemoteConfiguration.toJson().toString(),
                versionId = fakeNewVersionId,
                lastModified = null
            )
        )
        testedService = RemoteConfigServiceImpl(
            remoteConfigurationId = fakeRemoteConfigurationId,
            remoteConfigurationEndpoint = fakeEndpoint,
            fetcher = mockFetcher,
            storageDir = fakeStorageDir,
            executor = mockExecutor,
            internalLogger = mockInternalLogger,
            timeProvider = mockTimeProvider
        )

        // When
        testedService.syncWithRemote()

        // Then — a new version has not been applied yet, so firstApplied resets
        val meta = testedService.getSyncMetadata()
        assertThat(meta!!.versionId).isEqualTo(fakeNewVersionId)
        assertThat(meta.firstApplied).isNull()
    }

    @Test
    fun `M reset firstApplied W syncWithRemote() { both old and new versionId are null }`(
        @Forgery fakeRemoteConfiguration: RemoteConfiguration,
        @Forgery fakeMetadata: RemoteConfigSyncMetadata,
        @LongForgery(min = 1L) fakeFirstApplied: Long
    ) {
        // Given — a stamped metadata with no versionId (CDN omitted x-amz-version-id previously),
        // and a genuine re-fetch that also comes back with no versionId
        val configFile = File(fakeStorageDir, "$fakeRemoteConfigurationId.json")
        configFile.writeText(RemoteConfiguration().toJson().toString())
        val stamped = fakeMetadata.copy(versionId = null, firstApplied = fakeFirstApplied)
        val metadataFile = File(fakeStorageDir, "$fakeRemoteConfigurationId.metadata.json")
        metadataFile.writeText(stamped.toJson().toString())
        whenever(mockFetcher.fetch(any())).doReturn(
            RemoteConfigFetcher.FetchResult(
                body = fakeRemoteConfiguration.toJson().toString(),
                versionId = null,
                lastModified = null
            )
        )
        testedService = RemoteConfigServiceImpl(
            remoteConfigurationId = fakeRemoteConfigurationId,
            remoteConfigurationEndpoint = fakeEndpoint,
            fetcher = mockFetcher,
            storageDir = fakeStorageDir,
            executor = mockExecutor,
            internalLogger = mockInternalLogger,
            timeProvider = mockTimeProvider
        )

        // When
        testedService.syncWithRemote()

        // Then — without a real versionId there is no way to know this is the same content,
        // so firstApplied must not be carried over
        val meta = testedService.getSyncMetadata()
        assertThat(meta!!.versionId).isNull()
        assertThat(meta.firstApplied).isNull()
    }

    @Test
    fun `M not write metadata W syncWithRemote() { config write fails }`(
        @Forgery fakeRemoteConfiguration: RemoteConfiguration
    ) {
        // Given — config write returns false (disk full etc.)
        testedService = buildService()
        val fakeFetchResult = RemoteConfigFetcher.FetchResult(
            body = fakeRemoteConfiguration.toJson().toString(),
            versionId = null,
            lastModified = null
        )
        whenever(mockFetcher.fetch(any())).doReturn(fakeFetchResult)
        whenever(mockFileReaderWriter.writeData(any(), any(), any(), any())).doReturn(false)

        // When
        testedService.syncWithRemote()

        // Then — metadata stays null
        assertThat(testedService.getSyncMetadata()).isNull()
    }

    @Test
    fun `M not write metadata W syncWithRemote() { fetch returns null }`() {
        // Given
        testedService = buildService()
        whenever(mockFetcher.fetch(any())).doReturn(null)

        // When
        testedService.syncWithRemote()

        // Then
        assertThat(testedService.getSyncMetadata()).isNull()
        verify(mockFileReaderWriter, never()).writeData(any(), any(), any(), any())
    }

    // endregion
}
