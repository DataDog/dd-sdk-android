/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric.interactiontonextview

import com.datadog.android.rum.RumActionType

internal class ActionTypeInteractionValidator : InteractionIngestionValidator {
    override fun validate(
        context: InternalInteractionContext
    ): Boolean {
        return context.actionType in ALLOWED_TYPES
    }

    companion object {
        private val ALLOWED_TYPES = setOf(
            RumActionType.TAP,
            RumActionType.SWIPE,
            RumActionType.CLICK,
            RumActionType.BACK
        )
    }
}
