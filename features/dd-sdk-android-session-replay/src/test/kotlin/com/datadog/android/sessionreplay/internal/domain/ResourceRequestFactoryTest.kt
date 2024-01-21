/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.domain

import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ResourceRequestFactoryTest {
    private lateinit var testedRequestFactory: ResourceRequestFactory

    @Mock
    lateinit var mockResourceRequestBodyFactory: ResourceRequestBodyFactory

    private lateinit var fakeRawBatchEvents: List<RawBatchEvent>

    @Mock
    lateinit var mockRequestBody: RequestBody

    @Forgery
    lateinit var fakeRawBatchEvent: RawBatchEvent

    private lateinit var fakeMediaType: MediaType

    @StringForgery
    lateinit var fakeApplicationId: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeRawBatchEvents = listOf(fakeRawBatchEvent)
        fakeMediaType = forge.anElementFrom(
            listOf(
                MultipartBody.FORM,
                MultipartBody.ALTERNATIVE,
                MultipartBody.MIXED,
                MultipartBody.PARALLEL
            )
        )
        whenever(mockRequestBody.contentType()).thenReturn(fakeMediaType)

        whenever(mockResourceRequestBodyFactory.create(fakeApplicationId, listOf(fakeRawBatchEvent)))
            .thenReturn(mockRequestBody)

        testedRequestFactory = ResourceRequestFactory(
            customEndpointUrl = null,
            resourceRequestBodyFactory = mockResourceRequestBodyFactory
        )
    }
}
