/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.glide

import com.bumptech.glide.load.engine.executor.GlideExecutor
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor

/**
 * A [GlideExecutor.UncaughtThrowableStrategy] implementation that will forward all errors
 * to the active [RumMonitor].
 *
 * @param name the name of the feature this strategy will be used for
 * (e.g.: "Disk Cache", "Source", …)
 */
class DatadogRUMUncaughtThrowableStrategy(
    val name: String
) : GlideExecutor.UncaughtThrowableStrategy {

    // region GlideExecutor.UncaughtThrowableStrategy

    /** @inheritdoc */
    override fun handle(t: Throwable?) {
        if (t != null) {
            GlobalRum.get()
                .addError("Glide $name error", RumErrorSource.SOURCE, t, emptyMap())
        }
    }

    // endregion
}
