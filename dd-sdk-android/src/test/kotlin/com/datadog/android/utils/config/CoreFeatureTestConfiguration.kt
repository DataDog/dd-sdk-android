/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.config

import android.content.Context
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.internal.user.MutableUserInfoProvider
import com.datadog.android.privacy.TrackingConsent
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.Forge
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor

internal class CoreFeatureTestConfiguration<T : Context>(
    val appContext: ApplicationContextTestConfiguration<T>
) : TestConfiguration {

    lateinit var fakeServiceName: String
    lateinit var fakeEnvName: String
    lateinit var fakeRumApplicationId: String
    lateinit var fakeSourceName: String

    lateinit var mockUploadExecutor: ScheduledThreadPoolExecutor
    lateinit var mockPersistenceExecutor: ExecutorService

    lateinit var mockTimeProvider: TimeProvider
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider
    lateinit var mockSystemInfoProvider: SystemInfoProvider
    lateinit var mockUserInfoProvider: MutableUserInfoProvider
    lateinit var mockTrackingConsentProvider: ConsentProvider

    // region CoreFeatureTestConfiguration

    override fun setUp(forge: Forge) {
        createFakeInfo(forge)
        createMocks()
        configureCoreFeature()
    }

    override fun tearDown(forge: Forge) {
        CoreFeature.stop()
    }

    // endregion

    // region Internal

    private fun createFakeInfo(forge: Forge) {
        fakeEnvName = forge.anAlphabeticalString()
        fakeServiceName = forge.anAlphabeticalString()
        fakeRumApplicationId = forge.getForgery<UUID>().toString()
        fakeSourceName = forge.anAlphabeticalString()
    }

    private fun createMocks() {
        mockPersistenceExecutor = mock()
        mockUploadExecutor = mock()

        mockTimeProvider = mock()
        mockNetworkInfoProvider = mock()
        mockSystemInfoProvider = mock()
        mockUserInfoProvider = mock()
        mockTrackingConsentProvider = mock { on { getConsent() } doReturn TrackingConsent.PENDING }
    }

    private fun configureCoreFeature() {
        CoreFeature.isMainProcess = true
        CoreFeature.envName = fakeEnvName
        CoreFeature.serviceName = fakeServiceName
        CoreFeature.packageName = appContext.fakePackageName
        CoreFeature.packageVersion = appContext.fakeVersionName
        CoreFeature.variant = appContext.fakeVariant
        CoreFeature.rumApplicationId = fakeRumApplicationId
        CoreFeature.sourceName = fakeSourceName

        CoreFeature.persistenceExecutorService = mockPersistenceExecutor
        CoreFeature.uploadExecutorService = mockUploadExecutor

        CoreFeature.timeProvider = mockTimeProvider
        CoreFeature.networkInfoProvider = mockNetworkInfoProvider
        CoreFeature.systemInfoProvider = mockSystemInfoProvider
        CoreFeature.userInfoProvider = mockUserInfoProvider
        CoreFeature.trackingConsentProvider = mockTrackingConsentProvider
    }

    // endregion
}
