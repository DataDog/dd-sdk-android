/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.utils.forge

import com.datadog.android.flags.internal.model.PrecomputedAssignments
import com.datadog.android.flags.internal.model.PrecomputedFlag
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class PrecomputedAssignmentsForgeryFactory : ForgeryFactory<PrecomputedAssignments> {
    override fun getForgery(forge: Forge): PrecomputedAssignments = PrecomputedAssignments(
        flags = forge.aMap { anAlphabeticalString() to getForgery<PrecomputedFlag>() }
    )
}
