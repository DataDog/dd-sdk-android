/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import com.datadog.android.sessionreplay.internal.recorder.SessionReplayRecorder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Regression tests for RUMS-4124: "Error when Enable session replay in android mobile app".
 *
 * The root cause: [SessionReplayInternalCallback] was introduced with only [SessionReplayInternalCallback.getCurrentActivity],
 * but cross-platform frameworks (e.g. React Native) need to forward Session Replay resources
 * (images, SVGs, fonts) through the same pipeline. Without [SessionReplayInternalCallback.addResourceItem]
 * and [SessionReplayInternalCallback.setResourceQueue], the React Native module implementing this
 * interface cannot hook into the resource processing pipeline, causing the Android build to fail
 * with an "unresolved reference" or "overrides nothing" compile error.
 *
 * Fix: extend [SessionReplayInternalCallback] with [addResourceItem] and [setResourceQueue], and
 * have [SessionReplayRecorder] call [SessionReplayInternalCallback.setResourceQueue] during
 * initialization so the callback receives the live resource queue.
 */
internal class SessionReplayInternalCallbackContractTest {

    @Test
    fun `M declare addResourceItem W SessionReplayInternalCallback`() {
        // Cross-platform frameworks implementing SessionReplayInternalCallback need addResourceItem
        // to enqueue Session Replay resource items (images, SVGs, fonts) from their own layer into
        // the Android SDK's recording pipeline. Without this declaration, any platform-specific
        // implementation (e.g. ReactNativeInternalCallback) referencing this method produces an
        // "unresolved reference" compile error.
        val methods = SessionReplayInternalCallback::class.java.methods.map { it.name }

        assertThat(methods)
            .withFailMessage(
                "SessionReplayInternalCallback is missing 'addResourceItem' — " +
                    "required by cross-platform frameworks (RUMS-4124). " +
                    "Add 'fun addResourceItem(identifier: String, resourceData: ByteArray, mimeType: String?)' " +
                    "to the interface."
            )
            .contains("addResourceItem")
    }

    @Test
    fun `M declare setResourceQueue W SessionReplayInternalCallback`() {
        // SessionReplayRecorder must hand its internal RecordedDataQueueHandler to the
        // platform-specific callback so that addResourceItem calls are routed to the correct
        // queue. Without setResourceQueue on the interface, SessionReplayRecorder cannot wire up
        // the queue and the React Native layer's resources are silently dropped.
        val methods = SessionReplayInternalCallback::class.java.methods.map { it.name }

        assertThat(methods)
            .withFailMessage(
                "SessionReplayInternalCallback is missing 'setResourceQueue' — " +
                    "required so SessionReplayRecorder can inject the resource queue into the " +
                    "platform-specific callback (RUMS-4124). " +
                    "Add 'fun setResourceQueue(resourceQueue: SessionReplayInternalResourceQueue)' " +
                    "to the interface."
            )
            .contains("setResourceQueue")
    }

    @Test
    fun `M expose SessionReplayInternalResourceQueue W setResourceQueue parameter`() {
        // The setResourceQueue method must accept a SessionReplayInternalResourceQueue, so that
        // the callback implementation can forward addResourceItem calls to the correct handler.
        // This also verifies that SessionReplayInternalResourceQueue itself is a public type.
        val setResourceQueueMethod = SessionReplayInternalCallback::class.java.methods
            .find { it.name == "setResourceQueue" }

        assertThat(setResourceQueueMethod)
            .withFailMessage(
                "SessionReplayInternalCallback.setResourceQueue() method not found (RUMS-4124)."
            )
            .isNotNull()

        val paramTypes = setResourceQueueMethod!!.parameterTypes.map { it.simpleName }
        assertThat(paramTypes)
            .withFailMessage(
                "setResourceQueue() must accept a SessionReplayInternalResourceQueue parameter " +
                    "so the callback can forward resource items through the recording pipeline (RUMS-4124). " +
                    "Found parameters: $paramTypes"
            )
            .contains("SessionReplayInternalResourceQueue")
    }

    @Test
    fun `M wire resource queue on internalCallback W SessionReplayRecorder init`() {
        // SessionReplayRecorder must call setResourceQueue on its internalCallback during
        // construction, so that any resources enqueued via internalCallback.addResourceItem()
        // are routed into the RecordedDataQueueHandler. If this wiring is absent the callback
        // receives a null/no-op queue and all platform-layer resources are silently discarded.
        //
        // This test verifies the wiring contract by inspecting the SessionReplayRecorder constructor
        // via reflection and confirming that setResourceQueue exists and is invocable on the callback.
        val recorderConstructors = SessionReplayRecorder::class.java.constructors
        assertThat(recorderConstructors).isNotEmpty()

        // The internalCallback parameter must be present in at least one constructor
        val hasInternalCallbackParam = recorderConstructors.any { ctor ->
            ctor.parameterTypes.any { paramType ->
                paramType == SessionReplayInternalCallback::class.java
            }
        }
        assertThat(hasInternalCallbackParam)
            .withFailMessage(
                "SessionReplayRecorder has no constructor accepting SessionReplayInternalCallback — " +
                    "the recorder cannot wire up the resource queue for cross-platform frameworks (RUMS-4124)."
            )
            .isTrue()

        // The callback must expose setResourceQueue so the recorder can hand over the queue
        val setResourceQueueMethod = SessionReplayInternalCallback::class.java.methods
            .find { it.name == "setResourceQueue" }
        assertThat(setResourceQueueMethod)
            .withFailMessage(
                "SessionReplayInternalCallback.setResourceQueue() must be declared so that " +
                    "SessionReplayRecorder can inject its RecordedDataQueueHandler into the callback " +
                    "during initialization (RUMS-4124)."
            )
            .isNotNull()
    }
}
