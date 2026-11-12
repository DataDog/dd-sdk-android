/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.api.dsl.VariantDimension
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
    buildConfigString("BENCHMARK_RUM_APPLICATION_ID", config.rumApplicationId)
    buildConfigString("BENCHMARK_CLIENT_TOKEN", config.token)
    buildConfigString("BENCHMARK_API_KEY", config.apiKey)
    buildConfigString("BENCHMARK_APPLICATION_KEY", config.applicationKey)
}

fun configureFlavorForSampleApp(
    project: Project,
    flavor: ApplicationProductFlavor,
    rootDir: File
) {
    val config = sampleAppConfig("${rootDir.absolutePath}/config/${flavor.name}.json")
    project.logger.info("Configuring flavor: [${flavor.name}] with config: [$config]")
    flavor.buildConfigString("DD_OVERRIDE_LOGS_URL", config.logsEndpoint)
    flavor.buildConfigString("DD_OVERRIDE_TRACES_URL", config.tracesEndpoint)
    flavor.buildConfigString("DD_OVERRIDE_RUM_URL", config.rumEndpoint)
    flavor.buildConfigString("DD_OVERRIDE_SESSION_REPLAY_URL", config.sessionReplayEndpoint)
    flavor.configureRumBuildConfig(config)
    flavor.buildConfigString("DD_REMOTE_CONFIGURATION_ID", config.remoteConfigurationId)
    flavor.buildConfigString("DD_API_KEY", config.apiKey)
    flavor.buildConfigString("DD_APPLICATION_KEY", config.applicationKey)
    flavor.buildConfigString("DD_SITE_NAME", flavor.name.uppercase(Locale.US))
}

fun ApplicationDefaultConfig.configureFlavorForTvApp(
    rootDir: File
) {
    val config = sampleAppConfig("${rootDir.absolutePath}/config/tv.json")
    configureRumBuildConfig(config)
}

fun ApplicationDefaultConfig.configureFlavorForAutoApp(
    rootDir: File
) {
    val config = sampleAppConfig("${rootDir.absolutePath}/config/auto.json")
    configureRumBuildConfig(config)
}

private fun VariantDimension.buildConfigString(name: String, value: String) {
    buildConfigField("String", name, "\"$value\"")
}

private fun VariantDimension.configureRumBuildConfig(config: SampleAppConfig) {
    buildConfigString("DD_RUM_APPLICATION_ID", config.rumApplicationId)
    buildConfigString("DD_CLIENT_TOKEN", config.token)
}
