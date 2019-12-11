package com.datadog.android.sdk.integrationtests

import com.datadog.android.log.Logger

object Runtime{
    val appLogger: Logger by lazy {
        // Initialise Logger
        val logger = Logger.Builder()
            .setNetworkInfoEnabled(true)
            .setServiceName("android-sample-java")
            .build()
        attributes.forEach {
            logger.addAttribute(it.first,it.second)
        }
        tags.forEach {
            logger.addTag(it.first,it.second)
        }
        logger
    }

    val tags = arrayOf(
        Pair<String,String>("flavor", com.datadog.android.sdk.integrationtests.BuildConfig.FLAVOR),
        Pair<String,String>("build_type", com.datadog.android.sdk.integrationtests.BuildConfig
            .BUILD_TYPE)
    )

    val attributes = arrayOf(
        Pair<String,String>("version_code", com.datadog.android.sdk.integrationtests.BuildConfig
            .VERSION_CODE.toString()),
        Pair<String,String>("version_name", com.datadog.android.sdk.integrationtests.BuildConfig
            .VERSION_NAME)
    )

}

