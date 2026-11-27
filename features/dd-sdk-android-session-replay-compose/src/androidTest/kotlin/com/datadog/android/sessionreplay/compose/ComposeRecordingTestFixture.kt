/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.RootSemanticsNodeMapper
import com.datadog.android.sessionreplay.fixtures.SessionReplayRecordingTestFixture
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter

/** Runs production Compose mappers and retains the encoded image resources. */
internal class ComposeRecordingTestFixture(context: Context) {
    private val recordingFixture = SessionReplayRecordingTestFixture(context)
    private val rootMapper = RootSemanticsNodeMapper(DefaultColorStringFormatter)
    private val viewMappers = ComposeExtensionSupport().getCustomViewMappers()

    val resources: Map<String, ByteArray> get() = recordingFixture.resources
    val pendingJobs: Int get() = recordingFixture.pendingJobs

    fun map(view: ComposeView): List<MobileSegment.Wireframe.ImageWireframe> =
        viewMappers.first { it.supportsView(view) }.getUnsafeMapper().map(
            view,
            recordingFixture.mappingContext,
            recordingFixture.callback,
            InternalLogger.UNBOUND
        ).filterIsInstance<MobileSegment.Wireframe.ImageWireframe>()

    fun map(root: SemanticsNode): List<MobileSegment.Wireframe.ImageWireframe> =
        rootMapper.createComposeWireframes(
            root,
            root.layoutInfo.density.density,
            recordingFixture.mappingContext,
            recordingFixture.callback,
            InternalLogger.UNBOUND
        ).filterIsInstance<MobileSegment.Wireframe.ImageWireframe>()

    fun decode(wireframe: MobileSegment.Wireframe.ImageWireframe): Bitmap = recordingFixture.decode(wireframe)
}
