/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.app.Activity

/**
 * A predefined [WhitelistPredicate] which whitelists all the Activities to be tracked as RUM View
 * events. This is the default behaviour of the [ActivityViewTrackingStrategy].
 */
class ActivityWhitelistAcceptAll : WhitelistPredicate<Activity> {

    override fun accept(view: Activity): Boolean {
        return true
    }
}
