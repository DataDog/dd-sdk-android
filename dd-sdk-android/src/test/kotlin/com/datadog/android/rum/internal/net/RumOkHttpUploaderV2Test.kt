/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import android.app.Application
import com.datadog.android.core.internal.net.DataOkHttpUploaderV2
import com.datadog.android.core.internal.net.DataOkHttpUploaderV2Test
import com.datadog.android.core.internal.system.AppVersionProvider
import com.datadog.android.rum.RumAttributes
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Call
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumOkHttpUploaderV2Test : DataOkHttpUploaderV2Test<RumOkHttpUploaderV2>() {

    @Mock
    lateinit var mockAppVersionProvider: AppVersionProvider

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        whenever(mockAppVersionProvider.version) doReturn appContext.fakeVersionName
    }

    override fun buildTestedInstance(callFactory: Call.Factory): RumOkHttpUploaderV2 {
        return RumOkHttpUploaderV2(
            fakeEndpoint,
            fakeClientToken,
            fakeSource,
            fakeSdkVersion,
            callFactory,
            mockAndroidInfoProvider,
            mockAppVersionProvider
        )
    }

    override fun expectedPath(): String {
        return "/api/v2/rum"
    }

    override fun expectedQueryParams(): Map<String, String> {
        // SDK version is also in the header, where it is sanitized. So we should sanitize
        // it here as well.
        val sanitizedSdkVersion =
            fakeSdkVersion.filter { it == '\t' || it in '\u0020' until '\u007F' }
        val tags = mutableListOf(
            "${RumAttributes.SERVICE_NAME}:${coreFeature.fakeServiceName}",
            "${RumAttributes.APPLICATION_VERSION}:${appContext.fakeVersionName}",
            "${RumAttributes.SDK_VERSION}:$sanitizedSdkVersion",
            "${RumAttributes.ENV}:${coreFeature.fakeEnvName}"
        )

        if (appContext.fakeVariant.isNotBlank()) {
            tags.add("${RumAttributes.VARIANT}:${appContext.fakeVariant}")
        }

        return mapOf(
            DataOkHttpUploaderV2.QUERY_PARAM_SOURCE to fakeSource,
            DataOkHttpUploaderV2.QUERY_PARAM_TAGS to tags.joinToString(",")
        )
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)
        val coreFeature = CoreFeatureTestConfiguration(appContext)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, coreFeature)
        }
    }
}
