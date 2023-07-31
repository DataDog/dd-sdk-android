/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

internal class NoOpInteractionPredicate : InteractionPredicate {
    override fun getTargetName(target: Any): String? = null

    override fun equals(other: Any?): Boolean {
        return other is NoOpInteractionPredicate
    }

    override fun hashCode(): Int {
        return 0
    }
}
