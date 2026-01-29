package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;
import java.nio.ByteBuffer;

public class Zstd {
    static {
        Native.load();
    }

    public static native int setCompressionLevel(long stream, int level);
    public static native int setCompressionChecksums(long stream, boolean useChecksums);
    public static native int setCompressionLong(long stream, int windowLog);
    public static native int setCompressionWorkers(long stream, int workers);
    public static native int setCompressionOverlapLog(long stream, int overlapLog);
    public static native int setCompressionJobSize(long stream, int jobSize);
    public static native int setCompressionTargetLength(long stream, int targetLength);
    public static native int setCompressionMinMatch(long stream, int minMatch);
    public static native int setCompressionSearchLog(long stream, int searchLog);
    public static native int setCompressionChainLog(long stream, int chainLog);
    public static native int setCompressionHashLog(long stream, int hashLog);
    public static native int setCompressionWindowLog(long stream, int windowLog);
    public static native int setCompressionStrategy(long stream, int strategy);

    public static native int loadDictCompress(long stream, byte[] dict, int dict_size);
    public static native int loadFastDictCompress(long stream, ZstdDictCompress dict);

    public static native int defaultCompressionLevel();

    public static native boolean isError(long code);
    public static native String  getErrorName(long code);
    public static native long    getErrorCode(long code);

    public static native long errMemoryAllocation();

    static ByteBuffer getArrayBackedBuffer(BufferPool bufferPool, int size) throws ZstdIOException {
        ByteBuffer buffer = bufferPool.get(size);
        if (buffer == null) {
            throw new ZstdIOException(Zstd.errMemoryAllocation(), "Cannot get ByteBuffer of size " + size + " from the BufferPool");
        }
        if (!buffer.hasArray() || buffer.arrayOffset() != 0) {
            bufferPool.release(buffer);
            throw new IllegalArgumentException("provided ByteBuffer lacks array or has non-zero arrayOffset");
        }
        return buffer;
    }
}
