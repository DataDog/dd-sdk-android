/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.utils.forge

import com.datadog.android.flags.internal.diagnostics.FlagsRequestDiagnostics
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.UUID

internal class FlagsRequestDiagnosticsForgeryFactory : ForgeryFactory<FlagsRequestDiagnostics> {
    override fun getForgery(forge: Forge): FlagsRequestDiagnostics = FlagsRequestDiagnostics(
        instanceId = forge.getForgery<UUID>().toString(),
        sequence = forge.aLong(min = 1),
        trigger = forge.aValueFrom(FlagsRequestDiagnostics.Trigger::class.java)
    )
}
