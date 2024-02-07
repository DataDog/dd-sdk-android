/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.net

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
        val deserializedResources: List<ResourceEvent> = resources.mapNotNull {
            val resourceMetadata = MiscUtils.safeDeserializeToJsonObject(internalLogger, it.metadata)

            if (resourceMetadata != null) {
                val applicationId = MiscUtils.safeGetStringFromJsonObject(
                    internalLogger,
                    resourceMetadata,
                    APPLICATION_ID_KEY
                )
                val filename = MiscUtils.safeGetStringFromJsonObject(
                    internalLogger,
                    resourceMetadata,
                    FILENAME_KEY
                )

                if (applicationId != null && filename != null) {
                    ResourceEvent(
                        applicationId = applicationId,
                        identifier = filename,
                        it.data
                    )
                } else {
                    null
                }
            } else {
                null
            }
        }

        if (deserializedResources.isEmpty()) {
            @Suppress("ThrowingInternalException")
            throw InvalidPayloadFormatException(NO_RESOURCES_TO_SEND_ERROR)
        }

        val applicationId = getApplicationId(deserializedResources)

        @Suppress("UnsafeThirdPartyFunctionCall") // Handled up in the caller chain
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)

        addResourcesSection(builder, deserializedResources)
        addApplicationIdSection(builder, applicationId)
        @Suppress("UnsafeThirdPartyFunctionCall") // Handled up in the caller chain
        return builder.build()
    }

    private fun getApplicationId(resources: List<ResourceEvent>): String {
        val applicationIds = resources.map {
            it.applicationId
        }

        var selectedApplicationId = applicationIds[0]

        if (applicationIds.size > 1) {
            // more than one applicationId, so take the one from the largest group
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.USER,
                { MULTIPLE_APPLICATION_ID_ERROR }
            )

            @Suppress("UnsafeThirdPartyFunctionCall") // list is not empty
            selectedApplicationId = resources
                .groupBy { it.applicationId }
                .maxBy { it.value.size }
                .key
        }

        return selectedApplicationId
    }

    private fun addResourcesSection(builder: MultipartBody.Builder, resources: List<ResourceEvent>) {
        resources.forEach {
            val filename = it.identifier
            val resourceData = it.resourceData

            @Suppress("UnsafeThirdPartyFunctionCall") // Handled up in the caller chain
            builder.addFormDataPart(
                name = NAME_IMAGE,
                filename,
                resourceData.toRequestBody(CONTENT_TYPE_IMAGE)
            )
        }
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
        internal const val NO_RESOURCES_TO_SEND_ERROR =
                "No resources to send"
    }
}
