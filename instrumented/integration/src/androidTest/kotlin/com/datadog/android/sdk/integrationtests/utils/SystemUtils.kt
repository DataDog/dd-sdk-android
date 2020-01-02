package com.datadog.android.sdk.integrationtests.utils

fun execShell(vararg command: String): String {
    val process = ProcessBuilder(*command).start()
    var toReturn = ""
    process.errorStream.use {
        val errorOutput = it.bufferedReader().readText()
        if (errorOutput.isNotEmpty()) {
            System.err.println(errorOutput)
        }
    }
    process.inputStream.use {
        toReturn =
            it.bufferedReader().readLine() ?: ""
    }
    return toReturn
}
