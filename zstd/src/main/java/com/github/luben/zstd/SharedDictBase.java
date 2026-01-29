package com.github.luben.zstd;

import java.io.Closeable;

abstract class SharedDictBase implements Closeable {
    private boolean closed = false;
    private volatile int fence = 0;

    @Override
    public synchronized void close() {
        if (!closed) {
            doClose();
            closed = true;
        }
    }

    abstract void doClose();

    protected void storeFence() {
        fence = 1;
    }

    @Override
    protected void finalize() {
        close();
    }
}
