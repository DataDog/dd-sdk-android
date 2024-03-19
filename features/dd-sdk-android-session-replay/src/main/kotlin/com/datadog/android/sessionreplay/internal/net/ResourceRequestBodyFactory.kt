/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.net

import androidx.annotation.VisibleForTesting
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.sessionreplay.internal.processor.EnrichedResource.Companion.APPLICATION_ID_KEY
import com.datadog.android.sessionreplay.internal.processor.EnrichedResource.Companion.APPLICATION_KEY
import com.datadog.android.sessionreplay.internal.processor.EnrichedResource.Companion.FILENAME_KEY
import com.datadog.android.sessionreplay.internal.processor.EnrichedResource.Companion.ID_KEY
import com.datadog.android.sessionreplay.internal.utils.MiscUtils
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

internal class ResourceRequestBodyFactory(
    private val internalLogger: InternalLogger
) {
    internal fun create(
        resources: List<RawBatchEvent>
    ): RequestBody? {
        val deserializedResources: List<ResourceEvent> = deserializeToResourceEvents(resources)

        val applicationId = getApplicationId(deserializedResources) ?: return null

        // type is valid, so cannot throw IllegalArgumentException
        @SuppressWarnings("UnsafeThirdPartyFunctionCall")
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)

        addResourcesSection(builder, deserializedResources)
        addApplicationIdSection(builder, applicationId)

        val result = try {
            builder.build()
        } catch (e: IllegalStateException) {
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { EMPTY_REQUEST_BODY_ERROR },
                throwable = e
            )
            null
        }

        return result
    }

    private fun getApplicationId(resources: List<ResourceEvent>): String? {
        if (resources.isEmpty()) {
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { NO_RESOURCES_TO_SEND_ERROR }
            )
            return null
        }

        val uniqueApplicationIds = resources.groupBy {
            it.applicationId
        }

        if (uniqueApplicationIds.size > 1) {
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.USER,
                { MULTIPLE_APPLICATION_ID_ERROR }
            )
        }

        // list is not empty, so cannot throw NoSuchElementException
        @SuppressWarnings("UnsafeThirdPartyFunctionCall")
        val applicationId = resources.last().applicationId

        return applicationId
    }

    @VisibleForTesting
    internal fun deserializeToResourceEvents(resources: List<RawBatchEvent>): List<ResourceEvent> {
        return resources.mapNotNull {
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
    }

    private fun addResourcesSection(builder: MultipartBody.Builder, resources: List<ResourceEvent>) {
        resources.forEach {
            val filename = it.identifier
            val resourceData = it.resourceData
            addResourceRequestBody(builder, filename, resourceData)
        }
    }

    private fun addApplicationIdSection(builder: MultipartBody.Builder, applicationId: String) {
        val applicationIdOuter = JsonObject()
        val applicationIdInner = JsonObject()
        applicationIdInner.addProperty(ID_KEY, applicationId)
        applicationIdOuter.add(APPLICATION_KEY, applicationIdInner)
        applicationIdOuter.addProperty(TYPE_KEY, TYPE_RESOURCE)

        @Suppress("TooGenericExceptionCaught")
        val body = try {
            applicationIdOuter.toString().toRequestBody(CONTENT_TYPE_APPLICATION)
        } catch (e: ArrayIndexOutOfBoundsException) {
            // we have data, so should not be able to throw this
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { ERROR_CREATING_REQUEST_BODY },
                throwable = e
            )
            null
        } catch (e: IOException) {
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { ERROR_CREATING_REQUEST_BODY },
                throwable = e
            )
            null
        }

        if (body != null) {
            builder.addFormDataPart(
                name = NAME_EVENT,
                filename = FILENAME_BLOB,
                body = body
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun addResourceRequestBody(builder: MultipartBody.Builder, filename: String, resourceData: ByteArray) {
        val body = try {
            resourceData.toRequestBody(CONTENT_TYPE_IMAGE)
        } catch (e: ArrayIndexOutOfBoundsException) {
            // this should never happen because we aren't specifying an offset
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { ERROR_CREATING_REQUEST_BODY },
                throwable = e
            )
            null
        } catch (e: IOException) {
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { ERROR_CREATING_REQUEST_BODY },
                throwable = e
            )
            null
        }

        if (body != null) {
            builder.addFormDataPart(
                name = NAME_IMAGE,
                filename = filename,
                body = body
            )
        }
    }

    companion object {
        internal val CONTENT_TYPE_IMAGE = "image/png".toMediaTypeOrNull()
        internal val CONTENT_TYPE_APPLICATION = "application/json".toMediaTypeOrNull()

        internal const val TYPE_KEY = "type"
        internal const val TYPE_RESOURCE = "resource"
        internal const val NAME_IMAGE = "image"
        internal const val NAME_EVENT = "event"
        internal const val FILENAME_BLOB = "blob"

        internal const val MULTIPLE_APPLICATION_ID_ERROR =
            "There were multiple applicationIds associated with the resources"
        internal const val NO_RESOURCES_TO_SEND_ERROR =
            "No resources to send"
        private const val ERROR_CREATING_REQUEST_BODY =
            "Error creating request body"
        private const val EMPTY_REQUEST_BODY_ERROR =
            "Unable to add any sections to request body"
    }
}
