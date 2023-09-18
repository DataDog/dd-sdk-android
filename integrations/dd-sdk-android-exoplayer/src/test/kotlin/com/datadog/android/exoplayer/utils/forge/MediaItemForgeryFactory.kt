/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.exoplayer.utils.forge

import com.google.android.exoplayer2.MediaItem
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.net.URI

class MediaItemForgeryFactory : ForgeryFactory<MediaItem> {
    override fun getForgery(forge: Forge): MediaItem {
        return MediaItem.Builder()
            .setMediaId(forge.anHexadecimalString())
            .setUri(forge.getForgery<URI>().toString())
            .build()
    }
}