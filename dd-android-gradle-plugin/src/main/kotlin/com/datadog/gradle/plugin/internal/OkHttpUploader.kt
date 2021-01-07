/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.internal

import com.datadog.gradle.plugin.DdAndroidGradlePlugin.Companion.LOGGER
import java.io.File
import java.lang.IllegalStateException
import java.net.HttpURLConnection
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal class OkHttpUploader : Uploader {

    // region Uploader

    @Suppress("TooGenericExceptionCaught")
    override fun upload(
        url: String,
        file: File,
        identifier: DdAppIdentifier
    ) {

        val body = createBody(identifier, file)

        val client = OkHttpClient.Builder().build()
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val call = client.newCall(request)
        val response = try {
            call.execute()
        } catch (e: Throwable) {
            LOGGER.error("Error uploading the mapping file for $identifier", e)
            null
        }

        handleResponse(response, identifier)
    }

    // endregion

    // region Internal

    private fun createBody(
        identifier: DdAppIdentifier,
        file: File
    ): MultipartBody {
        val fileBody = MultipartBody.create(MEDIA_TYPE_TXT, file)
        return MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("version", identifier.version)
            .addFormDataPart("service", identifier.serviceName)
            .addFormDataPart("variant", identifier.variant)
            .addFormDataPart("type", TYPE_JVM_MAPPING_FILE)
            .addFormDataPart("jvm_mapping_file", file.name, fileBody)
            .build()
    }

    @Suppress("ThrowingInternalException", "TooGenericExceptionThrown")
    private fun handleResponse(
        response: Response?,
        identifier: DdAppIdentifier
    ) {
        val statusCode = response?.code()
        when {
            statusCode == null -> throw RuntimeException(
                "Unable to upload mapping file for $identifier; check your network connection"
            )
            statusCode in succesfulCodes -> LOGGER.info(
                "Mapping file upload successful for $identifier"
            )
            statusCode == HttpURLConnection.HTTP_FORBIDDEN -> throw IllegalStateException(
                "Unable to upload mapping file for $identifier; " +
                    "verify that you're using a valid API Key"
            )
            statusCode >= HttpURLConnection.HTTP_BAD_REQUEST -> throw IllegalStateException(
                "Unable to upload mapping file for $identifier; " +
                    "it can be because the mapping file already exist for this version"
            )
        }
    }

    // endregion

    companion object {

        internal val MEDIA_TYPE_TXT = MediaType.parse("text/plain")

        internal val succesfulCodes = arrayOf(
            HttpURLConnection.HTTP_OK,
            HttpURLConnection.HTTP_CREATED,
            HttpURLConnection.HTTP_ACCEPTED
        )

        internal const val TYPE_JVM_MAPPING_FILE = "jvm_mapping_file"
    }
}
