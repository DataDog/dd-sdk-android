/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import android.app.Application
import com.datadog.android.BuildConfig
import com.datadog.android.core.internal.net.DataOkHttpUploader
import com.datadog.android.core.internal.net.DataOkHttpUploaderTest
import com.datadog.android.rum.RumAttributes
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.junit.jupiter.api.BeforeEach
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
internal class RumOkHttpUploaderTest : DataOkHttpUploaderTest<RumOkHttpUploader>() {

    lateinit var mockAppContext: Application

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        mockAppContext = mockContext("fakePackageName", "fakePackageVersion")
    }

    override fun uploader(): RumOkHttpUploader {
        return RumOkHttpUploader(
            fakeEndpoint,
            fakeToken,
            OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_TEST_MS, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT_TEST_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(TIMEOUT_TEST_MS, TimeUnit.MILLISECONDS)
                .build()
        )
    }

    override fun urlFormat(): String {
        return RumOkHttpUploader.UPLOAD_URL
    }

    override fun expectedPathRegex(): String {
        return "^\\/v1\\/input/$fakeToken" +
            "\\?${DataOkHttpUploader.QP_BATCH_TIME}=\\d+" +
            "&${DataOkHttpUploader.QP_SOURCE}=${coreFeature.fakeSourceName}" +
            "&${RumOkHttpUploader.QP_TAGS}=" +
            "${RumAttributes.SERVICE_NAME}:${coreFeature.fakeServiceName}," +
            "${RumAttributes.APPLICATION_VERSION}:${coreFeature.fakePackageVersion}," +
            "${RumAttributes.SDK_VERSION}:${BuildConfig.SDK_VERSION_NAME}," +
            "${RumAttributes.ENV}:${coreFeature.fakeEnvName}," +
            "${RumAttributes.VARIANT}:${coreFeature.fakeVariant}" +
            "$"
    }

    companion object {
        val coreFeature = CoreFeatureTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(coreFeature)
        }
    }
}
