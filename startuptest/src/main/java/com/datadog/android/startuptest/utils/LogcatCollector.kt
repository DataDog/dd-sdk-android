/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.startuptest.utils

import android.app.Instrumentation
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader

class LogcatCollector(private val inst: Instrumentation) {

    fun subscribe(command: String): Flow<String> {
        return channelFlow {
            val fd = inst.uiAutomation.executeShellCommand(command)
            val stdout = FileInputStream(fd.fileDescriptor)
            val bufferedReader = BufferedReader(InputStreamReader(stdout))

            launch {
                bufferedReader.useLines { lines ->
                    lines.forEach { line ->
                        send(line)
                    }
                }
            }
            awaitClose {
                Log.w("WAGAGA", "closing")
                bufferedReader.close()
                stdout.close()
                fd.close()
            }
        }
    }
}
