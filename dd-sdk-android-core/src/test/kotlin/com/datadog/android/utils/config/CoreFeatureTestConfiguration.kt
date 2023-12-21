/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.config

import android.content.Context
import com.datadog.android.DatadogSite
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.internal.ContextProvider
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.DefaultFirstPartyHostHeaderTypeResolver
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.system.AndroidInfoProvider
import com.datadog.android.core.internal.system.AppVersionProvider
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.user.MutableUserInfoProvider
import com.datadog.android.privacy.TrackingConsent
import com.datadog.tools.unit.extensions.config.MockTestConfiguration
import com.datadog.tools.unit.forge.exhaustiveAttributes
import com.lyft.kronos.KronosClock
import fr.xgouchet.elmyr.Forge
import okhttp3.OkHttpClient
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.lang.ref.WeakReference
import java.nio.file.Files
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor

internal class CoreFeatureTestConfiguration<T : Context>(
    val appContext: ApplicationContextTestConfiguration<T>
) : MockTestConfiguration<CoreFeature>(CoreFeature::class.java) {

    lateinit var fakeServiceName: String
    lateinit var fakeEnvName: String
    lateinit var fakeSourceName: String
    lateinit var fakeClientToken: String
    lateinit var fakeSdkVersion: String
    lateinit var fakeStorageDir: File
    lateinit var fakeUploadFrequency: UploadFrequency
    lateinit var fakeSite: DatadogSite
    lateinit var fakeFeaturesContext: MutableMap<String, Map<String, Any?>>
    lateinit var fakeFilePersistenceConfig: FilePersistenceConfig
    lateinit var fakeBatchSize: BatchSize
    var fakeBuildId: String? = null

    lateinit var mockUploadExecutor: ScheduledThreadPoolExecutor
    lateinit var mockOkHttpClient: OkHttpClient
    lateinit var mockPersistenceExecutor: ExecutorService
    lateinit var mockKronosClock: KronosClock
    lateinit var mockContextRef: WeakReference<Context?>
    lateinit var mockFirstPartyHostHeaderTypeResolver: DefaultFirstPartyHostHeaderTypeResolver

    lateinit var mockTimeProvider: TimeProvider
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider
    lateinit var mockSystemInfoProvider: SystemInfoProvider
    lateinit var mockUserInfoProvider: MutableUserInfoProvider
    lateinit var mockTrackingConsentProvider: ConsentProvider
    lateinit var mockAndroidInfoProvider: AndroidInfoProvider
    lateinit var mockAppVersionProvider: AppVersionProvider
    lateinit var mockContextProvider: ContextProvider

    // region CoreFeatureTestConfiguration

    override fun setUp(forge: Forge) {
        super.setUp(forge)
        createFakeInfo(forge)
        createMocks()
        configureCoreFeature()
    }

    override fun tearDown(forge: Forge) {
        fakeStorageDir.deleteRecursively()
    }

    // endregion

    // region Internal

    private fun createFakeInfo(forge: Forge) {
        fakeEnvName = forge.anAlphabeticalString()
        fakeServiceName = forge.anAlphabeticalString()
        fakeSourceName = forge.anAlphabeticalString()
        fakeClientToken = forge.anHexadecimalString().lowercase(Locale.US)
        fakeSdkVersion = forge.aStringMatching("[0-9](\\.[0-9]{1,2}){1,3}")
        fakeStorageDir = Files.createTempDirectory(forge.anHexadecimalString()).toFile()
        fakeUploadFrequency = forge.aValueFrom(UploadFrequency::class.java)
        fakeSite = forge.aValueFrom(DatadogSite::class.java)
        // building nested maps with default size slows down tests quite a lot, so will use
        // an explicit small size
        fakeFeaturesContext = forge.aMap(size = 2) {
            forge.anAlphabeticalString() to forge.exhaustiveAttributes()
        }.toMutableMap()
        fakeFilePersistenceConfig = forge.getForgery()
        fakeBatchSize = forge.aValueFrom(BatchSize::class.java)
        fakeBuildId = forge.aNullable { getForgery<UUID>().toString() }
    }

    private fun createMocks() {
        mockPersistenceExecutor = mock()
        mockUploadExecutor = mock()
        mockOkHttpClient = mock()
        mockKronosClock = mock()
        // Mockito cannot mock WeakReference by some reason
        mockContextRef = WeakReference(appContext.mockInstance)
        mockFirstPartyHostHeaderTypeResolver = mock()

        mockTimeProvider = mock()
        mockNetworkInfoProvider = mock()
        mockSystemInfoProvider = mock()
        mockUserInfoProvider = mock()
        mockAndroidInfoProvider = mock()
        mockTrackingConsentProvider = mock { on { getConsent() } doReturn TrackingConsent.PENDING }
        mockAppVersionProvider = mock { on { version } doReturn appContext.fakeVersionName }
        mockContextProvider = mock()
    }

    private fun configureCoreFeature() {
        whenever(mockInstance.isMainProcess) doReturn true
        whenever(mockInstance.envName) doReturn fakeEnvName
        whenever(mockInstance.serviceName) doReturn fakeServiceName
        whenever(mockInstance.packageName) doReturn appContext.fakePackageName
        whenever(mockInstance.packageVersionProvider) doReturn mockAppVersionProvider
        whenever(mockInstance.variant) doReturn appContext.fakeVariant
        whenever(mockInstance.sourceName) doReturn fakeSourceName
        whenever(mockInstance.clientToken) doReturn fakeClientToken
        whenever(mockInstance.sdkVersion) doReturn fakeSdkVersion
        whenever(mockInstance.storageDir) doReturn fakeStorageDir
        whenever(mockInstance.uploadFrequency) doReturn fakeUploadFrequency
        whenever(mockInstance.site) doReturn fakeSite
        whenever(mockInstance.appBuildId) doReturn fakeBuildId
        whenever(mockInstance.featuresContext) doReturn fakeFeaturesContext

        whenever(mockInstance.persistenceExecutorService) doReturn mockPersistenceExecutor
        whenever(mockInstance.uploadExecutorService) doReturn mockUploadExecutor
        whenever(mockInstance.okHttpClient) doReturn mockOkHttpClient
        whenever(mockInstance.kronosClock) doReturn mockKronosClock
        whenever(mockInstance.contextRef) doReturn mockContextRef
        whenever(mockInstance.firstPartyHostHeaderTypeResolver) doReturn mockFirstPartyHostHeaderTypeResolver

        whenever(mockInstance.timeProvider) doReturn mockTimeProvider
        whenever(mockInstance.networkInfoProvider) doReturn mockNetworkInfoProvider
        whenever(mockInstance.systemInfoProvider) doReturn mockSystemInfoProvider
        whenever(mockInstance.userInfoProvider) doReturn mockUserInfoProvider
        whenever(mockInstance.trackingConsentProvider) doReturn mockTrackingConsentProvider
        whenever(mockInstance.androidInfoProvider) doReturn mockAndroidInfoProvider
        whenever(mockInstance.contextProvider) doReturn mockContextProvider

        whenever(mockInstance.buildFilePersistenceConfig()) doReturn fakeFilePersistenceConfig.copy(
            recentDelayMs = fakeBatchSize.windowDurationMs
        )
    }

    // endregion
}
