/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.anr

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.verifyLog
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException
import java.io.InputStream

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class AndroidTraceParserTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var testedParser: AndroidTraceParser

    @BeforeEach
    fun `set up`() {
        testedParser = AndroidTraceParser(mockInternalLogger)
    }

    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    @Test
    fun `M return threads dump W parse()`() {
        // Given
        val traceStream = javaClass.classLoader.getResourceAsStream("anr_crash_trace.txt")

        // When
        val threadsDump = testedParser.parse(traceStream)

        // Then
        assertThat(threadsDump).isNotEmpty
        assertThat(threadsDump.filter { it.crashed }).hasSize(1)
        assertThat(threadsDump).allMatch { it.stack.isNotEmpty() }

        assertThat(threadsDump.filter { it.name == "main" }).hasSize(1)
        val mainThread = threadsDump.first { it.name == "main" }
        assertThat(mainThread.stack).isEqualTo(MAIN_THREAD_STACK)
        assertThat(mainThread.state).isEqualTo("runnable")
        assertThat(mainThread.crashed).isTrue()

        assertThat(threadsDump.filter { it.name == "OkHttp browser-intake-datadoghq.com" })
            .hasSize(1)
        val mixedJavaNativeThread =
            threadsDump.first { it.name == "OkHttp browser-intake-datadoghq.com" }
        assertThat(mixedJavaNativeThread.stack).isEqualTo(JAVA_AND_NDK_THREAD_STACK)
        assertThat(mixedJavaNativeThread.state).isEqualTo("native")
        assertThat(mixedJavaNativeThread.crashed).isFalse()
    }

    @Test
    fun `M return empty list W parse() { malformed trace }`(
        @StringForgery fakeTrace: String
    ) {
        // When
        val threadsDump = testedParser.parse(fakeTrace.byteInputStream())

        // Then
        assertThat(threadsDump).isEmpty()

        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            message = AndroidTraceParser.PARSING_FAILURE_MESSAGE
        )
    }

    @Test
    fun `M return empty list W parse() { error reading stream }`() {
        // When
        val mockStream = mock<InputStream>().apply {
            whenever(read()) doThrow IOException()
        }
        val threadsDump = testedParser.parse(mockStream)

        // Then
        assertThat(threadsDump).isEmpty()

        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            AndroidTraceParser.TRACE_STREAM_READ_FAILURE,
            throwableClass = IOException::class.java
        )
    }

    companion object {
        const val MAIN_THREAD_STACK =
            """  at android.graphics.Paint.getNativeInstance(Paint.java:743)
  at android.graphics.BaseRecordingCanvas.drawRect(BaseRecordingCanvas.java:364)
  at com.datadog.android.sample.vitals.BadView.onDraw(BadView.kt:72)
  at android.view.View.draw(View.java:23889)
  at android.view.View.updateDisplayListIfDirty(View.java:22756)
  at android.view.ViewGroup.recreateChildDisplayList(ViewGroup.java:4540)
  at android.view.ViewGroup.dispatchGetDisplayList(ViewGroup.java:4513)
  at android.view.View.updateDisplayListIfDirty(View.java:22712)
  at android.view.ViewGroup.recreateChildDisplayList(ViewGroup.java:4540)
  at android.view.ViewGroup.dispatchGetDisplayList(ViewGroup.java:4513)
  at android.view.View.updateDisplayListIfDirty(View.java:22712)
  at android.view.ViewGroup.recreateChildDisplayList(ViewGroup.java:4540)
  at android.view.ViewGroup.dispatchGetDisplayList(ViewGroup.java:4513)
  at android.view.View.updateDisplayListIfDirty(View.java:22712)
  at android.view.ViewGroup.recreateChildDisplayList(ViewGroup.java:4540)
  at android.view.ViewGroup.dispatchGetDisplayList(ViewGroup.java:4513)
  at android.view.View.updateDisplayListIfDirty(View.java:22712)
  at android.view.ViewGroup.recreateChildDisplayList(ViewGroup.java:4540)
  at android.view.ViewGroup.dispatchGetDisplayList(ViewGroup.java:4513)
  at android.view.View.updateDisplayListIfDirty(View.java:22712)
  at android.view.ViewGroup.recreateChildDisplayList(ViewGroup.java:4540)
  at android.view.ViewGroup.dispatchGetDisplayList(ViewGroup.java:4513)
  at android.view.View.updateDisplayListIfDirty(View.java:22712)
  at android.view.ViewGroup.recreateChildDisplayList(ViewGroup.java:4540)
  at android.view.ViewGroup.dispatchGetDisplayList(ViewGroup.java:4513)
  at android.view.View.updateDisplayListIfDirty(View.java:22712)
  at android.view.ViewGroup.recreateChildDisplayList(ViewGroup.java:4540)
  at android.view.ViewGroup.dispatchGetDisplayList(ViewGroup.java:4513)
  at android.view.View.updateDisplayListIfDirty(View.java:22712)
  at android.view.ViewGroup.recreateChildDisplayList(ViewGroup.java:4540)
  at android.view.ViewGroup.dispatchGetDisplayList(ViewGroup.java:4513)
  at android.view.View.updateDisplayListIfDirty(View.java:22712)
  at android.view.ThreadedRenderer.updateViewTreeDisplayList(ThreadedRenderer.java:694)
  at android.view.ThreadedRenderer.updateRootDisplayList(ThreadedRenderer.java:700)
  at android.view.ThreadedRenderer.draw(ThreadedRenderer.java:798)
  at android.view.ViewRootImpl.draw(ViewRootImpl.java:4939)
  at android.view.ViewRootImpl.performDraw(ViewRootImpl.java:4643)
  at android.view.ViewRootImpl.performTraversals(ViewRootImpl.java:3822)
  at android.view.ViewRootImpl.doTraversal(ViewRootImpl.java:2465)
  at android.view.ViewRootImpl${'$'}TraversalRunnable.run(ViewRootImpl.java:9305)
  at android.view.Choreographer${'$'}CallbackRecord.run(Choreographer.java:1339)
  at android.view.Choreographer${'$'}CallbackRecord.run(Choreographer.java:1348)
  at android.view.Choreographer.doCallbacks(Choreographer.java:952)
  at android.view.Choreographer.doFrame(Choreographer.java:882)
  at android.view.Choreographer${'$'}FrameDisplayEventReceiver.run(Choreographer.java:1322)
  at android.os.Handler.handleCallback(Handler.java:958)
  at android.os.Handler.dispatchMessage(Handler.java:99)
  at android.os.Looper.loopOnce(Looper.java:205)
  at android.os.Looper.loop(Looper.java:294)
  at android.app.ActivityThread.main(ActivityThread.java:8177)
  at java.lang.reflect.Method.invoke(Native method)
  at com.android.internal.os.RuntimeInit${'$'}MethodAndArgsCaller.run(RuntimeInit.java:552)
  at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:971)"""

        const val JAVA_AND_NDK_THREAD_STACK =
            """  native: #00 pc 00062e1c  /apex/com.android.runtime/lib64/bionic/libc.so (syscall+28) (BuildId: a87908b48b368e6282bcc9f34bcfc28c)
  native: #01 pc 0022cfac  /apex/com.android.art/lib64/libart.so (art::ConditionVariable::WaitHoldingLocks+140) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #02 pc 0039978c  /apex/com.android.art/lib64/libart.so (art::::CheckJNI::SetPrimitiveArrayRegion +1352) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #03 pc 0002de34  /apex/com.android.art/lib64/libopenjdk.so (SocketInputStream_socketRead0+260) (BuildId: fc4c0ac2dde70b1afe348b962a85a634)
  native: #04 pc 00377030  /apex/com.android.art/lib64/libart.so (art_quick_generic_jni_trampoline+144) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #05 pc 003605a4  /apex/com.android.art/lib64/libart.so (art_quick_invoke_stub+612) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #06 pc 003ae360  /apex/com.android.art/lib64/libart.so (art::interpreter::ArtInterpreterToCompiledCodeBridge+320) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #07 pc 00398584  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<true>+1488) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #08 pc 0050cf2c  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>+12964) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #09 pc 003797d8  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #10 pc 00145c98  /apex/com.android.art/javalib/core-oj.jar (java.net.SocketInputStream.socketRead)
  native: #11 pc 0037cde0  /apex/com.android.art/lib64/libart.so (art::interpreter::Execute +356) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #12 pc 00398d78  /apex/com.android.art/lib64/libart.so (art::interpreter::ArtInterpreterToInterpreterBridge+100) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #13 pc 00398520  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<true>+1388) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #14 pc 0050cf2c  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>+12964) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #15 pc 003797d8  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #16 pc 00145b14  /apex/com.android.art/javalib/core-oj.jar (java.net.SocketInputStream.read)
  native: #17 pc 0037cde0  /apex/com.android.art/lib64/libart.so (art::interpreter::Execute +356) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #18 pc 0049120c  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<false>+4152) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #19 pc 00509f94  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>+780) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #20 pc 003797d8  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #21 pc 00145aec  /apex/com.android.art/javalib/core-oj.jar (java.net.SocketInputStream.read)
  native: #22 pc 0037cde0  /apex/com.android.art/lib64/libart.so (art::interpreter::Execute +356) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #23 pc 0037c560  /apex/com.android.art/lib64/libart.so (artQuickToInterpreterBridge+672) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #24 pc 00377168  /apex/com.android.art/lib64/libart.so (art_quick_to_interpreter_bridge+88) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #25 pc 0058acb0  /apex/com.android.art/lib64/libart.so (nterp_helper+4016) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #26 pc 0001818e  /apex/com.android.conscrypt/javalib/conscrypt.jar (com.android.org.conscrypt.ConscryptEngineSocket${'$'}SSLInputStream.readFromSocket+50)
  native: #27 pc 0058ac54  /apex/com.android.art/lib64/libart.so (nterp_helper+3924) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #28 pc 0001800e  /apex/com.android.conscrypt/javalib/conscrypt.jar (com.android.org.conscrypt.ConscryptEngineSocket${'$'}SSLInputStream.processDataFromSocket+350)
  native: #29 pc 0058ac54  /apex/com.android.art/lib64/libart.so (nterp_helper+3924) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #30 pc 000181d2  /apex/com.android.conscrypt/javalib/conscrypt.jar (com.android.org.conscrypt.ConscryptEngineSocket${'$'}SSLInputStream.readUntilDataAvailable+2)
  native: #31 pc 0058ac54  /apex/com.android.art/lib64/libart.so (nterp_helper+3924) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #32 pc 0001812c  /apex/com.android.conscrypt/javalib/conscrypt.jar (com.android.org.conscrypt.ConscryptEngineSocket${'$'}SSLInputStream.read+16)
  native: #33 pc 003605a4  /apex/com.android.art/lib64/libart.so (art_quick_invoke_stub+612) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #34 pc 004906b4  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<false>+1248) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #35 pc 00509f94  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>+780) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #36 pc 003797d8  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #37 pc 0008a120  <anonymous:7212f09000> (okio.InputStreamSource.read)
  native: #38 pc 0037cde0  /apex/com.android.art/lib64/libart.so (art::interpreter::Execute +356) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #39 pc 0049120c  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<false>+4152) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #40 pc 0050aca4  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>+4124) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #41 pc 003797d8  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #42 pc 0007f0f0  <anonymous:7212f09000> (okio.AsyncTimeout${'$'}source${'$'}1.read)
  native: #43 pc 0037cde0  /apex/com.android.art/lib64/libart.so (art::interpreter::Execute +356) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #44 pc 0049120c  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<false>+4152) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #45 pc 0050aca4  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>+4124) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #46 pc 003797d8  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #47 pc 0008f1a4  <anonymous:7212f09000> (okio.RealBufferedSource.request)
  native: #48 pc 0037cde0  /apex/com.android.art/lib64/libart.so (art::interpreter::Execute +356) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #49 pc 0049120c  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<false>+4152) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #50 pc 00509f94  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>+780) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #51 pc 003797d8  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #52 pc 000903ec  <anonymous:7212f09000> (okio.RealBufferedSource.require)
  native: #53 pc 0037cde0  /apex/com.android.art/lib64/libart.so (art::interpreter::Execute +356) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #54 pc 0049120c  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<false>+4152) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #55 pc 0050aca4  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>+4124) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #56 pc 003797d8  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #57 pc 00071904  <anonymous:7212f09000> (okhttp3.internal.http2.Http2Reader.nextFrame)
  native: #58 pc 0037cde0  /apex/com.android.art/lib64/libart.so (art::interpreter::Execute +356) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #59 pc 0049120c  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<false>+4152) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #60 pc 00509f94  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>+780) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #61 pc 003797d8  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #62 pc 0006f0a8  <anonymous:7212f09000> (okhttp3.internal.http2.Http2Connection${'$'}ReaderRunnable.invoke)
  native: #63 pc 0037cde0  /apex/com.android.art/lib64/libart.so (art::interpreter::Execute +356) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #64 pc 0049120c  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<false>+4152) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #65 pc 00509f94  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>+780) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #66 pc 003797d8  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #67 pc 0006eacc  <anonymous:7212f09000> (okhttp3.internal.http2.Http2Connection${'$'}ReaderRunnable.invoke)
  native: #68 pc 0037cde0  /apex/com.android.art/lib64/libart.so (art::interpreter::Execute +356) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #69 pc 0049120c  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<false>+4152) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #70 pc 0050aca4  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>+4124) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #71 pc 003797d8  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #72 pc 00062510  <anonymous:7212f09000> (okhttp3.internal.concurrent.TaskQueue${'$'}execute${'$'}1.runOnce)
  native: #73 pc 0037cde0  /apex/com.android.art/lib64/libart.so (art::interpreter::Execute +356) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #74 pc 0049120c  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<false>+4152) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #75 pc 00509f94  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>+780) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #76 pc 003797d8  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #77 pc 00063868  <anonymous:7212f09000> (okhttp3.internal.concurrent.TaskRunner.runTask)
  native: #78 pc 0037cde0  /apex/com.android.art/lib64/libart.so (art::interpreter::Execute +356) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #79 pc 0049120c  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<false>+4152) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #80 pc 0050a5d4  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>+2380) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #81 pc 003797d8  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #82 pc 000634d8  <anonymous:7212f09000> (okhttp3.internal.concurrent.TaskRunner.access${'$'}runTask)
  native: #83 pc 0037cde0  /apex/com.android.art/lib64/libart.so (art::interpreter::Execute +356) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #84 pc 0049120c  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<false>+4152) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #85 pc 0050a2f8  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>+1648) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #86 pc 003797d8  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #87 pc 00062fd0  <anonymous:7212f09000> (okhttp3.internal.concurrent.TaskRunner${'$'}runnable${'$'}1.run)
  native: #88 pc 0037cde0  /apex/com.android.art/lib64/libart.so (art::interpreter::Execute +356) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #89 pc 0049120c  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<false>+4152) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #90 pc 0050aca4  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>+4124) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #91 pc 003797d8  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #92 pc 002488d8  /apex/com.android.art/javalib/core-oj.jar (java.util.concurrent.ThreadPoolExecutor.runWorker)
  native: #93 pc 0037cde0  /apex/com.android.art/lib64/libart.so (art::interpreter::Execute +356) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #94 pc 0049120c  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<false>+4152) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #95 pc 00509f94  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>+780) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #96 pc 003797d8  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #97 pc 00247774  /apex/com.android.art/javalib/core-oj.jar (java.util.concurrent.ThreadPoolExecutor${'$'}Worker.run)
  native: #98 pc 0037cde0  /apex/com.android.art/lib64/libart.so (art::interpreter::Execute +356) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #99 pc 0049120c  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<false>+4152) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #100 pc 0050aca4  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>+4124) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #101 pc 003797d8  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #102 pc 0000308c  [anon:dalvik-/apex/com.android.art/javalib/core-oj.jar-transformed] (java.lang.Thread.run)
  native: #103 pc 0037cde0  /apex/com.android.art/lib64/libart.so (art::interpreter::Execute +356) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #104 pc 0037c560  /apex/com.android.art/lib64/libart.so (artQuickToInterpreterBridge+672) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #105 pc 00377168  /apex/com.android.art/lib64/libart.so (art_quick_to_interpreter_bridge+88) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #106 pc 003605a4  /apex/com.android.art/lib64/libart.so (art_quick_invoke_stub+612) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #107 pc 0034b8a4  /apex/com.android.art/lib64/libart.so (art::ArtMethod::Invoke+144) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #108 pc 004f3e30  /apex/com.android.art/lib64/libart.so (art::Thread::CreateCallback+1888) (BuildId: b10f5696fea1b32039b162aef3850ed3)
  native: #109 pc 000cb6a8  /apex/com.android.runtime/lib64/bionic/libc.so (__pthread_start+208) (BuildId: a87908b48b368e6282bcc9f34bcfc28c)
  native: #110 pc 0006821c  /apex/com.android.runtime/lib64/bionic/libc.so (__start_thread+64) (BuildId: a87908b48b368e6282bcc9f34bcfc28c)
  at java.net.SocketInputStream.socketRead0(Native method)
  at java.net.SocketInputStream.socketRead(SocketInputStream.java:118)
  at java.net.SocketInputStream.read(SocketInputStream.java:173)
  at java.net.SocketInputStream.read(SocketInputStream.java:143)
  at com.android.org.conscrypt.ConscryptEngineSocket${'$'}SSLInputStream.readFromSocket(ConscryptEngineSocket.java:983)
  at com.android.org.conscrypt.ConscryptEngineSocket${'$'}SSLInputStream.processDataFromSocket(ConscryptEngineSocket.java:947)
  at com.android.org.conscrypt.ConscryptEngineSocket${'$'}SSLInputStream.readUntilDataAvailable(ConscryptEngineSocket.java:862)
  at com.android.org.conscrypt.ConscryptEngineSocket${'$'}SSLInputStream.read(ConscryptEngineSocket.java:835)
  at okio.InputStreamSource.read(JvmOkio.kt:94)
  at okio.AsyncTimeout${'$'}source${'$'}1.read(AsyncTimeout.kt:125)
  at okio.RealBufferedSource.request(RealBufferedSource.kt:206)
  at okio.RealBufferedSource.require(RealBufferedSource.kt:199)
  at okhttp3.internal.http2.Http2Reader.nextFrame(Http2Reader.kt:89)
  at okhttp3.internal.http2.Http2Connection${'$'}ReaderRunnable.invoke(Http2Connection.kt:618)
  at okhttp3.internal.http2.Http2Connection${'$'}ReaderRunnable.invoke(Http2Connection.kt:609)
  at okhttp3.internal.concurrent.TaskQueue${'$'}execute${'$'}1.runOnce(TaskQueue.kt:98)
  at okhttp3.internal.concurrent.TaskRunner.runTask(TaskRunner.kt:116)
  at okhttp3.internal.concurrent.TaskRunner.access${'$'}runTask(TaskRunner.kt:42)
  at okhttp3.internal.concurrent.TaskRunner${'$'}runnable${'$'}1.run(TaskRunner.kt:65)
  at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1145)
  at java.util.concurrent.ThreadPoolExecutor${'$'}Worker.run(ThreadPoolExecutor.java:644)
  at java.lang.Thread.run(Thread.java:1012)"""
    }
}
