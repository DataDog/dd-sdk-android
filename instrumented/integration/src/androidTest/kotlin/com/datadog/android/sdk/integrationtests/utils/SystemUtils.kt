package com.datadog.android.sdk.integrationtests.utils

fun execShell(vararg command: String): String {
    val process = ProcessBuilder(*command).start()
    var toReturn = ""
    process.inputStream.use {
        toReturn =
            it.bufferedReader().readText()
    }
    process.waitFor()
    return toReturn
}
