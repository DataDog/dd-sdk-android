package com.datadog.android.sdk.integrationtests

import android.content.Context
import android.os.Build
import com.datadog.android.log.Logger

internal object Runtime {
    fun logger(context: Context): Logger {
        // Initialise Logger
        val logger = Logger.Builder()
            .setNetworkInfoEnabled(true)
            .setServiceName(context.packageName)
            .build()

        // Attributes
        stringAttributes.forEach { logger.addAttribute(it.key, it.value) }
        intAttribute.forEach { logger.addAttribute(it.key, it.value) }

        // tags
        keyValuePairsTags.forEach { logger.addTag(it.key, it.value) }
        singleValueTags.forEach { logger.addTag(it) }

        return logger
    }

    val keyValuePairsTags = mapOf(
        "flavor" to BuildConfig.FLAVOR,
        "build_type" to BuildConfig.BUILD_TYPE,
        "blank" to ""
    )

    val singleValueTags = listOf(
        "datadog",
        "mobile"
    )

    val stringAttributes = mapOf(
        "version_name" to BuildConfig.VERSION_NAME,
        "device" to Build.DEVICE,
        "null_string" to null
    )

    val intAttribute = mapOf(
        "version_code" to BuildConfig.VERSION_CODE,
        "sdk_int" to Build.VERSION.SDK_INT
    )

    const val DD_TOKEN = "MYTESTAPPTOKEN"
    const val DD_CONTENT_TYPE = "application/json"
}
