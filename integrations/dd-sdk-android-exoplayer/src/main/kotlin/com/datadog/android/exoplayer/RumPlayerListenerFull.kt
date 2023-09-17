/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.exoplayer

import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.MediaMetadata
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.metadata.Metadata
import com.google.android.exoplayer2.video.VideoSize
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION") // Exo Player is marked as deprecated, we should ignore it here
class RumPlayerListenerFull : Player.Listener {

    private var mediaContent: MediaContent? = null

    private var mediaStartNs = 0L
    private var mediaStopped = false
    private var prepareStartNS = 0L
    private val prepareTimings = mutableListOf<Pair<Long, Long>>()
    private val playingTimings = mutableListOf<Pair<Long, Long>>()
    private var playCommand: Pair<RumActionType, Long>? = null
    private var playingSince: Long = 0L
    private var bufferingSince: Long = 0L
    private var playingDuration: Long = 0L
    private var bufferingDuration: Long = 0L
    private var errorCount: Int = 0

    private var surfaceWidth = 1
    private var surfaceHeight = 1
    private var videoWidth = 1
    private var videoHeight = 1

    // region Player.Listener

    override fun onEvents(player: Player, events: Player.Events) {
        super.onEvents(player, events)
        log("onEvents ${events.all()}")
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)
        mediaContent?.let {
//            GlobalRumMonitor.get().stopMedia(it, emptyMap())
        }

        if (mediaItem == null) {
            mediaContent = null
        } else {
            log("onMediaItemTransition")
            val uri = mediaItem.requestMetadata.mediaUri ?: mediaItem.localConfiguration?.uri
            val id = mediaItem.mediaId
            val mediaContent = MediaContent(id, "video", uri?.toString().orEmpty())
            startMedia(mediaContent)
        }
    }


    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        super.onPlayWhenReadyChanged(playWhenReady, reason)
        if (playWhenReady) {
            playCommand = RumActionType.PLAY to System.nanoTime()
        }
    }

    override fun onIsLoadingChanged(isLoading: Boolean) {
        super.onIsLoadingChanged(isLoading)
        log("onIsLoadingChanged($isLoading)")

        val relativeNanos = System.nanoTime() - mediaStartNs
        if (isLoading) {
            prepareStartNS = relativeNanos
        } else {
            prepareTimings.add(prepareStartNS to relativeNanos)
            mediaContent?.let {
//                GlobalRumMonitor.get().updateMedia(it, mapOf("media.preparing" to prepareTimings))
            }
        }
    }

    override fun onRenderedFirstFrame() {
        super.onRenderedFirstFrame()
        mediaContent?.let {
//            GlobalRumMonitor.get().updateMedia(it, mapOf("media.first_frame_rendered" to System.nanoTime() - mediaStartNs))
        }
    }

    override fun onSurfaceSizeChanged(width: Int, height: Int) {
        super.onSurfaceSizeChanged(width, height)
        log("onSurfaceSizeChanged($width, $height)")
        surfaceWidth = width
        surfaceHeight = height
        mediaContent?.let {
//            GlobalRumMonitor.get().updateMedia(it, mapOf(
//                "media.surface_size.width" to width,
//                "media.surface_size.height" to height,
//                "media.scaling_ratio" to getScalingRatio()
//            ))
        }
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        super.onVideoSizeChanged(videoSize)
        log("onVideoSizeChanged(${videoSize.width}, ${videoSize.height})")
        videoWidth = videoSize.width
        videoHeight = videoSize.width
        mediaContent?.let {
//            GlobalRumMonitor.get().updateMedia(it, mapOf(
//                "media.video_size.width" to videoSize.width,
//                "media.video_size.height" to videoSize.height,
//                "media.scaling_ratio" to getScalingRatio()
//            ))
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        log("onPlaybackStateChanged($playbackState)")
        when (playbackState) {
            Player.STATE_IDLE -> {
                log("PLAYER IS IDLE")
                stopMedia(false)
                mediaStopped = true
            }

            Player.STATE_BUFFERING -> {
                log("BUFFERING")
                if (mediaStopped) {
                    mediaContent?.let { startMedia(it) }
                    playCommand = RumActionType.PLAY to System.nanoTime()
                    bufferingSince = System.nanoTime()
                }
            }

            Player.STATE_ENDED -> {
                log("PLAYER ENDED")
                stopMedia(true)
                mediaStopped = true
            }

            Player.STATE_READY -> log("PLAYER IS READY")
        }
    }

    override fun onMetadata(metadata: Metadata) {
        super.onMetadata(metadata)
        log("onIsPlayingChanged($metadata)")
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        super.onMediaMetadataChanged(mediaMetadata)
        log("onMediaMetadataChanged($mediaMetadata)")
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)
        val nanoTime = System.nanoTime()
        log("onIsPlayingChanged($isPlaying)")
        if (isPlaying) {
            playingSince = nanoTime
            if (mediaStartNs != 0L) {
                mediaContent?.let {
//                    GlobalRumMonitor.get()
//                        .updateMedia(it, mapOf("media.startup_time" to nanoTime - mediaStartNs))
                }
                mediaStartNs = 0L
            }

            playCommand?.let {
                val customAttributes = mapOf(
                    "_dd.timestamp" to System.currentTimeMillis() - TimeUnit.NANOSECONDS.toMillis(it.second),
                    "_dd.loading_time" to nanoTime - it.second,
                    "media.id" to mediaContent?.id,
                    "media.url" to mediaContent?.url,
                    "media.type" to mediaContent?.type,
                )
                GlobalRumMonitor.get()
                    .addAction(
                        it.first,
                        mediaContent?.id.orEmpty(),
                        customAttributes
                    )
            }
            playCommand = null
        } else if (playingSince != 0L) {
            playingTimings.add(playingSince to nanoTime)
            mediaContent?.let {
                playingDuration += nanoTime - playingSince
//                GlobalRumMonitor.get()
//                    .updateMedia(
//                        it,
//                        mapOf(
//                            "media.playing_time" to playingDuration,
//                            "media.playing" to prepareTimings
//                        )
//                    )
                playingSince = 0L
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        log("onPlayerError(${error.message})")
        errorCount++
        mediaContent?.let {
//            GlobalRumMonitor.get()
//                .updateMedia(it, mapOf("media.error.count" to errorCount))
        }
        GlobalRumMonitor.get()
            .addError(
                "ExoPlayer error",
                RumErrorSource.SOURCE,
                error,
                attributes = mapOf(
                    "media.id" to mediaContent?.id,
                    "media.url" to mediaContent?.url,
                    "media.type" to mediaContent?.type
                )
            )
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        super.onPositionDiscontinuity(oldPosition, newPosition, reason)
        playCommand = RumActionType.SEEK to System.nanoTime()
    }

    // endregion

    private fun stopMedia(mediaEnded: Boolean) {
        mediaContent?.let {
//            GlobalRumMonitor.get().stopMedia(
//                it,
//                mapOf(
//                    "media.ended" to mediaEnded,
//                    "media.video_size.width" to videoWidth,
//                    "media.video_size.height" to videoHeight,
//                    "media.surface_size.width" to surfaceWidth,
//                    "media.surface_size.height" to surfaceHeight,
//                    "media.scaling_ratio" to getScalingRatio()
//                )
//            )
        }
    }

    private fun getScalingRatio() = ((videoWidth.toFloat() / surfaceWidth) + (videoHeight.toFloat() / surfaceHeight)) / 2f

    private fun Player.Events.all(): String {
        val events = this
        return buildString {
            append("Player.Events:")
            for (i in 0 until events.size()) {
                append(events.get(i))
                append(",")
            }
            append("…")
        }
    }


    private fun startMedia(mediaContent: MediaContent) {
        log("Using media $mediaContent")
//        GlobalRumMonitor.get()
//            .startMedia(
//                mediaContent,
//                mapOf(
//                    "media.ended" to false,
//                    "media.playing_time" to 0L
//                )
//            )
        this.mediaContent = mediaContent
        mediaStartNs = System.nanoTime()
        prepareTimings.clear()
        playingTimings.clear()
        playingDuration = 0L
        playingSince = 0L
        bufferingDuration = 0L
        bufferingSince = 0L
        errorCount = 0
        mediaStopped = false
        playCommand = null
    }

    private fun log(message: String) {
        (Datadog.getInstance() as? FeatureSdkCore)
            ?.internalLogger
            ?.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.MAINTAINER,
                { message }
            )
    }

}