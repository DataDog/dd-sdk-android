package com.datadog.android.sdk.integrationtests

import com.datadog.android.log.Logger

object Runtime {
    val appLogger: Logger by lazy {
        // Initialise Logger
        val logger = Logger.Builder()
            .setNetworkInfoEnabled(true)
            .setServiceName(IntegrationTestsApplication.application.packageName)
            .build()
        attributes.forEach {
            logger.addAttribute(it.first, it.second)
        }
        tags.forEach {
            logger.addTag(it.first, it.second)
        }
        logger
    }

    val tags = arrayOf(
        Pair<String, String>("flavor", BuildConfig.FLAVOR),
        Pair<String, String>("build_type", BuildConfig
            .BUILD_TYPE)
    )

    val attributes = arrayOf(
        Pair<String, String>("version_code", BuildConfig
            .VERSION_CODE.toString()),
        Pair<String, String>("version_name", BuildConfig
            .VERSION_NAME)
    )

    const val DD_TOKEN = "MYTESTAPPTOKEN"
    const val DD_CONTENT_TYPE = "application/json"
}
