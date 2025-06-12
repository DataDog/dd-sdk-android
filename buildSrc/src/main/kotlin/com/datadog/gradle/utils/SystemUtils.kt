/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.utils

import org.gradle.process.ExecOperations
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader

fun ExecOperations.execShell(vararg command: String): List<String> {
    val outputStream = ByteArrayOutputStream()
    exec {
        commandLine(*command)
        standardOutput = outputStream
    }

    val reader = InputStreamReader(ByteArrayInputStream(outputStream.toByteArray()))
    return reader.readLines()
}
