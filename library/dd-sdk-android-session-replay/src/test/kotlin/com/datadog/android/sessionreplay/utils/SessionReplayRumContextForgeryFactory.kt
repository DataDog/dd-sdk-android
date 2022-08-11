/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.UUID

internal class SessionReplayRumContextForgeryFactory : ForgeryFactory<SessionReplayRumContext> {
    override fun getForgery(forge: Forge): SessionReplayRumContext {
        return SessionReplayRumContext(
            applicationId = forge.getForgery<UUID>().toString(),
            sessionId = forge.getForgery<UUID>().toString(),
            viewId = forge.getForgery<UUID>().toString()
        )
    }
}
