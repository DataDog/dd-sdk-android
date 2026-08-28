/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tv.sample

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumErrorSource
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.StreamExtractor
import timber.log.Timber
import kotlin.random.Random

/**
 * An activity playing a video stream from a YouTube URL.
 *
 * This activity looks for the URL in the `Intent`'s `data` property.
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var videoPlayerView: PlayerView
    private lateinit var videoPlayer: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val okHttpClient = (applicationContext as TvSampleApplication).okHttpClient
        videoPlayerView = findViewById(R.id.video_player_view)
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(OkHttpDataSource.Factory(okHttpClient))
        videoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        videoPlayer.addListener(RumPlayerListener())
    }

    override fun onResume() {
        super.onResume()
        val intentUri = intent.data?.toString()
        Toast.makeText(this, "Playing $intentUri", Toast.LENGTH_SHORT).show()

        if (intentUri == null) {
            finish()
            return
        }

        videoPlayerView.player = videoPlayer

        Thread {
            loadAndPlayVideo(intentUri)
        }.start()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun loadAndPlayVideo(intentUri: String) {
        try {
            val extractor = extractStreamingInformation(intentUri)
            val videoStreams = extractor.videoStreams
            val streamIdx = Random.nextInt(videoStreams.size)
            val videoStream = videoStreams[streamIdx]
            val mediaItem = MediaItem.Builder()
                .setUri(videoStream.content)
                .setMediaId(intentUri)
                .build()
            runOnUiThread {
                videoPlayer.setMediaItem(mediaItem)
                videoPlayer.playWhenReady = true
                videoPlayer.prepare()
            }
        } catch (t: Throwable) {
            GlobalRumMonitor.get().addError(
                "Unable to stream video",
                RumErrorSource.SOURCE,
                t
            )
        }
    }

    private fun extractStreamingInformation(intentUri: String?): StreamExtractor {
        val streamingService = YoutubeService(1)
        val extractor = streamingService.getStreamExtractor(intentUri)
        Timber.i("Fetching stream for $intentUri")
        extractor.fetchPage()
        return extractor
    }

    override fun onPause() {
        super.onPause()
        videoPlayer.stop()
        videoPlayer.release()
    }
}
