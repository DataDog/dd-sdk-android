/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.remote

import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.TimeProvider
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.io.File
import java.util.concurrent.Executor

@Tag("FormalMethods")
internal class FormalMethodsRemoteConfigDiagnosticsTest {

    @TempDir
    lateinit var storageDir: File

    @Test
    fun `known counterexample - queued sync uses fetcher after stop releases it`() {
        val queuedTasks = ArrayDeque<Runnable>()
        val executor = Executor { queuedTasks.addLast(it) }
        val fetcher = mock<RemoteConfigFetcher>()
        val service = RemoteConfigServiceImpl(
            remoteConfigurationId = "formal-methods-rc-id",
            remoteConfigurationEndpoint = "https://example.com".toHttpUrl(),
            fetcher = fetcher,
            storageDir = storageDir,
            executor = executor,
            internalLogger = mock<InternalLogger>(),
            timeProvider = mock<TimeProvider>()
        )

        service.syncWithRemote()
        assertThat(queuedTasks).hasSize(1)

        service.stop()
        verify(fetcher).release()

        queuedTasks.removeFirst().run()

        verify(fetcher).fetch(any())
    }

    @Test
    fun `known counterexample - sync requested after stop still uses released fetcher`() {
        val fetcher = mock<RemoteConfigFetcher>()
        val service = RemoteConfigServiceImpl(
            remoteConfigurationId = "formal-methods-rc-id",
            remoteConfigurationEndpoint = "https://example.com".toHttpUrl(),
            fetcher = fetcher,
            storageDir = storageDir,
            executor = Executor { it.run() },
            internalLogger = mock<InternalLogger>(),
            timeProvider = mock<TimeProvider>()
        )

        service.stop()
        service.syncWithRemote()

        verify(fetcher).release()
        verify(fetcher, times(1)).fetch(any())
    }
}
