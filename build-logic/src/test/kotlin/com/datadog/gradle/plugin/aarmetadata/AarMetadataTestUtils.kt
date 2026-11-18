/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.aarmetadata

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal fun File.writeAarMetadata(metadata: String?) {
    ZipOutputStream(outputStream()).use { zip ->
        zip.putNextEntry(ZipEntry("classes.jar"))
        zip.closeEntry()
        if (metadata != null) {
            zip.putNextEntry(ZipEntry("META-INF/com/android/build/gradle/aar-metadata.properties"))
            zip.write(metadata.toByteArray())
            zip.closeEntry()
        }
    }
}
