/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.datadog.android.sdk.integration.R

internal open class SessionReplayPlaygroundActivity : BaseSessionReplayActivity() {
    lateinit var titleTextView: TextView
    lateinit var clickMeButton: Button

    @Suppress("CheckInternal")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.session_replay_layout)
        titleTextView = findViewById(R.id.title)
        clickMeButton = findViewById(R.id.button)
    }
}
