package com.github.luben.zstd;

import java.io.OutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import com.github.luben.zstd.util.Native;

/**
 * OutputStream filter that compresses the data using Zstd compression.
 */
public class ZstdOutputStreamNoFinalizer extends FilterOutputStream {

    static {
        Native.load();
    }

    /* Opaque pointer to Zstd context object */
    private final long stream;
    // Source and Destination positions
    private long srcPos = 0;
    private long dstPos = 0;
    private final BufferPool bufferPool;
    private final ByteBuffer dstByteBuffer;
    private final byte[] dst;
    private boolean isClosed = false;
    private static final int dstSize = (int) recommendedCOutSize();
    private boolean frameClosed = true;
    private boolean frameStarted = false;

    /* JNI methods */
    public static native long recommendedCOutSize();
    private static native long createCStream();
    private static native int  freeCStream(long ctx);
    private native int resetCStream(long ctx);
    private native int compressStream(long ctx, byte[] dst, int dst_size, byte[] src, int src_size);
    private native int flushStream(long ctx, byte[] dst, int dst_size);
    private native int endStream(long ctx, byte[] dst, int dst_size);

    /**
     * create a new compressing OutputStream
     * @param outStream the stream to wrap
     */
    public ZstdOutputStreamNoFinalizer(OutputStream outStream) throws IOException {
        this(outStream, NoPool.INSTANCE);
    }

    /**
     * create a new compressing OutputStream
     * @param outStream the stream to wrap
     * @param bufferPool the pool to fetch and return buffers
     */
    public ZstdOutputStreamNoFinalizer(OutputStream outStream, BufferPool bufferPool) throws IOException {
        super(outStream);
        // create compression context
        this.stream = createCStream();
        this.bufferPool = bufferPool;
        this.dstByteBuffer = Zstd.getArrayBackedBuffer(bufferPool, dstSize);
        this.dst = dstByteBuffer.array();
    }

    public synchronized void write(byte[] src, int offset, int len) throws IOException {
        if (isClosed) {
            throw new IOException("StreamClosed");
        }
        if (frameClosed) {
            int size = resetCStream(this.stream);
            if (Zstd.isError(size)) {
                throw new ZstdIOException(size);
            }
            frameClosed = false;
            frameStarted = true;
        }
        int srcSize = offset + len;
        srcPos = offset;
        while (srcPos < srcSize) {
            int size = compressStream(stream, dst, dstSize, src, srcSize);
            if (Zstd.isError(size)) {
                throw new ZstdIOException(size);
            }
            if (dstPos > 0) {
                out.write(dst, 0, (int) dstPos);
            }
        }
    }

    public void write(int i) throws IOException {
        byte[] oneByte = new byte[1];
        oneByte[0] = (byte) i;
        write(oneByte, 0, 1);
    }

    /**
     * Flushes the output
     */
    public synchronized void flush() throws IOException {
        if (isClosed) {
            throw new IOException("StreamClosed");
        }
        if (!frameClosed) {
            // compress the remaining input
            int size;
            do {
                size = flushStream(stream, dst, dstSize);
                if (Zstd.isError(size)) {
                    throw new ZstdIOException(size);
                }
                out.write(dst, 0, (int) dstPos);
            } while (size > 0);
            out.flush();
        }
    }

    public synchronized void close() throws IOException {
        close(true);
    }

    public synchronized void closeWithoutClosingParentStream() throws IOException {
        close(false);
    }

    private void close(boolean closeParentStream) throws IOException {
        if (isClosed) {
            return;
        }
        try {
            int size;
            // Closing the stream without before writing anything
            // should still produce valid zstd frame. So reset the
            // stream to start a frame if no frame was ever started.
            if (!frameStarted) {
                size = resetCStream(this.stream);
                if (Zstd.isError(size)) {
                    throw new ZstdIOException(size);
                }
                frameClosed = false;
            }
            // compress the remaining input and close the frame
            if (!frameClosed) {
                do {
                    size = endStream(stream, dst, dstSize);
                    if (Zstd.isError(size)) {
                        throw new ZstdIOException(size);
                    }
                    out.write(dst, 0, (int) dstPos);
                } while (size > 0);
            }
            if (closeParentStream) {
                out.close();
            }
        } finally {
            // release the resources even if underlying stream throw an exception
            isClosed = true;
            bufferPool.release(dstByteBuffer);
            freeCStream(stream);
        }
    }
}
