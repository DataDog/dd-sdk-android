/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tv.sample

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.StyledPlayerView
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import timber.log.Timber

class PlayerActivity : AppCompatActivity() {

    lateinit var videoPlayerView: StyledPlayerView
    lateinit var videoPlayer: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        videoPlayerView = findViewById(R.id.video_player_view)
        videoPlayer = ExoPlayer.Builder(this).build()
    }

    override fun onResume() {
        super.onResume()
        val intentUri = intent.data?.toString()
        Toast.makeText(this, "Playing $intentUri", Toast.LENGTH_SHORT).show()

        videoPlayerView.player = videoPlayer

        Thread {
            try {
                val streamingService = YoutubeService(1)
                val extractor = streamingService.getStreamExtractor(intentUri)
                Timber.i("Fetching stream for $intentUri")
                extractor.fetchPage()

                val videoStreams = extractor.videoStreams
                Timber.i("Fetched ${videoStreams.size} video streams")
                videoStreams.forEach {
                    Timber.i("VS: ${it.getUrl()}")
                }
                val mediaItem = MediaItem.fromUri(videoStreams.first().getUrl())
                runOnUiThread {
                    videoPlayer.setMediaItem(mediaItem)
                    videoPlayer.playWhenReady = true
                    videoPlayer.prepare()
                }
            } catch (t: Throwable) {
                Timber.e(t)
            }
        }.start()
    }

    override fun onPause() {
        super.onPause()
        videoPlayer.release()
    }
}
