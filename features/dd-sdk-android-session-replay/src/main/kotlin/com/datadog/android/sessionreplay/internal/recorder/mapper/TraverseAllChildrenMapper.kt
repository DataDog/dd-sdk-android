/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View
import com.datadog.android.sessionreplay.model.MobileSegment

/**
 * Maps a View to a [List] of [MobileSegment.Wireframe].
 * This is mainly used internally by the SDK but if you want to provide a different
 * Session Replay representation for a specific View type you can implement this on your end.
 * Note that mappers using this interface also traverse all the children of the view
 * instead of just the immediate one. This means that you will need to have mappers
 * for all child views of the view the mapper is traversing.
 */
interface TraverseAllChildrenMapper<in T : View, out S : MobileSegment.Wireframe> :
    WireframeMapper<T, S>
