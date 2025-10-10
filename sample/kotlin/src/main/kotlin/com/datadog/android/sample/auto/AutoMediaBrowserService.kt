/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.auto

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumActionType
import timber.log.Timber

@Suppress("UndocumentedPublicProperty", "UndocumentedPublicClass")
class AutoMediaBrowserService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()

        mediaSession = MediaSessionCompat(this, "DatadogMediaService")

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE
            )
        mediaSession.setPlaybackState(stateBuilder.build())

        mediaSession.setCallback(object : MediaSessionCompat.Callback() {

            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                super.onPlayFromMediaId(mediaId, extras)
                Timber.i("Playing song with mediaId: $mediaId")
                GlobalRumMonitor.get()
                    .addAction(RumActionType.CUSTOM, "Play song", mapOf("media_id" to mediaId))
            }

            override fun onPause() {
                super.onPause()
                Timber.i("Playing is on pause")
                GlobalRumMonitor.get()
                    .addAction(RumActionType.CUSTOM, "Pause song")
            }
        })

        mediaSession.isActive = true

        sessionToken = mediaSession.sessionToken
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?) =
        BrowserRoot(ROOT_NAME, null)

    override fun onLoadChildren(parentId: String, result: Result<List<MediaBrowserCompat.MediaItem>>) {
        if (parentId != ROOT_NAME) {
            result.sendResult(emptyList())
        }

        val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()

        @Suppress("MagicNumber")
        repeat(10) {
            val description = MediaDescriptionCompat.Builder()
                .setMediaId("sample_song_id_$it")
                .setTitle("Sample Song $it")
                .setDescription("Sample description")
                .build()

            val item = MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
            mediaItems.add(item)
        }

        result.sendResult(mediaItems)
    }

    override fun onDestroy() {
        mediaSession.release()
        super.onDestroy()
    }

    companion object {
        const val ROOT_NAME = "root"
    }
}
