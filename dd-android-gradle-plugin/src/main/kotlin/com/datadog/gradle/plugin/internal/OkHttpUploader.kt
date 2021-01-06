/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.internal

import com.datadog.gradle.plugin.DdAppIdentifier
import java.io.File
import java.net.HttpURLConnection
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request

class OkHttpUploader : Uploader {

    override fun upload(
        url: String,
        file: File,
        identifier: DdAppIdentifier
    ) {
        val fileBody = MultipartBody.create(MEDIA_TYPE_TXT, file)

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("version", identifier.version)
            .addFormDataPart("service", identifier.serviceName)
            .addFormDataPart("variant", identifier.variant)
            .addFormDataPart("type", TYPE_JVM_MAPPING_FILE)
            .addFormDataPart("jvm_mapping_file", file.name, fileBody)
            .build()

        val client = OkHttpClient.Builder().build()
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val call = client.newCall(request)
        val response = call.execute()

        val statusCode = response.code()
        when {
            statusCode in succesfulCodes -> println(
                "Mapping file upload successful for $identifier"
            )
            statusCode == HttpURLConnection.HTTP_FORBIDDEN -> throw RuntimeException(
                "Unable to upload mapping file for $identifier; " +
                    "verify that you're using a valid API Keyr"
            )
            statusCode >= HttpURLConnection.HTTP_BAD_REQUEST -> throw RuntimeException(
                "Unable to upload mapping file for $identifier; " +
                    "it can be because the mapping file already exist for this version"
            )
        }
    }

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
