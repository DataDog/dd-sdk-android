/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import android.app.Application
import com.datadog.android.BuildConfig
import com.datadog.android.DatadogConfig
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.DataOkHttpUploader
import com.datadog.android.core.internal.net.DataOkHttpUploaderTest
import com.datadog.android.rum.RumAttributes
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumOkHttpUploaderTest : DataOkHttpUploaderTest<RumOkHttpUploader>() {
    lateinit var fakePackageName: String
    lateinit var fakePackageVersion: String
    lateinit var mockAppContext: Application

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        fakePackageName = forge.anAlphabeticalString()
        fakePackageVersion = forge.aStringMatching("\\d(\\.\\d){3}")
        mockAppContext = mockContext(fakePackageName, fakePackageVersion)
        CoreFeature.initialize(
            mockAppContext, DatadogConfig.CoreConfig(
                needsClearTextHttp = forge.aBool(),
                serviceName = forge.anAlphabeticalString()
            )
        )
    }

    @AfterEach
    override fun `tear down`() {
        super.`tear down`()
        CoreFeature.stop()
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
        return "\\/v1\\/input/${fakeToken}\\" +
            "?${DataOkHttpUploader.BATCH_TIME}=\\d+" +
            "&${RumOkHttpUploader.QP_SOURCE}=android" +
            "&${RumOkHttpUploader.QP_TAGS}=" +
            "${RumAttributes.SERVICE_NAME}:${CoreFeature.serviceName}," +
            "${RumAttributes.APPLICATION_VERSION}:${CoreFeature.packageVersion}," +
            "${RumAttributes.SDK_VERSION}:${BuildConfig.VERSION_NAME}$"
    }
}
