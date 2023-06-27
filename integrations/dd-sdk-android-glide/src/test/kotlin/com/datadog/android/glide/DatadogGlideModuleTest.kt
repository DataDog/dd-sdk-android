/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.glide

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.engine.executor.GlideExecutor
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.module.AppGlideModule
import com.datadog.android.okhttp.DatadogInterceptor
import com.datadog.android.v2.api.SdkCore
import com.datadog.tools.unit.assertj.containsInstanceOf
import com.datadog.tools.unit.getFieldValue
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DatadogGlideModuleTest {

    lateinit var testedModule: AppGlideModule

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockGlide: Glide

    @Mock
    lateinit var mockGlideBuilder: GlideBuilder

    @Mock
    lateinit var mockRegistry: Registry

    @Mock
    lateinit var mockSdkCore: SdkCore

    @StringForgery(regex = "[a-z]+\\.[a-z]{3}")
    lateinit var fakeHost: String

    @BeforeEach
    fun `set up`() {
        testedModule = DatadogGlideModule(firstPartyHosts = listOf(fakeHost))
    }

    @Test
    fun `registers a custom OkHttpLoader`() {
        // When
        testedModule.registerComponents(mockContext, mockGlide, mockRegistry)

        // Then
        argumentCaptor<ModelLoaderFactory<GlideUrl, InputStream>> {
            verify(mockRegistry).replace(
                eq(GlideUrl::class.java),
                eq(InputStream::class.java),
                capture()
            )

            assertThat(firstValue).isInstanceOf(OkHttpUrlLoader.Factory::class.java)
            val client: OkHttpClient = firstValue.getFieldValue("client")
            assertThat(client.interceptors).containsInstanceOf(DatadogInterceptor::class.java)
        }
    }

    @Test
    fun `applies custom diskCache Executor`() {
        testedModule.applyOptions(mockContext, mockGlideBuilder)

        argumentCaptor<GlideExecutor> {
            verify(mockGlideBuilder).setDiskCacheExecutor(capture())
            verify(mockGlideBuilder).setSourceExecutor(capture())

            assertUncaughtExceptionHandler(firstValue)
            assertUncaughtExceptionHandler(secondValue)
        }
    }

    private fun assertUncaughtExceptionHandler(executor: GlideExecutor) {
        val delegate: ExecutorService = executor.getFieldValue("delegate")
        check(delegate is ThreadPoolExecutor)

        val threadFactory: ThreadFactory = delegate.getFieldValue("threadFactory")
        val strategy: GlideExecutor.UncaughtThrowableStrategy =
            threadFactory.getFieldValue("uncaughtThrowableStrategy")

        assertThat(strategy).isInstanceOf(DatadogRUMUncaughtThrowableStrategy::class.java)
    }
}
