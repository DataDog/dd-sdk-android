/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.profiling

import android.annotation.SuppressLint
import android.content.Context
import com.google.perftools.profiles.ProfileProto
import com.google.protobuf.CodedOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun processTraceFile(context: Context, traceFilePath: String) {
    splitTraceFile(context, traceFilePath)
    val keyFile = File(context.getExternalFilesDir(null), "tmp.key")
    val dataFile = File(context.getExternalFilesDir(null), "tmp.data")
    val outputFile = File("$traceFilePath.pprof")
    if (!outputFile.exists()) {
        outputFile.createNewFile()
    }
    convertToPprof(
        keyFile = keyFile.path,
        dataFile = dataFile.path,
        outputFilePath = outputFile.path
    )
}

fun splitTraceFile(context: Context, traceFile: String) {
    val traceFile = File("$traceFile.trace")
    val lines = traceFile.readLines()
    val endIndex = lines.indexOfFirst { it.trim() == "*end" }

    if (endIndex == -1) {
        error("The trace file does not contain a '*end' marker.")
    }

    val keyLines = lines.subList(0, endIndex + 1) // Inclusive of *end
    val dataLines = lines.subList(endIndex + 1, lines.size)
    val keyFile = File(context.getExternalFilesDir(null), "tmp.key")
    val dataFile = File(context.getExternalFilesDir(null), "tmp.data")
    keyFile.writeText(keyLines.joinToString("\n"))
    dataFile.writeText(dataLines.joinToString("\n"))
}


fun convertToPprof(keyFile: String, dataFile: String, outputFilePath: String) {
    val keyFileInput = File(keyFile).readLines()

    val methodMap = mutableMapOf<String, Long>()  // Signature -> ID
    val functionMap = mutableMapOf<Long, ProfileProto.Function.Builder>()
    val sampleList = mutableListOf<ProfileProto.Sample.Builder>()
    val locationMap = mutableMapOf<Long, ProfileProto.Location.Builder>()

    var nextFunctionId = 1L
    var nextLocationId = 1L

    for (line in keyFileInput) {
        if (line.startsWith("0x")) {
            val parts = line.split('\t')
            val idHex = parts[0]
            val className = parts[1]
            val methodName = parts[2]
            val signature = parts[3]
            val fileName = parts.getOrNull(4) ?: ""
            val fullSig = "$className#$methodName$signature"
            val methodId = idHex.removePrefix("0x").toLong(16)
            methodMap[fullSig] = methodId

            // Create function and location
            val function = ProfileProto.Function.newBuilder()
                .setId(nextFunctionId)
                .setName(allocateString(fullSig).toLong())
                .setSystemName(allocateString(fullSig).toLong())
                .setFilename(allocateString(fileName).toLong())
            functionMap[methodId] = function

            val location = ProfileProto.Location.newBuilder()
                .setId(nextLocationId)
                .addLine(
                    ProfileProto.Line.newBuilder()
                        .setFunctionId(nextFunctionId)
                        .setLine(1)
                )
            locationMap[methodId] = location

            nextFunctionId++
            nextLocationId++
        }

    }

    val dataFileInPaintText = File(dataFile + ".painttext")
    parseDataFile(dataFile, dataFileInPaintText.path)
    val dataFileInput = dataFileInPaintText.readLines()
    for (line in dataFileInput) {
        if (line.contains("Record")) {
            val recordRegex =
                Regex("""Record\s+\d+\s+\|\s+Thread ID: (\d+)\s+\|\s+Method ID: (\d+)\s+\|\s+Action: (\w+)\s+\|\s+Time \+(\d+)us""")
            val match = recordRegex.find(line) ?: continue
            val (_, _, _, methodIdStr) = match.destructured
            val methodId = methodIdStr.toLong()

            val sample = ProfileProto.Sample.newBuilder()
                .addLocationId(locationMap[methodId]?.id ?: 0)
                .addValue(1)  // You can add duration or count
            sampleList.add(sample)
        }
    }


    val profile = ProfileProto.Profile.newBuilder()
        .addAllSample(sampleList.map { it.build() })
        .addAllFunction(functionMap.values.map { it.build() })
        .addAllLocation(locationMap.values.map { it.build() })
        .addSampleType(
            ProfileProto.ValueType.newBuilder()
                .setType(allocateString("samples").toLong())
                .setUnit(allocateString("count").toLong())
        )
        .addSampleType(
            ProfileProto.ValueType.newBuilder()
                .setType(allocateString("cpu").toLong())
                .setUnit(allocateString("nanoseconds").toLong())
        )
        .build()

    val output = File(outputFilePath).outputStream()
    profile.writeTo(CodedOutputStream.newInstance(output))
    output.close()
}

fun allocateString(str: String): Int {
    // In real use, manage deduplication and string table
    return str.hashCode()
}


@SuppressLint("DefaultLocale")
fun parseDataFile(inputFilePath: String, outputFilePath: String) {
    val inputFile = RandomAccessFile(inputFilePath, "r")
    val outputFile = File(outputFilePath).printWriter()
    try {
        // Read header
        val header = ByteArray(18)
        inputFile.readFully(header)
        val headerBuf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        val magic = headerBuf.int
        val version = headerBuf.short.toInt() and 0xFFFF
        val offsetToData = headerBuf.short.toInt() and 0xFFFF
        val startTimestampUsec = headerBuf.long

        outputFile.println("Magic: 0x${magic.toUInt().toString(16)} ('SLOW')")
        outputFile.println("Version: $version")
        outputFile.println("Offset to data: $offsetToData bytes")
        outputFile.println("Start timestamp: ${formatTimestamp(startTimestampUsec)}")
        outputFile.println("=====================================")

        // Seek to data section
        inputFile.seek(offsetToData.toLong())

        val recordBuffer = ByteArray(9)
        var recordIndex = 0

        while (true) {
            val bytesRead = inputFile.read(recordBuffer)
            if (bytesRead < 9) break // EOF

            val buf = ByteBuffer.wrap(recordBuffer).order(ByteOrder.LITTLE_ENDIAN)
            val threadId = buf.get().toInt() and 0xFF
            val methodWord = buf.int
            val deltaTime = buf.int.toUInt().toLong()

            val action = methodWord and 0x3
            val methodId = methodWord ushr 2

            val record = TraceRecord(threadId, methodId, action, deltaTime)

            outputFile.println(
                String.format(
                    "Record %-4d | Thread ID: %-3d | Method ID: %-6d | Action: %-6s | Time +%6dus",
                    recordIndex++,
                    record.threadId,
                    record.methodId,
                    decodeAction(record.action),
                    record.timeDelta
                )
            )
        }
    } finally {
        outputFile.close()
        inputFile.close()
    }
}

fun decodeAction(action: Int): String = when (action) {
    0 -> "ENTRY"
    1 -> "EXIT"
    2 -> "EXC_EXIT"
    else -> "RESERVED"
}

@SuppressLint("NewApi")
fun formatTimestamp(microseconds: Long): String {
    val millis = microseconds / 1000
    val instant = Instant.ofEpochMilli(millis)
    val formatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}

data class TraceRecord(val threadId: Int, val methodId: Int, val action: Int, val timeDelta: Long)
