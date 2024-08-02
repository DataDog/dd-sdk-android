/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.net

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
internal class SegmentRequestBodyFactoryTest {

    private lateinit var testedRequestBodyFactory: SegmentRequestBodyFactory

    @Mock
    lateinit var mockCompressor: BytesCompressor

    lateinit var fakeGroupedSegments: List<Pair<MobileSegment, JsonObject>>

    lateinit var fakeCompresseData: List<ByteArray>

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeGroupedSegments = forge.aList(size = forge.anInt(min = 1, max = 10)) {
            val segment = forge.getForgery<MobileSegment>()
            val json = forge.getForgery<JsonObject>()
            Pair(segment, json)
        }
        fakeCompresseData = fakeGroupedSegments.map { forge.aString().toByteArray() }
        fakeGroupedSegments.forEachIndexed { index, pair ->
            val compressedData = fakeCompresseData[index]
            whenever(mockCompressor.compressBytes((pair.second.toString() + "\n").toByteArray()))
                .thenReturn(compressedData)
        }
        testedRequestBodyFactory = SegmentRequestBodyFactory(mockCompressor)
    }

    @Test
    fun `M return a multipart body W create`() {
        // Given
        val expectedFormMetadata = fakeGroupedSegments
            .mapIndexed { index, pair ->
                pair.first.toJson().asJsonObject.apply {
                    addProperty(
                        SegmentRequestBodyFactory.COMPRESSED_SEGMENT_SIZE_FORM_KEY,
                        fakeCompresseData[index].size
                    )
                    addProperty(
                        SegmentRequestBodyFactory.RAW_SEGMENT_SIZE_FORM_KEY,
                        (pair.second.toString() + "\n").toByteArray().size
                    )
                }
            }.fold(JsonArray()) { acc, element ->
                acc.add(element)
                acc
            }

        // When
        val body = testedRequestBodyFactory.create(fakeGroupedSegments)

        // Then
        assertThat(body).isInstanceOf(MultipartBody::class.java)
        val multipartBody = body as MultipartBody
        assertThat(multipartBody.type).isEqualTo(MultipartBody.FORM)
        val parts = multipartBody.parts
        val compressedSegmentParts = fakeCompresseData.mapIndexed { index, bytes ->
            Part.createFormData(
                SegmentRequestBodyFactory.SEGMENT_DATA_FORM_KEY,
                "${SegmentRequestBodyFactory.BINARY_FILENAME_PREFIX}$index",
                bytes.toRequestBody(SegmentRequestBodyFactory.CONTENT_TYPE_BINARY_TYPE)
            )
        }
        val metadataPart = Part.createFormData(
            SegmentRequestBodyFactory.EVENT_NAME_FORM_KEY,
            filename = SegmentRequestBodyFactory.BLOB_FILENAME,
            expectedFormMetadata.toString()
                .toRequestBody(SegmentRequestBodyFactory.CONTENT_TYPE_JSON_TYPE)
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
                *compressedSegmentParts.toTypedArray(),
                metadataPart
            )
    }

    @Test
    fun `M throw W create() { mockCompressor throws }`(@Forgery fakeException: Exception) {
        // Given
        whenever(mockCompressor.compressBytes(any())).thenThrow(fakeException)

        // Then
        assertThatThrownBy { testedRequestBodyFactory.create(fakeGroupedSegments) }
            .isEqualTo(fakeException)
    }

    private fun RequestBody.toByteArray(): ByteArray {
        val buffer = Buffer()
        writeTo(buffer)
        return buffer.readByteArray()
    }
}
