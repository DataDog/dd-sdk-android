/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ActionBarContainer
import androidx.appcompat.widget.Toolbar
import com.datadog.android.Datadog
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.utils.getTrackingConsent
import com.datadog.android.sessionreplay.SessionReplayFeature
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.model.MobileSegment

internal class SessionReplayPlaygroundActivity : AppCompatActivity() {
    lateinit var titleTextView: TextView
    lateinit var clickMeButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val credentials = RuntimeConfig.credentials()
        // we will use a large long task threshold to make sure we will not have LongTask events
        // noise in our integration tests.
        val config = RuntimeConfig.configBuilder()
            .trackInteractions()
            .trackLongTasks(RuntimeConfig.LONG_TASK_LARGE_THRESHOLD)
            .useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
            .build()
        val trackingConsent = intent.getTrackingConsent()
        Datadog.initialize(this, credentials, config, trackingConsent)
        val sessionReplayConfig = RuntimeConfig.sessionReplayConfigBuilder()
            .setPrivacy(SessionReplayPrivacy.ALLOW_ALL)
            .build()
        val sessionReplayFeature = SessionReplayFeature(sessionReplayConfig)
        Datadog.registerFeature(sessionReplayFeature)
        Datadog.setVerbosity(Log.VERBOSE)
        GlobalRum.registerIfAbsent(RumMonitor.Builder().build())
        setContentView(R.layout.session_replay_layout)
        titleTextView = findViewById(R.id.title)
        clickMeButton = findViewById(R.id.button)
    }

    @Suppress("LongMethod")
    fun getExpectedSrData(): ExpectedSrData {
        val density = resources.displayMetrics.density
        val decorView = window.decorView
        val decorWidth = decorView.width.toLong()
            .densityNormalized(density)
        val decorHeight = decorView.height.toLong()
            .densityNormalized(density)

        val metaRecord = MobileSegment.MobileRecord.MetaRecord(
            System.currentTimeMillis(),
            MobileSegment.Data1(
                decorWidth,
                decorHeight
            )
        )
        val focusRecord = MobileSegment.MobileRecord.FocusRecord(
            System.currentTimeMillis(),
            MobileSegment.Data2(true)
        )
        val viewPortResizeData = MobileSegment.MobileIncrementalData.ViewportResizeData(
            decorWidth,
            decorHeight
        )
        val viewportRecord = MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord(
            System.currentTimeMillis(),
            data = viewPortResizeData
        )
        val fullSnapshotRecordWireframes = mutableListOf<MobileSegment.Wireframe>()
        val decorViewShapeStyle = ThemeUtils.resolveThemeColor(theme)?.let {
            MobileSegment.ShapeStyle(
                backgroundColor = StringUtils.formatColorAndAlphaAsHexa(it, FULL_OPACITY_AS_HEXA),
                opacity = 1f
            )
        }
        val rootViewCoordinates = decorView.getViewAbsoluteCoordinates()
        val rootWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = decorView.resolveId(),
            x = rootViewCoordinates[0].toLong().densityNormalized(density),
            y = rootViewCoordinates[1].toLong().densityNormalized(density),
            width = decorWidth,
            height = decorHeight,
            shapeStyle = decorViewShapeStyle
        )
        val titleViewScreenCoordinates = titleTextView.getViewAbsoluteCoordinates()
        val titleWireframe = MobileSegment.Wireframe.TextWireframe(
            id = titleTextView.resolveId(),
            x = titleViewScreenCoordinates[0].toLong().densityNormalized(density),
            y = titleViewScreenCoordinates[1].toLong().densityNormalized(density),
            width = titleTextView.width.toLong().densityNormalized(density),
            height = titleTextView.height.toLong().densityNormalized(density),
            text = "${titleTextView.text}",
            textStyle = MobileSegment.TextStyle(
                family = "sans-serif",
                size = titleTextView.textSize.toLong().densityNormalized(density),
                color = StringUtils.formatColorAndAlphaAsHexa(
                    titleTextView.currentTextColor,
                    FULL_OPACITY_AS_HEXA
                )
            ),
            textPosition = MobileSegment.TextPosition(
                padding = MobileSegment.Padding(0, 0, 0, 0),
                alignment = MobileSegment.Alignment(
                    horizontal = MobileSegment.Horizontal.LEFT,
                    vertical = MobileSegment.Vertical.TOP
                )
            )
        )
        val buttonScreenCoordinates = clickMeButton.getViewAbsoluteCoordinates()
        val buttonWireframe = MobileSegment.Wireframe.TextWireframe(
            id = clickMeButton.resolveId(),
            x = buttonScreenCoordinates[0].toLong().densityNormalized(density),
            y = buttonScreenCoordinates[1].toLong().densityNormalized(density),
            width = clickMeButton.width.toLong().densityNormalized(density),
            height = clickMeButton.height.toLong().densityNormalized(density),
            text = "${clickMeButton.text}",
            border = MobileSegment.ShapeBorder(
                width = 1,
                color = StringUtils.formatColorAndAlphaAsHexa(
                    BLACK_COLOR_AS_HEXA,
                    FULL_OPACITY_AS_HEXA
                )
            ),
            textStyle = MobileSegment.TextStyle(
                family = "sans-serif",
                size = clickMeButton.textSize.toLong().densityNormalized(density),
                color = StringUtils.formatColorAndAlphaAsHexa(
                    clickMeButton.currentTextColor,
                    FULL_OPACITY_AS_HEXA
                )
            ),
            textPosition = MobileSegment.TextPosition(
                padding = MobileSegment.Padding(
                    top = clickMeButton.totalPaddingTop.toLong()
                        .densityNormalized(density),
                    bottom = clickMeButton.totalPaddingBottom.toLong()
                        .densityNormalized(density),
                    left = clickMeButton.totalPaddingLeft.toLong()
                        .densityNormalized(density),
                    right = clickMeButton.totalPaddingRight.toLong()
                        .densityNormalized(density)
                ),
                alignment = MobileSegment.Alignment(
                    horizontal = MobileSegment.Horizontal.CENTER,
                    vertical = MobileSegment.Vertical.CENTER
                )
            )
        )
        fullSnapshotRecordWireframes.add(rootWireframe)
        fullSnapshotRecordWireframes.add(titleWireframe)
        fullSnapshotRecordWireframes.add(buttonWireframe)
        // one shape wireframe for action bar container and one for toolbar
        // probably these will be changed later as we decide how to handle the action bars
        decorView.findViewByType(ActionBarContainer::class.java)?.let {
            val actionBarContainerScreenCoordinates = it.getViewAbsoluteCoordinates()
            val actionBarContainerWireframe = MobileSegment.Wireframe.ShapeWireframe(
                id = it.resolveId(),
                width = it.width.toLong().densityNormalized(density),
                height = it.height.toLong().densityNormalized(density),
                x = actionBarContainerScreenCoordinates[0].toLong().densityNormalized(density),
                y = actionBarContainerScreenCoordinates[1].toLong().densityNormalized(density),
                border = MobileSegment.ShapeBorder(
                    color = StringUtils.formatColorAndAlphaAsHexa(
                        BLACK_COLOR_AS_HEXA,
                        FULL_OPACITY_AS_HEXA
                    ),
                    width = 1
                )
            )
            fullSnapshotRecordWireframes.add(actionBarContainerWireframe)
            (it.getChildAt(0) as? Toolbar)?.let { toolbar ->
                val toolbarScreenCoordinates = toolbar.getViewAbsoluteCoordinates()
                val actionBarToolbarWireframe = MobileSegment.Wireframe.ShapeWireframe(
                    id = toolbar.resolveId(),
                    width = toolbar.width.toLong().densityNormalized(density),
                    height = toolbar.height.toLong().densityNormalized(density),
                    x = toolbarScreenCoordinates[0].toLong().densityNormalized(density),
                    y = toolbarScreenCoordinates[1].toLong().densityNormalized(density),
                    border = MobileSegment.ShapeBorder(
                        color = StringUtils.formatColorAndAlphaAsHexa(
                            BLACK_COLOR_AS_HEXA,
                            FULL_OPACITY_AS_HEXA
                        ),
                        width = 1
                    )
                )
                fullSnapshotRecordWireframes.add(actionBarToolbarWireframe)
            }
        }

        val fullSnapshotRecord = MobileSegment.MobileRecord.MobileFullSnapshotRecord(
            timestamp = System.currentTimeMillis(),
            data = MobileSegment.Data(wireframes = fullSnapshotRecordWireframes)
        )

        // TODO - RUMM[2991] sessionId, applicationId, viewId will be assessed in a future iteration
        return ExpectedSrData(
            "",
            "",
            "",
            listOf(
                metaRecord,
                focusRecord,
                viewportRecord,
                fullSnapshotRecord
            ).map { it.toJson() }
        )
    }

    companion object {
        private const val BLACK_COLOR_AS_HEXA = 0
        private const val FULL_OPACITY_AS_HEXA = 255
    }
}
