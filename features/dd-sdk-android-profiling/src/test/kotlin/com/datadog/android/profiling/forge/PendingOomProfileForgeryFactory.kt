/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.forge

import com.datadog.android.profiling.internal.trigger.PendingOomProfile
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class PendingOomProfileForgeryFactory : ForgeryFactory<PendingOomProfile> {
    override fun getForgery(forge: Forge): PendingOomProfile {
        return PendingOomProfile(
            resultFilePath = forge.anAlphabeticalString(),
            startMs = forge.aLong(),
            endMs = forge.aLong(),
            bootNtpNs = forge.aLong(),
            rumErrorId = forge.anAlphabeticalString(),
            rumContext = forge.getForgery()
        )
    }
}
