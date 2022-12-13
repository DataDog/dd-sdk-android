/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.data.db.realm

import android.content.Context
import io.realm.Realm

internal object RealmFeature {

    private var wasInitialised = false

    fun initialise(context: Context) {
        if (!wasInitialised) {
            synchronized(this) {
                if (!wasInitialised) {
                    Realm.init(context)
                    wasInitialised = true
                }
            }
        }
    }
}
