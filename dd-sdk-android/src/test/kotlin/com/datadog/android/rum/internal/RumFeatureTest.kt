/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum.internal

import android.app.Application
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.core.internal.data.upload.DataUploadHandlerThread
import com.datadog.android.core.internal.domain.AsyncWriterFilePersistenceStrategy
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.instrumentation.TrackingStrategy
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.SystemOutputExtension
import com.datadog.tools.unit.getFieldValue
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.net.URL
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class),
    ExtendWith(SystemOutputExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumFeatureTest {

    lateinit var mockAppContext: Application

    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider
    @Mock
    lateinit var mockSystemInfoProvider: SystemInfoProvider
    @Mock
    lateinit var mockTimeProvider: TimeProvider
    @Mock
    lateinit var mockUserInfoProvider: UserInfoProvider
    @Mock
    lateinit var mockOkHttpClient: OkHttpClient

    lateinit var fakeConfig: DatadogConfig.RumConfig

    lateinit var fakePackageName: String
    lateinit var fakePackageVersion: String

    @TempDir
    lateinit var rootDir: File

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeConfig = DatadogConfig.RumConfig(
            clientToken = forge.anHexadecimalString(),
            applicationId = forge.getForgery(),
            endpointUrl = forge.getForgery<URL>().toString(),
            serviceName = forge.anAlphabeticalString(),
            envName = forge.anAlphabeticalString()
        )

        fakePackageName = forge.anAlphabeticalString()
        fakePackageVersion = forge.aStringMatching("\\d(\\.\\d){3}")

        mockAppContext = mockContext(fakePackageName, fakePackageVersion)
        whenever(mockAppContext.filesDir).thenReturn(rootDir)
        whenever(mockAppContext.applicationContext) doReturn mockAppContext
    }

    @AfterEach
    fun `tear down`() {
        RumFeature.stop()
    }

    @Test
    fun `initializes GlobalRum context`() {
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider
        )

        val context = GlobalRum.getRumContext()
        assertThat(context.applicationId).isEqualTo(fakeConfig.applicationId)
    }

    @Test
    fun `initializes persistence strategy`() {
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider
        )

        val persistenceStrategy = RumFeature.persistenceStrategy
        assertThat(persistenceStrategy)
            .isInstanceOf(AsyncWriterFilePersistenceStrategy::class.java)
        val reader = RumFeature.persistenceStrategy.getReader()
        val suffix: String = reader.getFieldValue("suffix")
        assertThat(suffix).isEqualTo("")
        val prefix: String = reader.getFieldValue("prefix")
        assertThat(prefix).isEqualTo("")
    }

    @Test
    fun `initializes uploader thread`() {
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider
        )

        val uploadHandlerThread = RumFeature.uploadHandlerThread

        assertThat(uploadHandlerThread)
            .isInstanceOf(DataUploadHandlerThread::class.java)
    }

    @Test
    fun `initializes from configuration`() {
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider
        )

        val clientToken = RumFeature.clientToken
        val endpointUrl = RumFeature.endpointUrl
        val serviceName = RumFeature.serviceName

        assertThat(clientToken).isEqualTo(fakeConfig.clientToken)
        assertThat(endpointUrl).isEqualTo(fakeConfig.endpointUrl)
        assertThat(serviceName).isEqualTo(fakeConfig.serviceName)
    }

    @Test
    fun `ignores if initialize called more than once`(forge: Forge) {
        Datadog.setVerbosity(android.util.Log.VERBOSE)
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider
        )
        val persistenceStrategy = RumFeature.persistenceStrategy
        val uploadHandlerThread = RumFeature.uploadHandlerThread
        val clientToken = RumFeature.clientToken
        val endpointUrl = RumFeature.endpointUrl
        val serviceName = RumFeature.serviceName

        fakeConfig = DatadogConfig.RumConfig(
            clientToken = forge.anHexadecimalString(),
            applicationId = forge.getForgery(),
            endpointUrl = forge.getForgery<URL>().toString(),
            serviceName = forge.anAlphabeticalString(),
            envName = forge.anAlphabeticalString(),
            trackGestures = forge.aBool(),
            trackActivitiesAsScreens = forge.aBool(),
            trackFragmentsAsScreens = forge.aBool()
        )
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider
        )
        val persistenceStrategy2 = RumFeature.persistenceStrategy
        val uploadHandlerThread2 = RumFeature.uploadHandlerThread
        val clientToken2 = RumFeature.clientToken
        val endpointUrl2 = RumFeature.endpointUrl
        val serviceName2 = RumFeature.serviceName

        assertThat(persistenceStrategy).isSameAs(persistenceStrategy2)
        assertThat(uploadHandlerThread).isSameAs(uploadHandlerThread2)
        assertThat(clientToken).isSameAs(clientToken2)
        assertThat(endpointUrl).isSameAs(endpointUrl2)
        assertThat(serviceName).isSameAs(serviceName2)

        verify(mockAppContext, never()).registerActivityLifecycleCallbacks(argThat {
            TrackingStrategy::class.java.isAssignableFrom(this::class.java)
        })
    }

    @Test
    fun `will not register any callback if no instrumentation feature enabled`() {
        // when
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider
        )

        // then
        verify(mockAppContext, never()).registerActivityLifecycleCallbacks(argThat {
            TrackingStrategy::class.java.isAssignableFrom(this::class.java)
        })
    }

    @Test
    fun `will use the right Strategy when tracking gestures enabled`() {
        // given
        fakeConfig = fakeConfig.copy(trackGestures = true)

        // when
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider
        )

        // then
        verify(mockAppContext).registerActivityLifecycleCallbacks(argThat {
            this is TrackingStrategy.GesturesTrackingStrategy
        })
    }

    @Test
    fun `will use the right Strategy if track activities as screens enabled`() {
        // given
        fakeConfig = fakeConfig.copy(trackActivitiesAsScreens = true)

        // when
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider
        )

        // then
        verify(mockAppContext).registerActivityLifecycleCallbacks(argThat {
            this is TrackingStrategy.ActivityTrackingStrategy
        })
    }

    @Test
    fun `will use the right Strategy if track fragments as screens enabled`() {
        // given
        fakeConfig = fakeConfig.copy(trackFragmentsAsScreens = true)

        // when
        RumFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider
        )

        // then
        verify(mockAppContext).registerActivityLifecycleCallbacks(argThat {
            this is TrackingStrategy.FragmentsTrackingStrategy
        })
    }
}
