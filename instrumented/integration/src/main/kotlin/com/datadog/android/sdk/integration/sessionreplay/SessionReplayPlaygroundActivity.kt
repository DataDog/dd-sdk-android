/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import android.app.Activity
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ActionBarContainer
import androidx.appcompat.widget.Toolbar
import com.datadog.android.Datadog
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.utils.getForgeSeed
import com.datadog.android.sdk.utils.getTrackingConsent
import com.datadog.android.sessionreplay.SessionReplay
import com.datadog.android.sessionreplay.SessionReplayConfiguration
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.model.MobileSegment
import java.util.Random

internal open class SessionReplayPlaygroundActivity : AppCompatActivity() {
    lateinit var titleTextView: TextView
    lateinit var clickMeButton: Button

    @Suppress("CheckInternal")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val credentials = RuntimeConfig.credentials()
        val config = RuntimeConfig.configBuilder().build()
        val trackingConsent = intent.getTrackingConsent()
        Datadog.setVerbosity(Log.VERBOSE)
        val sdkCore = Datadog.initialize(this, credentials, config, trackingConsent)
        checkNotNull(sdkCore)
        val featureActivations = mutableListOf(
            // we will use a large long task threshold to make sure we will not have LongTask events
            // noise in our integration tests.
            {
                val rumConfig = RuntimeConfig.rumConfigBuilder()
                    .trackUserInteractions()
                    .trackLongTasks(RuntimeConfig.LONG_TASK_LARGE_THRESHOLD)
                    .useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
                    .build()
                Rum.enable(rumConfig, sdkCore)
            },
            {
                val sessionReplayConfig = sessionReplayConfiguration()
                SessionReplay.enable(sessionReplayConfig, sdkCore)
            }
        )
        featureActivations.shuffled(Random(intent.getForgeSeed()))
            .forEach { it() }
        GlobalRumMonitor.registerIfAbsent(RumMonitor.Builder(sdkCore).build(), sdkCore)
        setContentView(R.layout.session_replay_layout)
        titleTextView = findViewById(R.id.title)
        clickMeButton = findViewById(R.id.button)
    }

    open fun sessionReplayConfiguration(): SessionReplayConfiguration =
        RuntimeConfig.sessionReplayConfigBuilder()
            .setPrivacy(SessionReplayPrivacy.ALLOW_ALL)
            .setSessionReplaySampleRate(SAMPLE_IN_ALL_SESSIONS)
            .build()

    @Suppress("LongMethod")
    open fun getExpectedSrData(): ExpectedSrData {
        val density = resources.displayMetrics.density
        val decorView = window.decorView

        val decorWidth = decorView.width.toLong()
            .densityNormalized(density)
        val decorHeight = decorView.height.toLong()
            .densityNormalized(density)
        val screenDimensions = resolveScreenDimensions(this)

        val metaRecord = MobileSegment.MobileRecord.MetaRecord(
            System.currentTimeMillis(),
            MobileSegment.Data1(
                screenDimensions.first,
                screenDimensions.second
            )
        )
        val focusRecord = MobileSegment.MobileRecord.FocusRecord(
            System.currentTimeMillis(),
            MobileSegment.Data2(true)
        )
        val viewPortResizeData = MobileSegment.MobileIncrementalData.ViewportResizeData(
            screenDimensions.first,
            screenDimensions.second
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
                family = SANS_SERIF_FAMILY_NAME,
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
                family = SANS_SERIF_FAMILY_NAME,
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

        decorView.findViewByType(ActionBarContainer::class.java)?.let {
            (it.getChildAt(0) as? Toolbar)?.let { toolbar ->
                val toolbarScreenCoordinates = toolbar.getViewAbsoluteCoordinates()

                val toolbarWireframe = MobileSegment.Wireframe.TextWireframe(
                    id = toolbar.resolveId(),
                    x = toolbarScreenCoordinates[0].toLong().densityNormalized(density),
                    y = toolbarScreenCoordinates[1].toLong().densityNormalized(density),
                    width = toolbar.width.toLong().densityNormalized(density),
                    height = toolbar.height.toLong().densityNormalized(density),
                    shapeStyle = MobileSegment.ShapeStyle(
                        backgroundColor = "#F1F1F3FF",
                        opacity = toolbar.alpha,
                        cornerRadius = 4
                    ),
                    border = MobileSegment.ShapeBorder(
                        color = "#D3D3D3FF",
                        width = 1L
                    ),
                    text = UNSUPPORTED_VIEW_TITLE,
                    textStyle = MobileSegment.TextStyle(
                        family = SANS_SERIF_FAMILY_NAME,
                        size = 10,
                        color = "#FF0000FF"
                    ),
                    textPosition = MobileSegment.TextPosition(
                        alignment = MobileSegment.Alignment(
                            horizontal = MobileSegment.Horizontal.CENTER,
                            vertical = MobileSegment.Vertical.CENTER
                        )
                    )
                )

                fullSnapshotRecordWireframes.add(toolbarWireframe)
            }
        }

        val fullSnapshotRecord = MobileSegment.MobileRecord.MobileFullSnapshotRecord(
            timestamp = System.currentTimeMillis(),
            data = MobileSegment.Data(wireframes = fullSnapshotRecordWireframes)
        )

        // TODO RUMM-2991 sessionId, applicationId, viewId will be assessed in a future iteration
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

    @Suppress("DEPRECATION")
    private fun resolveScreenDimensions(activity: Activity): Pair<Long, Long> {
        val displayMetrics = activity.resources.displayMetrics
        val screenDensity = displayMetrics.density
        val screenHeight: Long
        val screenWidth: Long
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val currentWindowMetrics = windowManager.currentWindowMetrics
            val screenBounds = currentWindowMetrics.bounds
            screenHeight = (screenBounds.bottom - screenBounds.top).toLong()
                .densityNormalized(screenDensity)
            screenWidth = (screenBounds.right - screenBounds.left).toLong()
                .densityNormalized(screenDensity)
        } else {
            val size = Point()
            windowManager.defaultDisplay.getSize(size)
            screenHeight = size.y.toLong().densityNormalized(screenDensity)
            screenWidth = size.x.toLong().densityNormalized(screenDensity)
        }
        return screenWidth to screenHeight
    }

    companion object {
        private const val SAMPLE_IN_ALL_SESSIONS = 100f
        private const val BLACK_COLOR_AS_HEXA = 0
        private const val FULL_OPACITY_AS_HEXA = 255
        private const val SANS_SERIF_FAMILY_NAME = "roboto, sans-serif"
        private const val UNSUPPORTED_VIEW_TITLE = "Toolbar"
    }
}
