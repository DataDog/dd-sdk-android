/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.os.Looper
import android.view.View

/**
 * Composition capture is confined to the main looper. Until an attached root reports that owner,
 * do not inspect its hierarchy or install draw/touch interception. Tracked detached roots are
 * retried on attachment; a null handler never implies main-thread ownership.
 */
internal fun isAttachedToMainLooper(view: View): Boolean {
    val ownerLooper = view.handler?.looper ?: return false
    return ownerLooper === Looper.getMainLooper()
}
