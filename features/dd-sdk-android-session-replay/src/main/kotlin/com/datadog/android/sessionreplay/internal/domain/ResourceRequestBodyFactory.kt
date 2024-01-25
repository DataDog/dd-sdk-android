/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.domain

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.sessionreplay.internal.exception.InvalidPayloadFormatException
import com.datadog.android.sessionreplay.internal.utils.MiscUtils
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.jvm.Throws

internal class ResourceRequestBodyFactory(
    private val internalLogger: InternalLogger
) {

    @Throws(InvalidPayloadFormatException::class)
    internal fun create(
        resources: List<RawBatchEvent>
    ): RequestBody {
        @Suppress("UnsafeThirdPartyFunctionCall") // Handled up in the caller chain
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        val applicationId = getApplicationId(resources)

        resources.filter {
            val metadata: JsonObject? = MiscUtils.safeDeserializeToJsonObject(it.metadata)
            val elementApplicationId: String = metadata?.get(APPLICATION_ID_KEY)?.asString ?: ""
            elementApplicationId == applicationId
        }

        addResourcesSection(builder, resources)
        addApplicationIdSection(builder, applicationId)
        @Suppress("UnsafeThirdPartyFunctionCall") // Handled up in the caller chain
        return builder.build()
    }

    private fun getApplicationId(resources: List<RawBatchEvent>): String {
        val resourcesMetaData: List<JsonObject> = resources.mapNotNull {
            MiscUtils.safeDeserializeToJsonObject(it.metadata)
        }

        val applicationIds = resourcesMetaData.mapNotNull {
            it.get(APPLICATION_ID_KEY)
        }

        if (applicationIds.isEmpty()) {
            @Suppress("ThrowingInternalException")
            throw InvalidPayloadFormatException(UNABLE_GET_APPLICATION_ID_ERROR)
        }

        var selectedApplicationId = applicationIds[0].asString

        if (applicationIds.size > 1) {
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.USER,
                { MULTIPLE_APPLICATION_ID_ERROR }
            )

            var maxSize = 0
            val groupedByApplicationId = resourcesMetaData.groupBy {
                it.get(APPLICATION_ID_KEY)
            }

            groupedByApplicationId.forEach {
                if (it.value.size > maxSize) {
                    maxSize = it.value.size
                    selectedApplicationId = it.key.asString
                }
            }
        }

        return selectedApplicationId
    }

    private fun addResourcesSection(builder: MultipartBody.Builder, resources: List<RawBatchEvent>) {
        resources.forEach {
            val filename = getFilename(it)

            val data = it.data

            @Suppress("UnsafeThirdPartyFunctionCall") // Handled up in the caller chain
            builder.addFormDataPart(
                name = NAME_IMAGE,
                filename,
                data.toRequestBody(CONTENT_TYPE_IMAGE)
            )
        }
    }

    private fun getFilename(rawBatchEvent: RawBatchEvent): String {
        val metadataObject = MiscUtils.safeDeserializeToJsonObject(rawBatchEvent.metadata)

        if (metadataObject == null) {
            @Suppress("ThrowingInternalException")
            throw InvalidPayloadFormatException(DESERIALIZE_METADATA_ERROR)
        }

        val filename = safeGetFilenameFromMetadata(metadataObject)

        if (filename == null) {
            @Suppress("ThrowingInternalException")
            throw InvalidPayloadFormatException(DESERIALIZE_METADATA_ERROR)
        }

        return filename
    }

    private fun addApplicationIdSection(builder: MultipartBody.Builder, applicationId: String) {
        val data = JsonObject()
        data.addProperty(APPLICATION_ID_KEY, applicationId)
        data.addProperty(TYPE_KEY, TYPE_RESOURCE)
        @Suppress("UnsafeThirdPartyFunctionCall") // Handled up in the caller chain
        val applicationIdSection = data.toString().toRequestBody(CONTENT_TYPE_APPLICATION)

        @Suppress("UnsafeThirdPartyFunctionCall") // Handled up in the caller chain
        builder.addFormDataPart(
            name = NAME_RESOURCE,
            filename = FILENAME_BLOB,
            applicationIdSection
        )
    }

    @Suppress("SwallowedException")
    private fun safeGetFilenameFromMetadata(metadataObject: JsonObject): String? {
        return try {
            metadataObject.get(FILENAME_KEY)?.asString
        } catch (e: ClassCastException) {
            null
        } catch (e: IllegalStateException) {
            null
        }
    }

    companion object {
        @Suppress("UnsafeThirdPartyFunctionCall") // if malformed returns null
        internal val CONTENT_TYPE_IMAGE = "image/png".toMediaTypeOrNull()

        @Suppress("UnsafeThirdPartyFunctionCall") // if malformed returns null
        internal val CONTENT_TYPE_APPLICATION = "application/json".toMediaTypeOrNull()

        internal const val APPLICATION_ID_KEY = "application_id"
        internal const val FILENAME_KEY = "filename"
        internal const val TYPE_KEY = "type"
        internal const val TYPE_RESOURCE = "resource"
        internal const val NAME_IMAGE = "image"
        internal const val NAME_RESOURCE = "resource"
        internal const val FILENAME_BLOB = "blob"

        internal const val MULTIPLE_APPLICATION_ID_ERROR =
            "There were multiple applicationIds associated with the resources"
        internal const val UNABLE_GET_APPLICATION_ID_ERROR =
            "Unable to get the applicationId for the resources"
        internal const val DESERIALIZE_METADATA_ERROR =
            "Error deserializing resource metadata"
    }
}
