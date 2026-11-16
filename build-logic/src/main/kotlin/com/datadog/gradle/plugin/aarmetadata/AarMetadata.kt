/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.aarmetadata

import java.io.File
import java.util.Properties
import java.util.zip.ZipFile

internal const val AAR_METADATA_ENTRY = "META-INF/com/android/build/gradle/aar-metadata.properties"

internal fun readAarMetadata(aar: File): Properties? {
    return ZipFile(aar).use { zip ->
        zip.getEntry(AAR_METADATA_ENTRY)?.let { entry ->
            zip.getInputStream(entry).use { input ->
                Properties().apply { load(input) }
            }
        }
    }
}
