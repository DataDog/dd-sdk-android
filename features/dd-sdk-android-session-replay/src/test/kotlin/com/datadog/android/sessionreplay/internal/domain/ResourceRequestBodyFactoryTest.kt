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
internal class ResourceRequestBodyFactoryTest {
    private lateinit var testedSegmentRequestBodyFactory: SegmentRequestBodyFactory

    @Mock
    lateinit var mockCompressor: BytesCompressor

    private lateinit var fakeCompressedData: ByteArray

    @Forgery
    lateinit var fakeSegment: MobileSegment

    @Forgery
    lateinit var fakeSegmentAsJson: JsonObject

    private lateinit var fakeSerializedSegmentWithNewLine: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeSerializedSegmentWithNewLine = fakeSegmentAsJson.toString() + "\n"
        fakeCompressedData = forge.aString().toByteArray()
        whenever(mockCompressor.compressBytes(fakeSerializedSegmentWithNewLine.toByteArray()))
            .thenReturn(fakeCompressedData)
        testedSegmentRequestBodyFactory = SegmentRequestBodyFactory(
            compressor = mockCompressor
        )
    }

    @Test
    fun `M return a multipart body W create { batches }`() {
        // When
        val body = testedSegmentRequestBodyFactory.create(fakeSegment, fakeSegmentAsJson)

        // Then
        assertThat(body).isInstanceOf(MultipartBody::class.java)
        val multipartBody = body as MultipartBody
        assertThat(multipartBody.type).isEqualTo(MultipartBody.FORM)
        val parts = multipartBody.parts
        val compressedSegmentPart = Part.createFormData(
            SegmentRequestBodyFactory.SEGMENT_FORM_KEY,
            fakeSegment.session.id,
            fakeCompressedData
                .toRequestBody(SegmentRequestBodyFactory.CONTENT_TYPE_BINARY.toMediaTypeOrNull())
        )
        val applicationIdPart = Part.createFormData(
            SegmentRequestBodyFactory.APPLICATION_ID_FORM_KEY,
            fakeSegment.application.id
        )
        val sessionIdPart = Part.createFormData(
            SegmentRequestBodyFactory.SESSION_ID_FORM_KEY,
            fakeSegment.session.id
        )
        val viewIdPart = Part.createFormData(
            SegmentRequestBodyFactory.VIEW_ID_FORM_KEY,
            fakeSegment.view.id
        )
        val hasFullSnapshotPart = Part.createFormData(
            SegmentRequestBodyFactory.HAS_FULL_SNAPSHOT_FORM_KEY,
            fakeSegment.hasFullSnapshot.toString()
        )
        val recordsCountPart = Part.createFormData(
            SegmentRequestBodyFactory.RECORDS_COUNT_FORM_KEY,
            fakeSegment.recordsCount.toString()
        )
        val rawSegmentSizePart = Part.createFormData(
            SegmentRequestBodyFactory.RAW_SEGMENT_SIZE_FORM_KEY,
            fakeCompressedData.size.toString()
        )
        val segmentsStartPart = Part.createFormData(
            SegmentRequestBodyFactory.START_TIMESTAMP_FORM_KEY,
            fakeSegment.start.toString()
        )
        val segmentsEndPart = Part.createFormData(
            SegmentRequestBodyFactory.END_TIMESTAMP_FORM_KEY,
            fakeSegment.end.toString()
        )
        val segmentSourcePart = Part.createFormData(
            SegmentRequestBodyFactory.SOURCE_FORM_KEY,
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
        assertThatThrownBy { testedSegmentRequestBodyFactory.create(fakeSegment, fakeSegmentAsJson) }
            .isEqualTo(fakeException)
    }

    private fun RequestBody.toByteArray(): ByteArray {
        val buffer = Buffer()
        writeTo(buffer)
        return buffer.readByteArray()
    }
}
