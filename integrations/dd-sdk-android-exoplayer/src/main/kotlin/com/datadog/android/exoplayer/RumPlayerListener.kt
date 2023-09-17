/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.exoplayer

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumActionType
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player

@Suppress("DEPRECATION") // Exo Player is marked as deprecated, we should ignore it here
class RumPlayerListener(
    private val sdkCore: FeatureSdkCore
) : Player.Listener {

    private var currentMediaItem: MediaItem? = null

    // region Media Ongoing State

    private val attributes = mutableMapOf<String, Any?>()

    // region Player.Listener

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)

        if (mediaItem != null) {
            startMedia(mediaItem)
        } else {
            currentMediaItem = null
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        when (playbackState) {
            Player.STATE_ENDED -> {
                stopMedia(true)
            }

            Player.STATE_BUFFERING -> {
                TODO()
            }

            Player.STATE_IDLE -> {
                TODO()
            }

            Player.STATE_READY -> {
                TODO()
            }
        }
    }

    // endregion

    // region Internal

    private fun startMedia(mediaItem: MediaItem) {
        currentMediaItem = mediaItem
        attributes.clear()
        attributes["media.id"] = mediaItem.mediaId
        attributes["media.url"] = mediaItem.mediaId
        attributes["media.type"] = "video"
    }

    private fun stopMedia(mediaEnded: Boolean) {
        currentMediaItem?.let { mediaItem ->
            attributes["media.ended"] = mediaEnded
            GlobalRumMonitor.get(sdkCore).addAction(
                RumActionType.MEDIA,
                mediaItem.mediaId,
                attributes
            )
        }
    }

    // endregion

    // region Temporary Debug Utils

    /**
     * Called when one or more player states changed.
     * This is a catchall method, but most work
     */
    override fun onEvents(player: Player, events: Player.Events) {
        super.onEvents(player, events)
        val message = buildString {
            append("Player.Events:")
            for (i in 0 until events.size()) {
                append(events.get(i))
                append(",")
            }
            append("…")
        }
        log("onEvents($player, $message)")
    }

    private fun log(message: String) {
        sdkCore.internalLogger.log(
            InternalLogger.Level.INFO,
            InternalLogger.Target.MAINTAINER,
            { message }
        )
    }

    // endregion
}