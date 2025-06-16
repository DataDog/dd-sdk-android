/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tv.sample

import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumActionType
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player

internal class RumPlayerListener : Player.Listener {

    private var currentMediaItem: MediaItem? = null
    private val attributes = mutableMapOf<String, Any?>()

    // region Player.Listener

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)

        stopMedia(false)
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
//                TODO()
            }

            Player.STATE_IDLE -> {
//                TODO()
            }

            Player.STATE_READY -> {
//                TODO()
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)
        currentMediaItem?.let { mediaItem ->
            val name = if (isPlaying) "play" else "pause"

            GlobalRumMonitor.get().addAction(
                type = RumActionType.CUSTOM,
                name = "media-$name",
                attributes = attributes
            )
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        super.onPositionDiscontinuity(oldPosition, newPosition, reason)
        currentMediaItem?.let { mediaItem ->
            GlobalRumMonitor.get().addAction(
                type = RumActionType.CUSTOM,
                name = "media-seek",
                attributes = attributes
            )
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
            GlobalRumMonitor.get().addAction(
                type = RumActionType.CUSTOM,
                name = "media:ended",
                attributes = attributes
            )
        }
    }

    // endregion
}
