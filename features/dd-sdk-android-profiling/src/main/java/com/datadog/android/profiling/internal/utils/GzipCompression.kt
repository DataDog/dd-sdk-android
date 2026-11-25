/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.utils

import com.datadog.android.api.InternalLogger
import okio.Buffer
import okio.ForwardingSink
import okio.GzipSink
import okio.Sink
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException

internal const val COMPRESSION_FAILED_MESSAGE = "Failed to gzip-compress the Perfetto trace."

/**
 * Compresses [file] as a gzip stream of at most [maxBytes], or reports the trace as raw when
 * compression fails, as long as the raw trace fits [maxBytes]. [maxBytes] bounds the stored
 * attachment: the compressed output when compressing, the raw trace on the fallback.
 */
internal fun compressFileSafe(file: File, maxBytes: Int, internalLogger: InternalLogger): CompressionResult {
    val sizeBytes = fileSizeSafe(file.path, internalLogger)
    if (sizeBytes <= 0L) return CompressionResult.Unreadable
    return try {
        compressFile(file, maxBytes)
    } catch (e: CompressedSizeLimitException) {
        CompressionResult.TooLarge(e.compressedSizeBytes, compressed = true)
    } catch (e: IOException) {
        rawFallback(maxBytes, sizeBytes, internalLogger, e)
    } catch (e: SecurityException) {
        rawFallback(maxBytes, sizeBytes, internalLogger, e)
    }
}

// Only states that the compression failed: what happens next can be a raw save, a drop as too
// large, or an unreadable trace, depending on why the compression failed.
private fun logCompressionFailure(internalLogger: InternalLogger, throwable: Throwable) {
    internalLogger.log(
        InternalLogger.Level.ERROR,
        InternalLogger.Target.MAINTAINER,
        { COMPRESSION_FAILED_MESSAGE },
        throwable,
        onlyOnce = true
    )
}

// Streams the file through gzip into an Okio Buffer, which keeps the output as small segments: there is no
// array that grows by copying, and the only copy is the exact-size array handed over at the end. The
// size-limited sink aborts with CompressedSizeLimitException as soon as the output exceeds maxBytes, so
// memory stays bounded.
// Okio writes a standard gzip stream at the default level.
@Throws(IOException::class)
private fun compressFile(file: File, maxBytes: Int): CompressionResult.Compressed {
    val output = Buffer()
    // The file source and the buffered gzip sink only fail with errors handled in compressFileSafe
    @Suppress("UnsafeThirdPartyFunctionCall")
    file.source().use { input ->
        GzipSink(SizeLimitedSink(output, maxBytes.toLong())).buffer().use { gzipSink ->
            gzipSink.writeAll(input)
        }
    }
    // The whole output buffer is read, so this cannot reach the end of the data early
    @Suppress("UnsafeThirdPartyFunctionCall")
    val bytes = output.readByteArray()
    return CompressionResult.Compressed(bytes)
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

// sizeBytes is the size of the raw trace, measured before compressing: a trace that could not be
// measured was already reported as unreadable, so only the limit matters here.
private fun rawFallbackResult(sizeBytes: Long, maxBytes: Int): CompressionResult =
    if (sizeBytes > maxBytes) {
        CompressionResult.TooLarge(sizeBytes, compressed = false)
    } else {
        CompressionResult.Raw(sizeBytes)
    }

/**
 * What to save for the Perfetto trace.
 */
internal sealed class CompressionResult {

    /** A standard gzip stream of the trace: [bytes] is exactly the stream. */
    internal class Compressed(val bytes: ByteArray) : CompressionResult()

    /** The trace is uncompressed, [sizeBytes] long. */
    internal class Raw(val sizeBytes: Long) : CompressionResult()

    /**
     * The trace is over the limit. When [compressed], [sizeBytes] is the compressed size the
     * stream had reached when it aborted; otherwise it is the measured size of the raw trace.
     */
    internal class TooLarge(val sizeBytes: Long, val compressed: Boolean) : CompressionResult()

    /** The trace could not be read. */
    internal object Unreadable : CompressionResult()
}

internal class CompressedSizeLimitException(val compressedSizeBytes: Long) :
    IOException("Compressed profile exceeds the size limit")

// Counts what is written to the delegate and fails as soon as it exceeds maxBytes, reporting the size reached.
private class SizeLimitedSink(delegate: Sink, private val maxBytes: Long) : ForwardingSink(delegate) {

    private var written = 0L

    @Throws(IOException::class)
    override fun write(source: Buffer, byteCount: Long) {
        written += byteCount
        @Suppress("ThrowingInternalException") // Caught in compressFileSafe to drop the profile
        if (written > maxBytes) throw CompressedSizeLimitException(written)
        // The IOException of the delegate is declared above and handled in compressFileSafe
        @Suppress("UnsafeThirdPartyFunctionCall")
        super.write(source, byteCount)
    }
}
