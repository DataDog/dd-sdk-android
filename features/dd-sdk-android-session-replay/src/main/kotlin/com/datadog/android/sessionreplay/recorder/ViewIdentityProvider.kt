/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

import android.view.View

/**
 * Provides stable identity hashes for Views, enabling correlation between
 * Session Replay wireframes and RUM action events (heatmaps).
 */
interface ViewIdentityProvider {

    /**
     * Resolves a stable identity hash for the given view.
     *
     * @param view the view to resolve identity for
     * @return the stable identity hash, or null if the view's identity cannot be determined
     *         (e.g., detached view, or identity tracking is disabled)
     */
    fun resolveIdentity(view: View): String?
}
