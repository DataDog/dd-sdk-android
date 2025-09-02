/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.startuptest.utils

import android.app.Instrumentation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader

class LogcatCollector(private val inst: Instrumentation) {

    fun subscribe(command: String): Flow<String> {
        return flow {
            inst.uiAutomation.executeShellCommand(command).use { fd ->
                val stdout = FileInputStream(fd.fileDescriptor)
                BufferedReader(InputStreamReader(stdout)).useLines { lines ->
                    lines.forEach { line ->
                        if (!line.contains("WAHAHA_COPY")) {
                            emit(line)
                        }
                    }
                }
            }
        }
    }
}
