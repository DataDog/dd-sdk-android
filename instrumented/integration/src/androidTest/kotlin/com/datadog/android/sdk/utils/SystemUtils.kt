package com.datadog.android.sdk.utils

fun execShell(vararg command: String): List<String> {
    val lines = mutableListOf<String>()
    val process = ProcessBuilder(*command).start()
    process.errorStream.use {
        val errorOutput = it.bufferedReader().readText()
        if (errorOutput.isNotEmpty()) {
            System.err.println(errorOutput)
        }
    }
    process.inputStream.bufferedReader().use {
        var line: String ?
        do {
            line = it.readLine()
            if (line != null) lines.add(line)
        } while (line != null)
    }
    return lines
}
