/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.domain

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.net.BytesCompressor
import com.datadog.android.sessionreplay.model.MobileSegment
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.MultipartBody.Part
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class RequestBodyFactoryTest {

    lateinit var testedRequestBodyFactory: RequestBodyFactory

    @Mock
    lateinit var mockCompressor: BytesCompressor

    lateinit var fakeCompressedData: ByteArray

    @Forgery
    lateinit var fakeSegment: MobileSegment

    @Forgery
    lateinit var fakeSegmentAsJson: JsonObject

    lateinit var fakeSerializedSegmentWithNewLine: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeSerializedSegmentWithNewLine = fakeSegmentAsJson.toString() + "\n"
        fakeCompressedData = forge.aString().toByteArray()
        whenever(mockCompressor.compressBytes(fakeSerializedSegmentWithNewLine.toByteArray()))
            .thenReturn(fakeCompressedData)
        testedRequestBodyFactory = RequestBodyFactory(mockCompressor)
    }

    @Test
    fun `M return a multipart body W create`() {
        // When
        val body = testedRequestBodyFactory.create(fakeSegment, fakeSegmentAsJson)

        // Then
        assertThat(body).isInstanceOf(MultipartBody::class.java)
        val multipartBody = body as MultipartBody
        assertThat(multipartBody.type).isEqualTo(MultipartBody.FORM)
        val parts = multipartBody.parts
        val compressedSegmentPart = Part.createFormData(
            RequestBodyFactory.SEGMENT_FORM_KEY,
            fakeSegment.session.id,
            fakeCompressedData
                .toRequestBody(RequestBodyFactory.CONTENT_TYPE_BINARY.toMediaTypeOrNull())
        )
        val applicationIdPart = Part.createFormData(
            RequestBodyFactory.APPLICATION_ID_FORM_KEY,
            fakeSegment.application.id
        )
        val sessionIdPart = Part.createFormData(
            RequestBodyFactory.SESSION_ID_FORM_KEY,
            fakeSegment.session.id
        )
        val viewIdPart = Part.createFormData(
            RequestBodyFactory.VIEW_ID_FORM_KEY,
            fakeSegment.view.id
        )
        val hasFullSnapshotPart = Part.createFormData(
            RequestBodyFactory.HAS_FULL_SNAPSHOT_FORM_KEY,
            fakeSegment.hasFullSnapshot.toString()
        )
        val recordsCountPart = Part.createFormData(
            RequestBodyFactory.RECORDS_COUNT_FORM_KEY,
            fakeSegment.recordsCount.toString()
        )
        val rawSegmentSizePart = Part.createFormData(
            RequestBodyFactory.RAW_SEGMENT_SIZE_FORM_KEY,
            fakeCompressedData.size.toString()
        )
        val segmentsStartPart = Part.createFormData(
            RequestBodyFactory.START_TIMESTAMP_FORM_KEY,
            fakeSegment.start.toString()
        )
        val segmentsEndPart = Part.createFormData(
            RequestBodyFactory.END_TIMESTAMP_FORM_KEY,
            fakeSegment.end.toString()
        )
        val segmentSourcePart = Part.createFormData(
            RequestBodyFactory.SOURCE_FORM_KEY,
            fakeSegment.source.toJson().asString
        )

        assertThat(parts)
            .usingElementComparator { first, second ->
                val headersEval = first.headers == second.headers
                val bodyEval = first.body.toByteArray().contentEquals(second.body.toByteArray())
                if (headersEval && bodyEval) {
                    0
                } else {
                    -1
                }
            }
            .containsExactlyInAnyOrder(
                compressedSegmentPart,
                applicationIdPart,
                sessionIdPart,
                viewIdPart,
                hasFullSnapshotPart,
                recordsCountPart,
                rawSegmentSizePart,
                segmentsStartPart,
                segmentsEndPart,
                segmentSourcePart
            )
    }

    @Test
    fun `M throw W create() { mockCompressor throws }`(@Forgery fakeException: Exception) {
        // Given
        whenever(mockCompressor.compressBytes(any())).thenThrow(fakeException)

        // Then
        assertThatThrownBy { testedRequestBodyFactory.create(fakeSegment, fakeSegmentAsJson) }
            .isEqualTo(fakeException)
    }

    private fun RequestBody.toByteArray(): ByteArray {
        val buffer = Buffer()
        writeTo(buffer)
        return buffer.readByteArray()
    }
}
