/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.config

import android.app.ActivityManager
import android.content.Context
import com.datadog.android.DatadogSite
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.system.AndroidInfoProvider
import com.datadog.android.core.internal.system.AppVersionProvider
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.internal.user.MutableUserInfoProvider
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.tools.unit.extensions.config.MockTestConfiguration
import com.lyft.kronos.KronosClock
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import java.io.File
import java.lang.ref.WeakReference
import java.nio.file.Files
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import okhttp3.OkHttpClient

internal class CoreFeatureTestConfiguration<T : Context>(
    val appContext: ApplicationContextTestConfiguration<T>
) : MockTestConfiguration<CoreFeature>(CoreFeature::class.java) {

    lateinit var fakeServiceName: String
    lateinit var fakeEnvName: String
    lateinit var fakeRumApplicationId: String
    lateinit var fakeSourceName: String
    lateinit var fakeClientToken: String
    lateinit var fakeSdkVersion: String
    lateinit var fakeStorageDir: File
    lateinit var fakeUploadFrequency: UploadFrequency
    lateinit var fakeSite: DatadogSite
    var fakeProcessImportance: Int = 0
    lateinit var fakeFeaturesContext: MutableMap<String, Map<String, Any?>>

    lateinit var mockUploadExecutor: ScheduledThreadPoolExecutor
    lateinit var mockOkHttpClient: OkHttpClient
    lateinit var mockPersistenceExecutor: ExecutorService
    lateinit var mockKronosClock: KronosClock
    lateinit var mockContextRef: WeakReference<Context?>
    lateinit var mockFirstPartyHostDetector: FirstPartyHostDetector

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
        CoreFeature.processImportance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    // endregion

    // region Internal

    private fun createFakeInfo(forge: Forge) {
        fakeEnvName = forge.anAlphabeticalString()
        fakeServiceName = forge.anAlphabeticalString()
        fakeRumApplicationId = forge.getForgery<UUID>().toString()
        fakeSourceName = forge.anAlphabeticalString()
        fakeClientToken = forge.anHexadecimalString().lowercase(Locale.US)
        fakeSdkVersion = forge.aStringMatching("[0-9](\\.[0-9]{1,2}){1,3}")
        fakeStorageDir = Files.createTempDirectory(forge.anHexadecimalString()).toFile()
        fakeUploadFrequency = forge.aValueFrom(UploadFrequency::class.java)
        fakeSite = forge.aValueFrom(DatadogSite::class.java)
        fakeProcessImportance = forge.anInt()
        // building nested maps with default size slows down tests quite a lot, so will use
        // an explicit small size
        fakeFeaturesContext = forge.aMap(size = 2) {
            forge.anAlphabeticalString() to forge.exhaustiveAttributes()
        }.toMutableMap()
    }

    private fun createMocks() {
        mockPersistenceExecutor = mock()
        mockUploadExecutor = mock()
        mockOkHttpClient = mock()
        mockKronosClock = mock()
        // Mockito cannot mock WeakReference by some reason
        mockContextRef = WeakReference(appContext.mockInstance)
        mockFirstPartyHostDetector = mock()

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
        whenever(mockInstance.disableKronosBackgroundSync) doReturn true
        whenever(mockInstance.isMainProcess) doReturn true
        whenever(mockInstance.envName) doReturn fakeEnvName
        whenever(mockInstance.serviceName) doReturn fakeServiceName
        whenever(mockInstance.packageName) doReturn appContext.fakePackageName
        whenever(mockInstance.packageVersionProvider) doReturn mockAppVersionProvider
        whenever(mockInstance.variant) doReturn appContext.fakeVariant
        whenever(mockInstance.rumApplicationId) doReturn fakeRumApplicationId
        whenever(mockInstance.sourceName) doReturn fakeSourceName
        whenever(mockInstance.clientToken) doReturn fakeClientToken
        whenever(mockInstance.sdkVersion) doReturn fakeSdkVersion
        whenever(mockInstance.storageDir) doReturn fakeStorageDir
        whenever(mockInstance.uploadFrequency) doReturn fakeUploadFrequency
        whenever(mockInstance.site) doReturn fakeSite
        whenever(mockInstance.featuresContext) doReturn fakeFeaturesContext
        CoreFeature.processImportance = fakeProcessImportance

        whenever(mockInstance.persistenceExecutorService) doReturn mockPersistenceExecutor
        whenever(mockInstance.uploadExecutorService) doReturn mockUploadExecutor
        whenever(mockInstance.okHttpClient) doReturn mockOkHttpClient
        whenever(mockInstance.kronosClock) doReturn mockKronosClock
        whenever(mockInstance.contextRef) doReturn mockContextRef
        whenever(mockInstance.firstPartyHostDetector) doReturn mockFirstPartyHostDetector

        whenever(mockInstance.timeProvider) doReturn mockTimeProvider
        whenever(mockInstance.networkInfoProvider) doReturn mockNetworkInfoProvider
        whenever(mockInstance.systemInfoProvider) doReturn mockSystemInfoProvider
        whenever(mockInstance.userInfoProvider) doReturn mockUserInfoProvider
        whenever(mockInstance.trackingConsentProvider) doReturn mockTrackingConsentProvider
        whenever(mockInstance.androidInfoProvider) doReturn mockAndroidInfoProvider
        whenever(mockInstance.contextProvider) doReturn mockContextProvider
    }

    // endregion
}
