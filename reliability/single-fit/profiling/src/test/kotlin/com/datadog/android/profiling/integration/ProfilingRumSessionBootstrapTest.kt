/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.integration

import com.datadog.android.api.feature.Feature
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.internal.FeatureContextKeys
import com.datadog.android.internal.data.SharedPreferencesStorage
import com.datadog.android.internal.rum.RumSessionConstants
import com.datadog.android.profiling.ExperimentalProfilingApi
import com.datadog.android.profiling.ProfilingConfiguration
import com.datadog.android.profiling.fixtures.ProfilingFeatureTestHandle
import com.datadog.android.profiling.fixtures.ProfilingStorageFixture
import com.datadog.android.profiling.integration.tests.elmyr.ProfilingIntegrationForgeConfigurator
import com.datadog.android.profiling.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness

@OptIn(ExperimentalProfilingApi::class)
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(ProfilingIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfilingRumSessionBootstrapTest {

    private lateinit var stubSdkCore: StubSDKCore

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)
        // The stub Application has no real SharedPreferences; inject a Mockito mock so
        // ProfilingFeature.onInitialize() can persist its launch flag.
        ProfilingStorageFixture.stubWith(mock<SharedPreferencesStorage>())
    }

    @AfterEach
    fun `tear down`() {
        ProfilingStorageFixture.reset()
    }

    @Test
    fun `M bootstrap profiling with existing session W Rum#enable before Profiling#enable`(
        @StringForgery applicationId: String,
        @StringForgery viewKey: String,
        @StringForgery viewName: String
    ) {
        // Given
        Rum.enable(
            RumConfiguration.Builder(applicationId)
                .trackNonFatalAnrs(false)
                .setSessionSampleRate(100f)
                .build(),
            stubSdkCore
        )
        GlobalRumMonitor.get(stubSdkCore).startView(viewKey, viewName)
        val rumSessionId = stubSdkCore
            .getFeatureContext(Feature.RUM_FEATURE_NAME)[FeatureContextKeys.RUM_SESSION_ID] as? String
        check(!rumSessionId.isNullOrEmpty() && rumSessionId != RumSessionConstants.EMPTY_RUM_SESSION_ID) {
            "Precondition: RUM feature context must hold a real session id before Profiling registers"
        }

        // When
        val handle = ProfilingFeatureTestHandle.create(
            sdkCore = stubSdkCore,
            configuration = ProfilingConfiguration.Builder()
                .setApplicationLaunchSampleRate(100f)
                .setContinuousSampleRate(100f)
                .build()
        )

        // Then — Profiling and its scheduler picked up the existing RUM session.
        assertThat(handle.lastSeenRumSessionId).isEqualTo(rumSessionId)
        assertThat(handle.schedulerSessionId).isEqualTo(rumSessionId)
        assertThat(handle.isSchedulerSessionSampled).isTrue()
    }

    @Test
    fun `M observe session renewal W Rum renews session after Profiling already enabled`(
        @StringForgery applicationId: String,
        @StringForgery viewKey: String,
        @StringForgery viewName: String
    ) {
        // Given
        val handle = ProfilingFeatureTestHandle.create(
            sdkCore = stubSdkCore,
            configuration = ProfilingConfiguration.Builder()
                .setApplicationLaunchSampleRate(100f)
                .setContinuousSampleRate(100f)
                .build()
        )
        check(handle.lastSeenRumSessionId == null) {
            "Precondition: no RUM session should have been observed before RUM is enabled"
        }

        // When
        Rum.enable(
            RumConfiguration.Builder(applicationId)
                .trackNonFatalAnrs(false)
                .setSessionSampleRate(100f)
                .build(),
            stubSdkCore
        )
        GlobalRumMonitor.get(stubSdkCore).startView(viewKey, viewName)

        // Then
        val rumSessionId = stubSdkCore
            .getFeatureContext(Feature.RUM_FEATURE_NAME)[FeatureContextKeys.RUM_SESSION_ID] as? String
        assertThat(rumSessionId).isNotNull()
        assertThat(handle.lastSeenRumSessionId).isEqualTo(rumSessionId)
        assertThat(handle.schedulerSessionId).isEqualTo(rumSessionId)
    }

    companion object {
        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }
    }
}
