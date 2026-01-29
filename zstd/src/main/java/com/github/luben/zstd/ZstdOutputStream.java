package com.github.luben.zstd;

import java.io.OutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;

/**
 * OutputStream filter that compresses the data using Zstd compression
 */
public class ZstdOutputStream extends FilterOutputStream {

    private ZstdOutputStreamNoFinalizer inner;

    /**
     * create a new compressing OutputStream
     * @param outStream the stream to wrap
     */
    public ZstdOutputStream(OutputStream outStream) throws IOException {
        this(outStream, NoPool.INSTANCE);
    }

    /**
     * create a new compressing OutputStream
     * @param outStream the stream to wrap
     * @param bufferPool the pool to fetch and return buffers
     */
    public ZstdOutputStream(OutputStream outStream, BufferPool bufferPool) throws IOException {
        super(outStream);
        inner = new ZstdOutputStreamNoFinalizer(outStream, bufferPool);
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }

    public void write(byte[] src, int offset, int len) throws IOException {
        inner.write(src, offset, len);
    }

    public void write(int i) throws IOException {
        inner.write(i);
    }

    /**
     * Flushes the output
     */
    public void flush() throws IOException {
        inner.flush();
    }

    public void close() throws IOException {
        inner.close();
    }
}
