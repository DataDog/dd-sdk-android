/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.utils.forge

import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest.Diagnostics
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest.Fetch
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest.Sdk
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.UUID

internal class PrecomputedRequestDiagnosticsForgeryFactory : ForgeryFactory<Diagnostics> {
    override fun getForgery(forge: Forge): Diagnostics = Diagnostics(
        sdk = Sdk(forge.getForgery<UUID>().toString()),
        fetch = Fetch(forge.aLong(min = 1), forge.getForgery())
    )
}
