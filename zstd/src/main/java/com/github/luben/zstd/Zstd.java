package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;
import java.nio.ByteBuffer;

public class Zstd {
    static {
        Native.load();
    }

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
