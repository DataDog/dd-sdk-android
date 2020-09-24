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
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.RegexForgery
import fr.xgouchet.elmyr.annotation.StringForgery
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

    @RegexForgery("([a-z]+\\.)+[a-z]+")
    lateinit var fakePackageName: String
    @RegexForgery("\\d(\\.\\d){3}")
    lateinit var fakePackageVersion: String

    @StringForgery
    lateinit var fakeEnvName: String
    lateinit var mockAppContext: Application

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        RumFeature.envName = fakeEnvName
        mockAppContext = mockContext(fakePackageName, fakePackageVersion)
        CoreFeature.initialize(
            mockAppContext,
            DatadogConfig.CoreConfig(needsClearTextHttp = forge.aBool())
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
        return "^\\/v1\\/input/$fakeToken" +
            "\\?${DataOkHttpUploader.QP_BATCH_TIME}=\\d+" +
            "&${DataOkHttpUploader.QP_SOURCE}=${DataOkHttpUploader.DD_SOURCE_ANDROID}" +
            "&${RumOkHttpUploader.QP_TAGS}=" +
            "${RumAttributes.SERVICE_NAME}:$fakePackageName," +
            "${RumAttributes.APPLICATION_VERSION}:$fakePackageVersion," +
            "${RumAttributes.SDK_VERSION}:${BuildConfig.VERSION_NAME}," +
            "${RumAttributes.ENV}:$fakeEnvName" +
            "$"
    }
}
