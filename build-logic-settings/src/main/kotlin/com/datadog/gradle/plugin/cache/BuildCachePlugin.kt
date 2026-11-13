/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.cache

import com.github.burrunan.s3cache.AwsS3BuildCache
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import java.util.Locale

internal class BuildCachePlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        if (!settings.providers.environmentVariable("GITLAB_CI").orNull.toBoolean()) return

        settings.pluginManager.apply("com.github.burrunan.s3-build-cache")

        // Here we override the location of the local cache because the default cache folder gets uploaded
        // to GitLab and we don't want to make it bigger for no reason. And use some leftover stuff from there too.
        settings.buildCache.local {
            directory = settings.rootDir.resolve("workspace/build-cache")
        }

        val os = settings.providers.systemProperty("os.name").get().lowercase(Locale.US).replace(' ', '-')
        val arch = settings.providers.systemProperty("os.arch").get()

        settings.buildCache.remote(AwsS3BuildCache::class.java) {
            bucket = "dd-sdk-android-gradle-build-cache"
            region = "us-east-1"
            prefix = "v1/main/$os/$arch/"
            isPush = true
            lookupDefaultAwsCredentials = true
            isReducedRedundancy = false
        }
    }
}
