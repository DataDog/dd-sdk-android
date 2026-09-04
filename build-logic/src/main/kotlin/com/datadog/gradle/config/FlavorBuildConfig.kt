/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.ApplicationProductFlavor
import com.google.gson.Gson
import org.gradle.api.Project
import java.io.File
import java.util.Locale

val SAMPLE_APP_REGIONS = arrayOf(
    "us1",
    "us3",
    "us5",
    "us1_fed",
    "us2_fed",
    "eu1",
    "uk1",
    "ap1",
    "ap2",
    "staging"
)

fun sampleAppConfig(filePath: String): SampleAppConfig {
    val file = File(filePath)
    if (!file.exists()) {
        return SampleAppConfig()
    }
    file.inputStream().reader().use {
        val jsonString = it.readText()
        return Gson().fromJson(jsonString, SampleAppConfig::class.java)
    }
}

fun ApplicationDefaultConfig.configureFlavorForBenchmark(
    rootDir: File
) {
    val config = sampleAppConfig("${rootDir.absolutePath}/config/benchmark.json")
    buildConfigField(
        "String",
        "BENCHMARK_RUM_APPLICATION_ID",
        "\"${config.rumApplicationId}\""
    )
    buildConfigField(
        "String",
        "BENCHMARK_CLIENT_TOKEN",
        "\"${config.token}\""
    )
    buildConfigField(
        "String",
        "BENCHMARK_API_KEY",
        "\"${config.apiKey}\""
    )
    buildConfigField(
        "String",
        "BENCHMARK_APPLICATION_KEY",
        "\"${config.applicationKey}\""
    )
}

fun configureFlavorForSampleApp(
    project: Project,
    flavor: ApplicationProductFlavor,
    rootDir: File
) {
    val flagsConfigPath = "${rootDir.absolutePath}/config/dd_flags.json"
    val flavorConfigPath = "${rootDir.absolutePath}/config/${flavor.name}.json"
    val flagsConfig = sampleAppConfig(flagsConfigPath)
    val flavorConfig = sampleAppConfig(flavorConfigPath)
    val config = if (File(flagsConfigPath).exists()) {
        project.logger.info("Using dd_flags.json config (overrides ${flavor.name}.json)")
        flagsConfig
    } else {
        flavorConfig
    }
    project.logger.info("Configuring flavor: [${flavor.name}] with config: [$config]")
    flavor.buildConfigField(
        "String",
        "DD_OVERRIDE_LOGS_URL",
        "\"${config.logsEndpoint}\""
    )
    flavor.buildConfigField(
        "String",
        "DD_OVERRIDE_TRACES_URL",
        "\"${config.tracesEndpoint}\""
    )
    flavor.buildConfigField(
        "String",
        "DD_OVERRIDE_RUM_URL",
        "\"${config.rumEndpoint}\""
    )
    flavor.buildConfigField(
        "String",
        "DD_OVERRIDE_SESSION_REPLAY_URL",
        "\"${config.sessionReplayEndpoint}\""
    )
    flavor.buildConfigField(
        "String",
        "DD_RUM_APPLICATION_ID",
        "\"${config.rumApplicationId}\""
    )
    flavor.buildConfigField(
        "String",
        "DD_REMOTE_CONFIGURATION_ID",
        "\"${config.remoteConfigurationId}\""
    )
    flavor.buildConfigField(
        "String",
        "DD_CLIENT_TOKEN",
        "\"${config.token}\""
    )
    flavor.buildConfigField(
        "String",
        "DD_API_KEY",
        "\"${config.apiKey}\""
    )
    flavor.buildConfigField(
        "String",
        "DD_APPLICATION_KEY",
        "\"${config.applicationKey}\""
    )
    val siteName = config.site.ifBlank { flavor.name }.uppercase(Locale.US)
    flavor.buildConfigField(
        "String",
        "DD_SITE_NAME",
        "\"$siteName\""
    )
}

fun ApplicationDefaultConfig.configureFlavorForTvApp(
    rootDir: File
) {
    val config = sampleAppConfig("${rootDir.absolutePath}/config/tv.json")
    buildConfigField(
        "String",
        "DD_RUM_APPLICATION_ID",
        "\"${config.rumApplicationId}\""
    )
    buildConfigField(
        "String",
        "DD_CLIENT_TOKEN",
        "\"${config.token}\""
    )
}

fun ApplicationDefaultConfig.configureFlavorForAutoApp(
    rootDir: File
) {
    val config = sampleAppConfig("${rootDir.absolutePath}/config/auto.json")
    buildConfigField(
        "String",
        "DD_RUM_APPLICATION_ID",
        "\"${config.rumApplicationId}\""
    )
    buildConfigField(
        "String",
        "DD_CLIENT_TOKEN",
        "\"${config.token}\""
    )
}
