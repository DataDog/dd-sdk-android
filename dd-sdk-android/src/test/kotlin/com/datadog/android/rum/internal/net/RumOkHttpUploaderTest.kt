/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import android.app.Application
import com.datadog.android.BuildConfig
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.DataOkHttpUploader
import com.datadog.android.core.internal.net.DataOkHttpUploaderTest
import com.datadog.android.rum.RumAttributes
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.android.utils.mockCoreFeature
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Call
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

    @StringForgery(regex = "([a-z]+\\.)+[a-z]+")
    lateinit var fakePackageName: String

    @StringForgery(regex = "\\d(\\.\\d){3}")
    lateinit var fakePackageVersion: String

    @StringForgery
    lateinit var fakeEnvName: String

    @StringForgery
    lateinit var fakeVariant: String

    lateinit var mockAppContext: Application

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        mockAppContext = mockContext(fakePackageName, fakePackageVersion)
        mockCoreFeature(fakePackageName, fakePackageVersion, fakeEnvName, fakeVariant)
    }

    @AfterEach
    override fun `tear down`() {
        super.`tear down`()
        CoreFeature.stop()
    }

    override fun uploader(callFactory: Call.Factory): RumOkHttpUploader {
        return RumOkHttpUploader(
            fakeEndpoint,
            fakeToken,
            callFactory
        )
    }

    override fun expectedPath(): String {
        return "/v1/input/$fakeToken"
    }

    override fun expectedQueryParams(): Map<String, String> {
        val tags = "${RumAttributes.SERVICE_NAME}:$fakePackageName," +
            "${RumAttributes.APPLICATION_VERSION}:$fakePackageVersion," +
            "${RumAttributes.SDK_VERSION}:${BuildConfig.SDK_VERSION_NAME}," +
            "${RumAttributes.ENV}:$fakeEnvName," +
            "${RumAttributes.VARIANT}:$fakeVariant"
        return mapOf(
            DataOkHttpUploader.QP_SOURCE to CoreFeature.sourceName,
            RumOkHttpUploader.QP_TAGS to tags
        )
    }
}
