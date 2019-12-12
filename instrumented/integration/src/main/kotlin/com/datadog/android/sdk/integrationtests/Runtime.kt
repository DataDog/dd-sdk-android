package com.datadog.android.sdk.integrationtests

import android.content.Context
import com.datadog.android.log.Logger

object Runtime {
    fun logger(context: Context): Logger {
        // Initialise Logger
        val logger = Logger.Builder()
            .setNetworkInfoEnabled(true)
            .setServiceName(context.packageName)
            .build()
        attributes.forEach {
            logger.addAttribute(it.first, it.second)
        }
        keyValuePairsTags.forEach {
            logger.addTag(it.first, it.second)
        }
        singleValueTags.forEach {
            logger.addTag(it)
        }
        return logger
    }

    val keyValuePairsTags = arrayOf(
        Pair<String, String>("flavor", BuildConfig.FLAVOR),
        Pair<String, String>(
            "build_type", BuildConfig
                .BUILD_TYPE
        )
    )
    val singleValueTags = arrayOf(
        "datadog",
        "mobile"
    )

    val attributes = arrayOf(
        Pair<String, String>(
            "version_code", BuildConfig
                .VERSION_CODE.toString()
        ),
        Pair<String, String>(
            "version_name", BuildConfig
                .VERSION_NAME
        )
    )

    const val DD_TOKEN = "MYTESTAPPTOKEN"
    const val DD_CONTENT_TYPE = "application/json"
}
