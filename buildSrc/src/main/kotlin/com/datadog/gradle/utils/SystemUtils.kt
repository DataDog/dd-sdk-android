package com.datadog.gradle.utils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import org.gradle.api.Project

fun Project.execShell(vararg command: String): List<String> {
    val outputStream = ByteArrayOutputStream()
    this.exec {
        commandLine(*command)
        standardOutput = outputStream
    }

    val reader = InputStreamReader(ByteArrayInputStream(outputStream.toByteArray()))
    return reader.readLines()
}
