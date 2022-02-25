/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.net

import android.content.Context
import com.datadog.android.core.internal.net.DataOkHttpUploaderTest
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Call
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
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
internal class TracesOkHttpUploaderTest : DataOkHttpUploaderTest<TracesOkHttpUploader>() {

    override fun uploader(callFactory: Call.Factory): TracesOkHttpUploader {
        return TracesOkHttpUploader(
            fakeEndpoint,
            fakeToken,
            callFactory,
            mockAndroidInfoProvider
        )
    }

    override fun expectedPath(): String {
        return "/v1/input/$fakeToken"
    }

    override fun expectedQueryParams(): Map<String, String> {
        return emptyMap()
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Context::class.java)
        val coreFeature = CoreFeatureTestConfiguration(appContext)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, coreFeature)
        }
    }
}
