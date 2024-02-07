/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.exception.InvalidPayloadFormatException
import com.datadog.android.sessionreplay.internal.net.ResourceRequestBodyFactory.Companion.APPLICATION_ID_KEY
import com.datadog.android.sessionreplay.internal.net.ResourceRequestBodyFactory.Companion.CONTENT_TYPE_IMAGE
import com.datadog.android.sessionreplay.internal.net.ResourceRequestBodyFactory.Companion.FILENAME_BLOB
import com.datadog.android.sessionreplay.internal.net.ResourceRequestBodyFactory.Companion.FILENAME_KEY
import com.datadog.android.sessionreplay.internal.net.ResourceRequestBodyFactory.Companion.MULTIPLE_APPLICATION_ID_ERROR
import com.datadog.android.sessionreplay.internal.net.ResourceRequestBodyFactory.Companion.NAME_IMAGE
import com.datadog.android.sessionreplay.internal.net.ResourceRequestBodyFactory.Companion.NAME_RESOURCE
import com.datadog.android.sessionreplay.internal.net.ResourceRequestBodyFactory.Companion.NO_RESOURCES_TO_SEND_ERROR
import com.datadog.android.sessionreplay.internal.net.ResourceRequestBodyFactory.Companion.TYPE_KEY
import com.datadog.android.sessionreplay.internal.net.ResourceRequestBodyFactory.Companion.TYPE_RESOURCE
import com.datadog.android.utils.verifyLog
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.MultipartBody
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ResourceRequestBodyFactoryTest {
    private lateinit var testedRequestBodyFactory: ResourceRequestBodyFactory

    @StringForgery
    private lateinit var fakeApplicationId: String

    @StringForgery
    private lateinit var fakeFilename: String

    @StringForgery
    private lateinit var fakeImageRepresentation: String

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var fakeMetaData: JsonObject

    @BeforeEach
    fun `set up`() {
        fakeMetaData = JsonObject()
        fakeMetaData.addProperty(APPLICATION_ID_KEY, fakeApplicationId)
        fakeMetaData.addProperty(FILENAME_KEY, fakeFilename)

        testedRequestBodyFactory = ResourceRequestBodyFactory(mockInternalLogger)
    }

    @Test
    fun `M return valid requestBody W create()`() {
        // Given
        val fakeRawBatchEvent = RawBatchEvent(
            data = fakeImageRepresentation.toByteArray(),
            metadata = fakeMetaData.toString().toByteArray(Charsets.UTF_8)
        )
        val fakeListResources = listOf(fakeRawBatchEvent)

        // When
        val requestBody = testedRequestBodyFactory.create(fakeListResources)

        // Then
        assertThat(requestBody).isInstanceOf(MultipartBody::class.java)
        assertThat(requestBody.contentType()?.type).isEqualTo(MultipartBody.FORM.type)
        assertThat(requestBody.contentType()?.subtype).isEqualTo(MultipartBody.FORM.subtype)

        val body = requestBody as MultipartBody
        val parts = body.parts

        val applicationIdJson = JsonObject()
        applicationIdJson.addProperty(APPLICATION_ID_KEY, fakeApplicationId)
        applicationIdJson.addProperty(TYPE_KEY, TYPE_RESOURCE)

        val applicationIdPart = MultipartBody.Part.createFormData(
            NAME_RESOURCE,
            FILENAME_BLOB,
            applicationIdJson.toString().toRequestBody(ResourceRequestBodyFactory.CONTENT_TYPE_APPLICATION)
        )

        val resourcesPart = MultipartBody.Part.createFormData(
            NAME_IMAGE,
            fakeFilename,
            fakeImageRepresentation.toByteArray().toRequestBody(CONTENT_TYPE_IMAGE)
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
                resourcesPart,
                applicationIdPart
            )
    }

    @Test
    fun `M throw exception W create() { empty resource list }`() {
        // Given
        val missingApplicationIdData = fakeMetaData.deepCopy()
        val missingFilenameData = fakeMetaData.deepCopy()

        missingApplicationIdData.remove(APPLICATION_ID_KEY)
        val missingApplicationIdBatchEvent = RawBatchEvent(
            data = fakeImageRepresentation.toByteArray(),
            metadata = missingApplicationIdData.toString().toByteArray(Charsets.UTF_8)
        )

        missingFilenameData.remove(FILENAME_KEY)
        val missingFilenameBatchEvent = RawBatchEvent(
            data = fakeImageRepresentation.toByteArray(),
            metadata = missingFilenameData.toString().toByteArray(Charsets.UTF_8)
        )

        val emptyMetaDataBatchEvent = RawBatchEvent(
            data = fakeImageRepresentation.toByteArray(),
            metadata = ByteArray(0)
        )

        val fakeListResources = listOf(
            missingApplicationIdBatchEvent,
            missingFilenameBatchEvent,
            emptyMetaDataBatchEvent
        )

        // When
        assertThatThrownBy { testedRequestBodyFactory.create(fakeListResources) }
            .isInstanceOf(InvalidPayloadFormatException::class.java)
            .hasMessage(NO_RESOURCES_TO_SEND_ERROR)
    }

    @Test
    fun `M throw exception and return largest group W create() { multiple applicationIds }`(forge: Forge) {
        // Given
        val fakeSecondApplicationId = forge.anAsciiString()
        val fakeSecondFilename = forge.anAsciiString()
        val fakeRawBatchEvent1 = RawBatchEvent(
            data = fakeImageRepresentation.toByteArray(),
            metadata = fakeMetaData.toString().toByteArray(Charsets.UTF_8)
        )

        fakeMetaData.remove(APPLICATION_ID_KEY)
        fakeMetaData.addProperty(APPLICATION_ID_KEY, fakeSecondApplicationId)
        fakeMetaData.remove(FILENAME_KEY)
        fakeMetaData.addProperty(FILENAME_KEY, fakeSecondFilename)

        val fakeRawBatchEvent2 = RawBatchEvent(
            data = fakeImageRepresentation.toByteArray(),
            metadata = fakeMetaData.toString().toByteArray(Charsets.UTF_8)
        )

        val fakeRawBatchEvent3 = RawBatchEvent(
            data = fakeImageRepresentation.toByteArray(),
            metadata = fakeMetaData.toString().toByteArray(Charsets.UTF_8)
        )

        val fakeListResources = mutableListOf<RawBatchEvent>()
        fakeListResources.add(fakeRawBatchEvent1)
        fakeListResources.add(fakeRawBatchEvent2)
        fakeListResources.add(fakeRawBatchEvent3)

        // When
        val requestBody = testedRequestBodyFactory.create(fakeListResources)
        val body = requestBody as MultipartBody
        val parts = body.parts

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            message = MULTIPLE_APPLICATION_ID_ERROR
        )

        val resourceData = JsonObject()
        resourceData.addProperty(APPLICATION_ID_KEY, fakeSecondApplicationId)
        resourceData.addProperty(TYPE_KEY, TYPE_RESOURCE)

        val applicationIdPart = MultipartBody.Part.createFormData(
            NAME_RESOURCE,
            FILENAME_BLOB,
            resourceData.toString().toRequestBody(ResourceRequestBodyFactory.CONTENT_TYPE_APPLICATION)
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
            .contains(
                applicationIdPart
            )
    }

    private fun RequestBody.toByteArray(): ByteArray {
        val buffer = Buffer()
        writeTo(buffer)
        return buffer.readByteArray()
    }
}
