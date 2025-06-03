/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.profiling

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureEventReceiver
import com.datadog.android.core.internal.data.upload.CurlInterceptor
import com.datadog.android.rum.internal.domain.RumContext
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.ConnectionSpec
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class PerfettoTracesUploader(
    private val siteName: String,
    private val tracesDirectoryPath: String,
    private val apiKey: String,
    private val internalLogger: InternalLogger,
    private val version: String,
    private val service: String,
    private val sdkVersion: String
) : FeatureEventReceiver {

    private val okHttpClient = OkHttpClient.Builder()
        .addNetworkInterceptor(CurlInterceptor(true))
        .build()
    private val scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    private var appLaunchContextHolder: AtomicReference<RumContext> = AtomicReference()

    fun startUploadingTraces(
        uploadPeriod: Long
    ) {
        scheduledExecutorService.scheduleWithFixedDelay(
            UploadTracesRunnable(
                okHttpClient = okHttpClient,
                tracesDirectoryPath = tracesDirectoryPath,
                apiKey = apiKey,
                version = version,
                siteName = siteName,
                service = service,
                sdkVersion = sdkVersion,
                internalLogger = internalLogger,
                appLaunchContext = appLaunchContextHolder
            ),
            10,
            uploadPeriod,
            TimeUnit.SECONDS
        )
    }

    override fun onReceive(event: Any) {
        if (event is Map<*, *>) {
            if (event.get("type") == "rum_app_launch") {
                appLaunchContextHolder.compareAndSet(null, event["context"] as RumContext)
            }
        }
    }

    private class UploadTracesRunnable(
        val okHttpClient: OkHttpClient,
        val tracesDirectoryPath: String,
        val apiKey: String,
        val version: String,
        val siteName: String,
        val service: String,
        val sdkVersion: String,
        val internalLogger: InternalLogger,
        val appLaunchContext: AtomicReference<RumContext>
    ) :
        Runnable {

        private val origin = "dd-sdk-android"
        override fun run() {
            if (appLaunchContext.get() == null) {
                internalLogger.log(
                    InternalLogger.Level.INFO,
                    InternalLogger.Target.MAINTAINER,
                    { "TracesUploader: App launch context is null, skipping upload." }
                )
                return
            }
            // read the all the .trace files not more recent than 1 minute
            val tracesDirectory = File(tracesDirectoryPath)
            tracesDirectory.listFiles()?.take(10)?.forEach {
                uploadFiles(it)
            }
        }

        private fun uploadFiles(file: File) {
            val intermediaryFilePath = file.absolutePath.substringBeforeLast(".").split("_")
            val endTimestampInMillis  = intermediaryFilePath.lastOrNull()?.toLongOrNull() ?: return
            val startTimestampInMillis = intermediaryFilePath.getOrNull(intermediaryFilePath.size - 2)?.toLongOrNull()
                ?: return
            val startTimestampUtcFormat = formatIsoUtc(startTimestampInMillis)
            val endTimestampUtcFormat = formatIsoUtc(endTimestampInMillis)
            val context = appLaunchContext.get()
            val applicationId = JsonObject().apply {
                addProperty("id", context?.applicationId as String)
            }
            val sessionId = JsonObject().apply {
                addProperty("id", context?.sessionId as String)
            }
            val viewId = JsonObject().apply {
                val ids = JsonArray().apply {
                    add(context?.viewId as String)
                }
                add("id", ids)
            }
            // let's create a json object here instead of a string
            val payload = JsonObject().apply {
                val attachments = JsonArray().apply {
                    add("perfetto.proto")
                }
                add("attachments", attachments)
                add("application", applicationId)
                add("session", sessionId)
                add("view", viewId)
                addProperty("start", startTimestampUtcFormat)
                addProperty("end", endTimestampUtcFormat)
                addProperty("tags_profiler", "service:$service,version:$version")
                addProperty("family", "android")
                addProperty("version", "4")
            }

            // Create multipart request
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "perfetto.proto",
                    file.name,
                    file.asRequestBody(CONTENT_TYPE_BINARY_TYPE)
                )
                .addFormDataPart(
                    "event",
                    "event.json",
                    payload.toString().toRequestBody(CONTENT_TYPE_JSON_TYPE)
                )
                .build()

            val request = Request.Builder()
                .url("$siteName/api/v2/profile")
                .addHeader("DD-API-KEY", apiKey)
                .addHeader("DD-EVP-ORIGIN", origin)
                .addHeader("DD-EVP-ORIGIN-VERSION", sdkVersion)
                .post(requestBody)
                .build()

            try {
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    val errorMessage = response.body?.string()
                    internalLogger.log(
                        InternalLogger.Level.ERROR,
                        InternalLogger.Target.MAINTAINER,
                        { "TracesUploader: Failed to upload traces: $errorMessage" }
                    )
                } else {
                    internalLogger.log(
                        InternalLogger.Level.INFO,
                        InternalLogger.Target.MAINTAINER,
                        { "TracesUploader: Successfully uploaded traces ${file.name}" }
                    )
                }
            } catch (e: Exception) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.MAINTAINER,
                    { "TracesUploader: Exception while uploading traces: ${e.message}" }
                )
            } finally {
                // Clean up files after successful upload
                file.delete()
            }
        }

        fun formatIsoUtc(epochMillis: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date(epochMillis))
        }

        companion object{
            internal val CONTENT_TYPE_BINARY_TYPE = "application/octet-stream".toMediaTypeOrNull()
            internal val CONTENT_TYPE_JSON_TYPE = "application/json".toMediaTypeOrNull()
        }
    }

    companion object {
        internal const val LOCAL_HOST = "http://10.0.2.2:8080/api/v2/profile"
    }
}
