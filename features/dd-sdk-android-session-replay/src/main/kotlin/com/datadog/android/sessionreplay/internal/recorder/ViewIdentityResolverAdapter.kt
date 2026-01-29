/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import com.datadog.android.internal.identity.ViewIdentityResolver
import com.datadog.android.sessionreplay.recorder.ViewIdentityProvider

/**
 * Adapter that wraps [ViewIdentityResolver] from the internal module
 * to expose it as session-replay's public [ViewIdentityProvider] interface.
 */
internal class ViewIdentityResolverAdapter(
    private val resolver: ViewIdentityResolver
) : ViewIdentityProvider {

    override fun resolveIdentity(view: View): String? {
        return resolver.resolveViewIdentity(view)
    }
}
