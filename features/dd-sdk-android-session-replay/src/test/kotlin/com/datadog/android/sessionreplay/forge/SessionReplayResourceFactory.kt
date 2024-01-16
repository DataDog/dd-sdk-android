/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.forge

import com.datadog.android.sessionreplay.internal.recorder.SessionReplayResource
import com.datadog.android.sessionreplay.internal.recorder.SessionReplayResourceContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class SessionReplayResourceFactory : ForgeryFactory<SessionReplayResource> {
    override fun getForgery(forge: Forge): SessionReplayResource {
        val sessionReplayResourceContext = forge.getForgery<SessionReplayResourceContext>()

        return SessionReplayResource(
            identifier = forge.anAsciiString(),
            data = forge.anAsciiString().toByteArray(),
            context = sessionReplayResourceContext
        )
    }
}
