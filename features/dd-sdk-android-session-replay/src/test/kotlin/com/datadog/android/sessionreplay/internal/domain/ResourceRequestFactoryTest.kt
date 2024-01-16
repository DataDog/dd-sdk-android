/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.domain

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.exception.InvalidPayloadFormatException
import com.datadog.android.sessionreplay.internal.net.RawEventsToResourcesMapper
import com.datadog.android.sessionreplay.internal.recorder.SessionReplayResource
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.Buffer
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.ObjectInput

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ResourceRequestFactoryTest {
    private lateinit var testedRequestFactory: ResourceRequestFactory

    @Mock
    lateinit var mockRawEventsToResourcesMapper: RawEventsToResourcesMapper

    @Mock
    lateinit var mockResourceRequestBodyFactory: ResourceRequestBodyFactory

    private lateinit var rawBatchEvents: List<RawBatchEvent>

    @Forgery
    lateinit var fakeSessionReplayResource: SessionReplayResource

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Mock
    lateinit var mockRequestBody: RequestBody

    @Mock
    lateinit var mockInputStream: ObjectInput

    @Mock
    lateinit var mockLogger: InternalLogger

    private lateinit var fakeMediaType: MediaType

    private var fakeMetadata: ByteArray? = null

    @StringForgery
    lateinit var fakeData: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        rawBatchEvents = listOf(
        RawBatchEvent(
            fakeData.toByteArray()
        )
        )
        fakeMediaType = forge.anElementFrom(
            listOf(
                MultipartBody.FORM,
                MultipartBody.ALTERNATIVE,
                MultipartBody.MIXED,
                MultipartBody.PARALLEL
            )
        )
        whenever(mockRequestBody.contentType()).thenReturn(fakeMediaType)

        fakeMetadata = forge.aNullable { forge.aString().toByteArray() }

        whenever(mockResourceRequestBodyFactory.create(listOf(fakeSessionReplayResource), mockLogger))
            .thenReturn(mockRequestBody)
        whenever(mockRawEventsToResourcesMapper.map(mockInputStream))
            .thenReturn(fakeSessionReplayResource)

        testedRequestFactory = ResourceRequestFactory(
            customEndpointUrl = null,
            rawEventsToResourcesMapper = mockRawEventsToResourcesMapper,
            internalLogger = mockLogger,
            resourceRequestBodyFactory = mockResourceRequestBodyFactory
        )
    }

    // region Request

    @Test
    fun `M throw exception W create(){ payload is broken }`() {
        // Given
        whenever(mockRawEventsToResourcesMapper.map(any()))
            .thenReturn(null)

        // When
        Assertions.assertThatThrownBy {
            testedRequestFactory.create(fakeDatadogContext, rawBatchEvents, fakeMetadata)
        }
            .isInstanceOf(InvalidPayloadFormatException::class.java)
            .hasMessage(
                "The payload format was broken and " +
                        "an upload request could not be created"
            )
    }

    // endregion

    // region internal

    private fun RequestBody.toByteArray(): ByteArray {
        val buffer = Buffer()
        writeTo(buffer)
        return buffer.readByteArray()
    }

    // endregion
}
