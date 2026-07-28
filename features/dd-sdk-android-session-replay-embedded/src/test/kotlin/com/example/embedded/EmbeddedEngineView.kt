/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.embedded

import android.content.Context
import android.view.View

/**
 * Stand-in for a hypothetical cross-platform SDK's view class (Flutter, React Native, or any
 * other engine rendering into a native Android screen). Its only purpose is to exist at a
 * class name that isn't known at compile time, so that
 * [com.datadog.android.sessionreplay.embedded.EmbeddedViewExtensionSupport]'s reflection-based
 * lookup can be exercised in tests without depending on any real embedded engine.
 */
internal open class EmbeddedEngineView(context: Context) : View(context)
