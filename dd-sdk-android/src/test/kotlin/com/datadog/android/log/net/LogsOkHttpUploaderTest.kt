/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.net

import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.DataOkHttpUploader
import com.datadog.android.core.internal.net.DataOkHttpUploaderTest
import com.datadog.android.log.internal.net.LogsOkHttpUploader
import com.datadog.android.utils.forge.Configurator
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
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class LogsOkHttpUploaderTest : DataOkHttpUploaderTest<LogsOkHttpUploader>() {

    override fun uploader(callFactory: Call.Factory): LogsOkHttpUploader {
        return LogsOkHttpUploader(
            fakeEndpoint,
            fakeToken,
            callFactory
        )
    }

    override fun expectedPath(): String {
        return "/v1/input/$fakeToken"
    }

    override fun expectedQueryParams(): Map<String, String> {
        return mapOf(DataOkHttpUploader.QP_SOURCE to CoreFeature.sourceName)
    }
}
