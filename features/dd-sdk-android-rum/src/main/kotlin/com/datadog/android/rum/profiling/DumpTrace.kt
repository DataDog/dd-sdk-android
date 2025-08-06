/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.profiling

import android.content.Context
import android.util.Log
import com.google.perftools.profiles.ProfileProto
import com.google.perftools.profiles.ProfileProto.Profile
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit


const val MAX_STACK_DEPTH: Int = 10000
const val MAX_THREADS: Int = 32768
const val HTML_BUFSIZE: Int = 10240

const val GRAPH_LABEL_VISITED: Int = 0x0001
const val GRAPH_NODE_VISITED: Int = 0x0002

var versionNumber: Int = 0

// Data Structures
data class DataHeader(
    var magic: UInt = 0u,
    var version: Short = 0,
    var offsetToData: Short = 0,
    var startWhen: Long = 0,
    var recordSize: Short = 0
)

data class ThreadEntry(
    val threadId: Int,
    val threadName: String
)

class TimedMethod(
    var next: TimedMethod? = null,
    var elapsedInclusive: ULong = 0u,
    var numCalls: Int = 0,
    var method: MethodEntry? = null
)

data class ClassEntry(
    val className: String,
    var elapsedExclusive: ULong = 0u,
    var numMethods: Int = 0,
    var methods: MutableList<MethodEntry> = mutableListOf(),
    var numCalls: IntArray = IntArray(2)
)

data class UniqueMethodEntry(
    var elapsedExclusive: ULong = 0u,
    var numMethods: Int = 0,
    var methods: MutableList<MethodEntry> = mutableListOf(),
    var numCalls: IntArray = IntArray(2)
)

data class MethodEntry(
    var methodId: Long,
    var className: String,
    var methodName: String?,
    var signature: String?,
    var fileName: String?,
    var lineNum: Int,
    var elapsedExclusive: ULong = 0u,
    var elapsedInclusive: ULong = 0u,
    var topExclusive: ULong = 0u,
    var recursiveInclusive: ULong = 0u,
    var parents: Array<TimedMethod?> = arrayOfNulls(2),
    var children: Array<TimedMethod?> = arrayOfNulls(2),
    var numCalls: IntArray = IntArray(2),
    var index: Int = 0,
    var recursiveEntries: Int = 0,
    var graphState: Int = 0
)

data class DataKeys(
    var fileData: ByteArray,
    var fileLen: Long,
    var numThreads: Int = 0,
    var threads: MutableList<ThreadEntry> = mutableListOf(),
    var numMethods: Int = 0,
    var methods: MutableList<MethodEntry> = mutableListOf()
)

data class StackEntry(
    var method: MethodEntry?,
    var entryTime: ULong
)

data class CallStack(
    var top: Int = 0,
    var calls: Array<StackEntry?> = Array(MAX_STACK_DEPTH) { null },
    var lastEventTime: ULong = 0u,
    var threadStartTime: ULong = 0u
)

data class DiffEntry(
    var method1: MethodEntry?,
    var method2: MethodEntry?,
    var differenceExclusive: Long = 0,
    var differenceInclusive: Long = 0,
    var differenceExclusivePercentage: Double = 0.0,
    var differenceInclusivePercentage: Double = 0.0
)

data class Options(
    var traceFileName: String? = null,
    var diffFileName: String? = null,
    var graphFileName: String? = null,
    var keepDotFile: Boolean = false,
    var dump: Boolean = false,
    var outputHtml: Boolean = false,
    var sortableUrl: String? = null,
    var threshold: Int = 20
)

data class TraceData(
    var numClasses: Int = 0,
    var classes: MutableList<ClassEntry> = mutableListOf(),
    var stacks: Array<CallStack?> = Array(MAX_THREADS) { null },
    var depth: IntArray = IntArray(MAX_THREADS),
    var numUniqueMethods: Int = 0,
    var uniqueMethods: MutableList<UniqueMethodEntry> = mutableListOf()
)

fun parseDataHeader(dis: DataInputStream): DataHeader? {
    val magic = dis.read4LE().toUInt()
    val version = dis.read2LE().toShort()
    val offsetToData = dis.read2LE().toShort()
    val startWhen = dis.read8LE()
    var bytesToRead = offsetToData.toInt() - 16

    val recordSize: Short = when (version.toInt()) {
        1 -> 9
        2 -> 10
        3 -> {
            val size = dis.read2LE().toShort()
            bytesToRead -= 2
            size
        }

        else -> {
            System.err.println("Unsupported trace file version: $version")
            return null
        }
    }

    if (bytesToRead > 0) dis.skipBytes(bytesToRead)

    return DataHeader(magic, version, offsetToData, startWhen, recordSize)
}


// Additional utilities like htmlEscape, sort methods, parsing logic, etc. can be added below.
fun parseDataKeys(traceData: TraceData, file: File): DataKeys? {
    val dis = DataInputStream(BufferedInputStream(FileInputStream(file)))
    val keysResult = parseKeysOffset(file) ?: return null
    val keys = keysResult.first
    val offset = keysResult.second
    dis.skipBytes(offset.toInt())
    val header = parseDataHeader(dis) ?: return null
    val stacks = Array<CallStack?>(MAX_THREADS) { null }
    var caller: MethodEntry? = null

    while (true) {
        var bytesToRead = header.recordSize.toInt()
        val threadId = try {
            if (header.version.toInt() == 1) {
                bytesToRead -= 1
                dis.readUnsignedByte()
            } else {
                bytesToRead -= 2
                dis.read2LE()
            }
        } catch (e: EOFException) {
            break
        }
        val methodVal = dis.read4LE()
        val currentTime = dis.read4LE().toLong()
        bytesToRead -= 8
        while (bytesToRead-- > 0) {
            dis.read()
        }
        val action = methodVal and 0x03
        val methodId = methodVal.toLong() and 0xFFFFFFFC
        Log.i(
            "TraceParser",
            "Thread: $threadId, Action: $action, Method: ${methodId}, Time: $currentTime"
        )
        val stack = stacks[threadId] ?: CallStack().also { stacks[threadId] = it }
        val method = keys.methods.find { it.methodId == methodId } ?: keys.methods[1] // unknown

        if (action == 0) { // ENTER
            if (stack.top >= MAX_STACK_DEPTH) error("Stack overflow")
            caller = if (stack.top >= 1) stack.calls[stack.top - 1]?.method else keys.methods[0]
            caller?.elapsedExclusive =
                caller?.elapsedExclusive?.plus(currentTime.toULong() - stack.lastEventTime) ?: 0u
            stack.calls[stack.top++] = StackEntry(method, currentTime.toULong())
        } else { // EXIT/UNROLL
            var entryTime = 0L
            if (stack.top > 0) {
                entryTime = stack.calls[--stack.top]?.entryTime?.toLong() ?: 0L
            }
            val elapsed = currentTime - entryTime
            caller = if (stack.top >= 1) stack.calls[stack.top - 1]?.method else keys.methods[0]
            caller?.let {
                addInclusiveTime(it, method, elapsed.toULong())
            }
            method.elapsedExclusive += currentTime.toULong() - stack.lastEventTime

        }
        stack.lastEventTime = currentTime.toULong()
    }

    traceData.stacks.indices.forEach { idx ->
        traceData.stacks[idx] = stacks[idx]
        val stack = stacks[idx] ?: return@forEach
        traceData.depth[idx] = stack.top
    }
    return keys
}


fun parseKeys(file: File): DataKeys? {
    val fileData = file.readBytes()
    val keys = DataKeys(
        fileData = fileData,
        fileLen = fileData.size.toLong(),
        threads = mutableListOf(),
        methods = mutableListOf()
    )
    val content = fileData.toString(StandardCharsets.UTF_8)
    val lines = content.lines()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        if (line.startsWith("*version")) {
            versionNumber = lines[index + 1].toInt()
            index += 2
        } else if (line.startsWith("*threads")) {
            index++
            while (index < lines.size && !lines[index].startsWith("*")) {
                val parts = lines[index].split("\t")
                if (parts.size == 2) {
                    keys.threads.add(ThreadEntry(parts[0].toInt(), parts[1]))
                }
                index++
            }
        } else if (line.startsWith("*methods")) {
            index++
            keys.methods.add(MethodEntry(-2, "(toplevel)", null, null, null, -1))
            keys.methods.add(MethodEntry(-1, "(unknown)", null, null, null, -1))
            while (index < lines.size && !lines[index].startsWith("*")) {
                val parts = lines[index].split("\t")
                val id = parts[0].removePrefix("0x").toLong(16)
                val className = if (parts.size > 1) parts[1] else ""
                val methodName = if (parts.size > 2) parts[2] else null
                val signature = if (parts.size > 3) parts[3] else null
                val fileName = if (parts.size > 4) parts[4] else null
                val lineNum = if (parts.size > 5) parts[5].toInt() else -1
                keys.methods.add(
                    MethodEntry(
                        id,
                        className,
                        methodName,
                        signature,
                        fileName,
                        lineNum
                    )
                )
                index++
            }
        } else if (line.startsWith("*end")) {
            break
        } else {
            index++
        }
    }
    keys.numThreads = keys.threads.size
    keys.numMethods = keys.methods.size
    return keys
}

fun parseKeysOffset(file: File): Pair<DataKeys, Long>? {
    val headerLines = mutableListOf<String>()
    var bytesReadForHeader: Long = 0
    var foundEndMarker = false

    try {
        FileInputStream(file).use { fis ->
            BufferedInputStream(fis).use { bis ->
                val currentLineBytes = mutableListOf<Byte>()
                while (true) {
                    val byteVal = bis.read()
                    if (byteVal == -1) {
                        System.err.println("EOF reached before *end marker in trace key section.")
                        return null
                    }
                    bytesReadForHeader++
                    currentLineBytes.add(byteVal.toByte())

                    if (byteVal.toByte() == '\n'.code.toByte()) { // Line ended
                        // Convert line bytes to string, removing potential \r from \r\n
                        val lineStr =
                            String(currentLineBytes.toByteArray(), StandardCharsets.UTF_8).trimEnd(
                                '\r',
                                '\n'
                            )
                        headerLines.add(lineStr)
                        currentLineBytes.clear()
                        if (lineStr.startsWith("*end")) {
                            foundEndMarker = true
                            break // Found *end, bytesReadForHeader is now the offset after this line
                        }
                    }
                }
            }
        }
    } catch (e: IOException) {
        System.err.println("Error reading trace file for keys: ${e.message}")
        return null
    }

    if (!foundEndMarker) {
        System.err.println("Could not find *end marker in trace key section.")
        return null
    }

    val keys = DataKeys(
        fileData = byteArrayOf(), // Or specific header bytes if needed elsewhere
        fileLen = 0L,             // Or actual header length (bytesReadForHeader) if needed
        threads = mutableListOf(),
        methods = mutableListOf()
    )

    var idx = 0
    while (idx < headerLines.size) {
        val line = headerLines[idx]
        if (line.startsWith("*version")) {
            if (idx + 1 < headerLines.size) {
                try {
                    // Assuming versionNumber is a visible (e.g., companion object or top-level) var
                    versionNumber = headerLines[idx + 1].toInt()
                } catch (e: NumberFormatException) {
                    System.err.println("Error parsing version number: '${headerLines[idx + 1]}'")
                    return null
                }
            } else {
                System.err.println("Missing version number after *version tag.")
                return null
            }
            idx += 2
        } else if (line.startsWith("*threads")) {
            idx++
            while (idx < headerLines.size && !headerLines[idx].startsWith("*")) {
                val parts = headerLines[idx].split("\t")
                if (parts.size == 2) {
                    try {
                        keys.threads.add(ThreadEntry(parts[0].toInt(), parts[1]))
                    } catch (e: NumberFormatException) {
                        System.err.println("Error parsing thread entry: '${headerLines[idx]}'")
                        // Optionally skip this entry or return null for strict parsing
                    }
                }
                idx++
            }
        } else if (line.startsWith("*methods")) {
            idx++
            keys.methods.add(MethodEntry(-2, "(toplevel)", null, null, null, -1))
            keys.methods.add(MethodEntry(-1, "(unknown)", null, null, null, -1))
            while (idx < headerLines.size && !headerLines[idx].startsWith("*")) {
                val parts = headerLines[idx].split("\t")
                if (parts.isNotEmpty()) {
                    try {
                        val id = parts[0].removePrefix("0x").toLong(16)
                        val className = if (parts.size > 1) parts[1] else ""
                        val methodName = if (parts.size > 2) parts[2] else null
                        val signature = if (parts.size > 3) parts[3] else null
                        val fileName = if (parts.size > 4) parts[4] else null
                        val lineNum = if (parts.size > 5) parts[5].toIntOrNull() ?: -1 else -1
                        keys.methods.add(
                            MethodEntry(id, className, methodName, signature, fileName, lineNum)
                        )
                    } catch (e: Exception) {
                        System.err.println("Error parsing method entry: '${headerLines[idx]}' - ${e.message}")
                        // Optionally skip or return null
                    }
                }
                idx++
            }
        } else if (line.startsWith("*end")) {
            // Processing of header lines is complete
            break
        } else {
            // Unknown or empty lines in header, skip
            idx++
        }
    }
    keys.numThreads = keys.threads.size
    keys.numMethods = keys.methods.size

    return Pair(keys, bytesReadForHeader)
}

fun dumpTrace(context: Context, filePath: String) {
    val traceData = TraceData()
    val keys = parseDataKeys(traceData, File(filePath))
    println(traceData)
    val outputPath = context.filesDir.absolutePath + "/profiling"
    val outputFile = File(outputPath, "app_launch.pprof")
    keys?.let {
        convertToPprof(
            keys = keys,
            traceData = traceData,
            outputFilePath = outputFile.path
        )
    }
}

fun readDataRecord(
    dis: DataInputStream,
    header: DataHeader,
    threadIdOut: (Int) -> Unit,
    methodValOut: (Int) -> Unit,
    elapsedTimeOut: (Long) -> Unit
): Boolean {
    val threadId = if (header.version.toInt() == 1) dis.readUnsignedByte() else dis.read2LE()
    if (threadId == -1) return true

    val methodVal = dis.read4LE()
    val elapsedTime = dis.read4LE().toLong()

    val remaining = header.recordSize.toInt() - when (header.version.toInt()) {
        1 -> 1 + 4 + 4
        2 -> 2 + 4 + 4
        else -> 0 // version 3 already handled extra above
    }
    dis.skipBytes(remaining)

    threadIdOut(threadId)
    methodValOut(methodVal)
    elapsedTimeOut(elapsedTime)

    return false
}

fun addInclusiveTime(parent: MethodEntry, child: MethodEntry, elapsedTime: ULong) {
    val childIsRecursive = child.recursiveEntries > 0
    val parentIsRecursive = parent.recursiveEntries > 1

    if (child.recursiveEntries == 0) {
        child.elapsedInclusive += elapsedTime
    } else if (child.recursiveEntries == 1) {
        child.recursiveInclusive += elapsedTime
    }
    child.numCalls[if (childIsRecursive) 1 else 0]++

    // Update parent's child list
    var found = false
    val childList = parent.children[if (parentIsRecursive) 1 else 0]
    var node = childList
    while (node != null) {
        if (node.method == child) {
            node.elapsedInclusive += elapsedTime
            node.numCalls++
            found = true
            break
        }
        node = node.next
    }
    if (!found) {
        val newNode = TimedMethod(null, elapsedTime, 1, child)
        newNode.next = childList
        parent.children[if (parentIsRecursive) 1 else 0] = newNode
    }

    // Update child's parent list
    found = false
    val parentList = child.parents[if (childIsRecursive) 1 else 0]
    node = parentList
    while (node != null) {
        if (node.method == parent) {
            node.elapsedInclusive += elapsedTime
            node.numCalls++
            found = true
            break
        }
        node = node.next
    }
    if (!found) {
        val newNode = TimedMethod(null, elapsedTime, 1, parent)
        newNode.next = parentList
        child.parents[if (childIsRecursive) 1 else 0] = newNode
    }
}


// Utility to read little-endian values
fun InputStream.read2LE(): Int {
    val b0 = this.read()
    val b1 = this.read()
    if (b0 < 0 || b1 < 0) throw EOFException()
    return b0 or (b1 shl 8)
}

fun InputStream.read4LE(): Int {
    val b0 = this.read()
    val b1 = this.read()
    val b2 = this.read()
    val b3 = this.read()
    if (b0 or b1 or b2 or b3 < 0) throw EOFException()
    return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
}

fun InputStream.read8LE(): Long {
    var result = 0L
    for (i in 0..7) {
        val b = this.read()
        if (b < 0) throw EOFException()
        result = result or (b.toLong() shl (8 * i))
    }
    return result
}

fun convertToPprof(
    keys: DataKeys,
    traceData: TraceData,
    outputFilePath: String
) {
    val stringIndexUtils = StringIndexUtils()
    val profileBuilder = Profile.newBuilder()
    val functionMap = mutableMapOf<Long, ProfileProto.Function.Builder>()
    val sampleList = mutableListOf<ProfileProto.Sample.Builder>()
    val locationMap = mutableMapOf<Long, ProfileProto.Location.Builder>()
    val locationIdSet = mutableSetOf<Long>()
    val cpuType = ProfileProto.ValueType.newBuilder()
        .setType(stringIndexUtils.getStringIndex("cpu").toLong())
        .setUnit(stringIndexUtils.getStringIndex("nanoseconds").toLong())
    profileBuilder.addSampleType(cpuType)
        .setPeriodType(cpuType)
        .setPeriod(1000L) // TODO: Fix with real interval

    /*keys.methods.forEach { methodEntry ->
        val funcId =
            "${methodEntry.className}.${methodEntry.methodId}.${methodEntry.fileName}".toLongHash()
        val fullSig = "${methodEntry.className}#${methodEntry.methodName}${methodEntry.signature}"
        val functionBuilder = ProfileProto.Function.newBuilder()
            .setId(funcId)
            .setName(stringIndexUtils.getStringIndex(methodEntry.methodName ?: "unknown"))
            .setSystemName(stringIndexUtils.getStringIndex(fullSig))
            .setFilename(stringIndexUtils.getStringIndex(methodEntry.fileName ?: "Unknown Source"))
            .setStartLine(methodEntry.lineNum.toLong())
        functionMap[funcId] = functionBuilder

        val locationBuilder = ProfileProto.Location.newBuilder()
            .setId(funcId) // Using methodId as location ID for simplicity
            .addLine(
                ProfileProto.Line.newBuilder()
                    .setFunctionId(funcId)
                    .setLine(methodEntry.lineNum.toLong())
            )
        locationMap[methodEntry.methodId] = locationBuilder
    }*/
    traceData.stacks.forEachIndexed { threadId, callStack ->
        val locationIdList = mutableListOf<Long>()
        callStack?.calls?.forEach { call ->
            call?.method?.let { methodEntry ->
                val funcId =
                    "${methodEntry.className}.${methodEntry.methodId}.${methodEntry.fileName}".toLongHash()
                val fullSig =
                    "${methodEntry.className}#${methodEntry.methodName}${methodEntry.signature}"
                val locationId = call.hashCode().toLong()
                val functionBuilder = ProfileProto.Function.newBuilder()
                    .setId(funcId)
                    .setName(stringIndexUtils.getStringIndex(methodEntry.methodName ?: "unknown"))
                    .setSystemName(stringIndexUtils.getStringIndex(fullSig))
                    .setFilename(
                        stringIndexUtils.getStringIndex(
                            methodEntry.fileName ?: "Unknown Source"
                        )
                    )
                    .setStartLine(methodEntry.lineNum.toLong())
                functionMap[funcId] = functionBuilder
                if (!locationIdSet.contains(locationId)) {
                    val locationBuilder = ProfileProto.Location.newBuilder()
                        .setId(locationId) // Using methodId as location ID for simplicity
                        .addLine(
                            ProfileProto.Line.newBuilder()
                                .setFunctionId(funcId)
                                .setLine(methodEntry.lineNum.toLong())
                        )
                    locationMap[locationId] = locationBuilder
                }
                locationIdList.add(locationId)
            }
        }

        if (callStack != null && callStack.top > 0) {
            val sampleBuilder = ProfileProto.Sample.newBuilder()
            locationIdList.forEach { sampleBuilder.addLocationId(it) }
            sampleBuilder.addValue(1L) // Set value to 1 (one occurrence)
            sampleBuilder.addLabel(
                ProfileProto.Label.newBuilder()
                    .setKey(stringIndexUtils.getStringIndex("thread_id"))
                    .setNum(threadId.toLong()) // numeric thread ID
            )
            val threadName = keys.threads.getOrNull(threadId)?.threadName ?: "Unknown Thread"
            sampleBuilder.addLabel(
                ProfileProto.Label.newBuilder()
                    .setKey(stringIndexUtils.getStringIndex("thread_name"))
                    .setStr(
                        stringIndexUtils.getStringIndex(threadName)
                    ) // thread name string
            )
            sampleList.add(sampleBuilder)
        }
    }

    val profile = profileBuilder
        .addAllFunction(functionMap.values.map { it.build() })
        .addAllLocation(locationMap.values.map { it.build() })
        .addAllSample(sampleList.map { it.build() })
        .addAllStringTable(stringIndexUtils.getStringTable())
        .setTimeNanos(TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()))
        .setDurationNanos(0)
        .build()
    val output = File(outputFilePath).outputStream()
    profile.writeTo(output)
    output.close()
}

private fun String?.toLongHash(): Long {
    return (this ?: "Unknown").hashCode().toLong() and 0xFFFFFFFFL// Ensure it's a positive long
}
