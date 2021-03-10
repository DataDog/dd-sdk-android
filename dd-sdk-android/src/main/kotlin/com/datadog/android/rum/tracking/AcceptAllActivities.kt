/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.app.Activity

/**
 * A predefined [ComponentPredicate] which accepts all [Activity]
 * to be tracked as a RUM View event.
 * This is the default behaviour for the [ActivityViewTrackingStrategy].
 */
open class AcceptAllActivities : ComponentPredicate<Activity> {

    /** @inheritdoc */
    override fun accept(component: Activity): Boolean {
        return true
    }

    /** @inheritdoc */
    override fun getViewName(component: Activity): String? {
        return null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}
