/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.embedded

import android.content.Context
import android.view.View

/**
 * A second, distinct stand-in view class, used alongside [EmbeddedEngineView] to prove that
 * [com.datadog.android.sessionreplay.embedded.EmbeddedViewExtensionSupport] can detect several
 * different embedded engines' view classes at once.
 */
internal open class AnotherEmbeddedEngineView(context: Context) : View(context)
