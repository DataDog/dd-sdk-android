/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.utils.forge

import com.datadog.android.flags.internal.model.FlagsSnapshot
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.model.EvaluationContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class FlagsSnapshotForgeryFactory : ForgeryFactory<FlagsSnapshot> {
    override fun getForgery(forge: Forge): FlagsSnapshot = FlagsSnapshot(
        context = forge.getForgery(),
        flags = mapOf(forge.anAlphabeticalString() to forge.getForgery<PrecomputedFlag>()),
        requestedContext = forge.aNullable { getForgery<EvaluationContext>() },
        restoredFromDisk = forge.aBool()
    )
}
