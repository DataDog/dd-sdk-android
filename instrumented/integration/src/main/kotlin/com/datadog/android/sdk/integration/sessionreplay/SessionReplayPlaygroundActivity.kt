/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.datadog.android.Datadog
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.utils.getTrackingConsent
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.model.MobileSegment
internal class SessionReplayPlaygroundActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val credentials = RuntimeConfig.credentials()
        // we will use a large long task threshold to make sure we will not have LongTask events
        // noise in our integration tests.
        val config = RuntimeConfig.configBuilder()
            .trackInteractions()
            .trackLongTasks(RuntimeConfig.LONG_TASK_LARGE_THRESHOLD)
            .setSessionReplayPrivacy(SessionReplayPrivacy.ALLOW_ALL)
            .useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
            .build()
        val trackingConsent = intent.getTrackingConsent()

        Datadog.initialize(this, credentials, config, trackingConsent)
        Datadog.setVerbosity(Log.VERBOSE)
        GlobalRum.registerIfAbsent(RumMonitor.Builder().build())
        setContentView(R.layout.session_replay_layout)
    }

    /**
     *
     * {"type":10,"data":{"wireframes":[{"id":158046225,"x":0,"y":0,"width":411,"height":914,"shapeStyle":{"backgroundColor":"#303030ff","opacity":1.0},"type":"shape"},{"id":155917686,"x":0,"y":0,"width":411,"height":890,"type":"shape"},{"id":121220983,"x":0,"y":24,"width":411,"height":866,"type":"shape"},{"id":68167652,"x":0,"y":24,"width":411,"height":866,"type":"shape"},{"id":90219597,"x":0,"y":80,"width":411,"height":810,"type":"shape"},{"id":214145794,"x":0,"y":80,"width":411,"height":810,"type":"shape"},{"id":235916307,"x":0,"y":80,"width":172,"height":19,"type":"text","text":"Welcome to Session Replay","textStyle":{"family":"sans-serif","size":14,"color":"#00ddffff"},"textPosition":{"padding":{"top":0,"bottom":0,"left":0,"right":0},"alignment":{"horizontal":"left","vertical":"top"}}},{"id":110747216,"x":0,"y":109,"width":88,"height":48,"border":{"color":"#000000ff","width":1},"type":"text","text":"Click Me","textStyle":{"family":"sans-serif","size":14,"color":"#ffffffff"},"textPosition":{"padding":{"top":14,"bottom":14,"left":11,"right":11},"alignment":{"horizontal":"center","vertical":"center"}}},{"id":52443209,"x":0,"y":24,"width":411,"height":56,"border":{"color":"#000000ff","width":1},"type":"shape"},{"id":122971470,"x":0,"y":24,"width":411,"height":56,"border":{"color":"#000000ff","width":1},"type":"shape"}]}}
     */

    fun getExpectedSrData(): ExpectedSrData {

        val timestamp = System.currentTimeMillis()
        val decorWidth = window.decorView.width.toLong()
                .densityNormalized(resources.displayMetrics.density)
        val decorHeight = window.decorView.height.toLong()
                .densityNormalized(resources.displayMetrics.density)
        val metaRecord = MobileSegment.MobileRecord.MetaRecord(
            timestamp,
            MobileSegment.Data1(
                    decorWidth,
                    decorHeight
            )
        )
        val focusRecord = MobileSegment.MobileRecord.FocusRecord(
            timestamp,
            MobileSegment.Data2(true)
        )
        val viewPortResizeData = MobileSegment.MobileIncrementalData.ViewportResizeData(
                decorWidth,
                decorHeight
        )
        val viewportRecord = MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord(
            timestamp,
            data = viewPortResizeData
        )
//        val rootWireframe = MobileSegment.Wireframe.ShapeWireframe(
//                id= window.decorView.resolveId(),
//                x=0,
//                y=0,
//                width=decorWidth,
//                height=decorHeight,
//                shapeStyle = MobileSegment.ShapeStyle(backgroundColor = "#3033040ff", opacity = 1f))
        return ExpectedSrData(
            "",
            "",
            "",
            listOf(
                metaRecord,
                focusRecord,
                viewportRecord
            ).map { it.toJson() }
        )
    }
}
