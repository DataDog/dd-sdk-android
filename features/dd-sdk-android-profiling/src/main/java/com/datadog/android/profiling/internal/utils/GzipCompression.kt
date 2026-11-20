/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.utils

import com.datadog.android.api.InternalLogger
import okio.GzipSink
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import java.io.OutputStream

internal const val COMPRESSION_FAILED_MESSAGE = "Failed to gzip-compress the Perfetto trace."

/**
 * Compresses [file] as a gzip stream of at most [maxBytes], or reports the trace as raw when
 * compression fails, as long as the raw trace fits [maxBytes].
 */
internal fun compressFileSafe(file: File, maxBytes: Int, internalLogger: InternalLogger): CompressionResult {
    val sizeBytes = fileSizeSafe(file.path, internalLogger)
    if (sizeBytes <= 0L) return CompressionResult.Unreadable
    return try {
        compressFile(file, maxBytes)
    } catch (e: CompressedSizeLimitException) {
        CompressionResult.TooLarge(e.compressedSizeBytes)
    } catch (e: IOException) {
        rawFallback(maxBytes, sizeBytes, internalLogger, e)
    } catch (e: SecurityException) {
        rawFallback(maxBytes, sizeBytes, internalLogger, e)
    }
}

// Only states that the compression failed: what happens next can be a raw save, a drop as too
// large, or an unreadable trace, depending on why the compression failed.
internal fun logCompressionFailure(internalLogger: InternalLogger, throwable: Throwable) {
    internalLogger.log(
        InternalLogger.Level.ERROR,
        InternalLogger.Target.MAINTAINER,
        { COMPRESSION_FAILED_MESSAGE },
        throwable,
        onlyOnce = true
    )
}

// Streams the file through gzip so peak memory stays around the compressed output, and aborts with
// CompressedSizeLimitException as soon as the output exceeds maxBytes. Okio writes a standard gzip
// stream at the default level.
@Throws(IOException::class)
private fun compressFile(file: File, maxBytes: Int): CompressionResult.Compressed {
    val sink = BoundedByteArrayOutputStream(maxBytes)
    // The stream, its gzip sink and its buffer only fail with errors handled in compressFileSafe
    @Suppress("UnsafeThirdPartyFunctionCall")
    file.inputStream().use { input ->
        GzipSink(sink.sink()).buffer().use { gzipSink ->
            gzipSink.writeAll(input.source())
        }
    }
    return CompressionResult.Compressed(sink.buffer, sink.size)
}

// A partial stream is never saved: the raw trace is handed over by path, and the caller streams it
// into the metadata blob, so no raw copy is held here. The size measured before compressing is
// reused: the writer streams the raw file through encode(stream, size, rum), which rejects a trace
// truncated or grown since it was measured.
private fun rawFallback(
    maxBytes: Int,
    sizeBytes: Long,
    internalLogger: InternalLogger,
    error: Throwable
): CompressionResult {
    logCompressionFailure(internalLogger, error)
    return rawFallbackResult(sizeBytes, maxBytes)
}

// The measured size decides the state on its own, so the decision is testable without a failure.
internal fun rawFallbackResult(sizeBytes: Long, maxBytes: Int): CompressionResult = when {
    sizeBytes <= 0L -> CompressionResult.Unreadable
    sizeBytes > maxBytes -> CompressionResult.TooLarge(sizeBytes)
    else -> CompressionResult.Raw(sizeBytes)
}

/**
 * What to save for the Perfetto trace.
 */
internal sealed class CompressionResult {

    /**
     * A standard gzip stream of the trace: the first [length] bytes of [bytes] are the stream.
     * [bytes] is the buffer the stream was written into, so it is longer than [length] unless the
     * stream filled it exactly.
     */
    internal class Compressed(val bytes: ByteArray, val length: Int) : CompressionResult()

    /** The trace is uncompressed, [sizeBytes] long. */
    internal class Raw(val sizeBytes: Long) : CompressionResult()

    /** The trace reached [sizeBytes], over the limit. */
    internal class TooLarge(val sizeBytes: Long) : CompressionResult()

    /** The trace could not be read. */
    internal object Unreadable : CompressionResult()
}

internal class CompressedSizeLimitException(val compressedSizeBytes: Long) :
    IOException("Compressed profile exceeds the size limit")

// Owns its buffer so it starts small and its capacity never grows past maxBytes: peak memory is at
// most two buffers of maxBytes, during a growth copy.
internal class BoundedByteArrayOutputStream(private val maxBytes: Int) : OutputStream() {

    var size = 0
        private set

    private var storage = ByteArray(minOf(INITIAL_BUFFER_BYTES, maxBytes))

    /**
     * The backing array: as long as the capacity, so at least [size] and never longer than maxBytes.
     * Callers must honour [size] and ignore the rest.
     */
    internal val buffer: ByteArray
        get() = storage

    override fun write(b: Int) {
        ensureCapacity(1)
        storage[size] = b.toByte()
        size++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        ensureCapacity(len)
        @Suppress("UnsafeThirdPartyFunctionCall") // Callers pass valid ranges
        System.arraycopy(b, off, storage, size, len)
        size += len
    }

    private fun ensureCapacity(len: Int) {
        val newSize = size + len
        @Suppress("ThrowingInternalException") // Caught in compressFileSafe to drop the profile
        if (newSize > maxBytes) throw CompressedSizeLimitException(newSize.toLong())
        if (newSize > storage.size) {
            val newCapacity = minOf(maxOf(storage.size * 2L, newSize.toLong()), maxBytes.toLong())
            @Suppress("UnsafeThirdPartyFunctionCall") // newCapacity is positive and at most maxBytes
            storage = storage.copyOf(newCapacity.toInt())
        }
    }

    companion object {
        private const val INITIAL_BUFFER_BYTES = 8 * 1024
    }
}
