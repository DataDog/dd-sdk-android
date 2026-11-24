/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package androidx.recyclerview.widget

import android.content.Context
import android.view.ViewGroup

/**
 * Stands in for the real class, which this module does not depend on, so that tests can subclass a
 * view the way an app does and have it recognized by name.
 */
internal open class RecyclerView(context: Context) : ViewGroup(context) {
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) = Unit
}
