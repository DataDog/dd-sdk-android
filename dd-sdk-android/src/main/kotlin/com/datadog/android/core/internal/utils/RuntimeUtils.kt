/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.core.SdkInternalLogger
import java.util.Locale

// TODO RUMM-2948 Temporary thing, we probably shouldn't expose it in such way to modules,
//  at least as var
@Suppress("UndocumentedPublicProperty")
var internalLogger: InternalLogger = SdkInternalLogger.UNBOUND

/**
 * Warns the user that they're using a deprecated feature.
 * @param target the target feature (e.g. method name)
 * @param deprecatedSince the version when the feature was deprecated
 * @param removedInVersion the version in which the feature will disappear
 * @param alternative an alternative option to get the same effect
 */
internal fun warnDeprecated(
    target: String,
    deprecatedSince: String,
    removedInVersion: String,
    alternative: String? = null
) {
    if (alternative == null) {
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            WARN_DEPRECATED.format(
                Locale.US,
                target,
                deprecatedSince,
                removedInVersion
            )
        )
    } else {
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            WARN_DEPRECATED_WITH_ALT.format(
                Locale.US,
                target,
                deprecatedSince,
                removedInVersion,
                alternative
            )
        )
    }
}

internal const val WARN_DEPRECATED = "%s has been deprecated since version %s, " +
    "and will be removed in version %s."

internal const val WARN_DEPRECATED_WITH_ALT = "%s has been deprecated since version %s, " +
    "and will be removed in version %s. Please use %s instead"
