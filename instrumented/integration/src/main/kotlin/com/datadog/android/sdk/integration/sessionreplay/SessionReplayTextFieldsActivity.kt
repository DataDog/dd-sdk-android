/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import android.os.Bundle
import android.widget.EditText
import com.datadog.android.sdk.integration.R

internal open class SessionReplayTextFieldsActivity : BaseSessionReplayActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sr_text_fields_layout)

        findViewById<EditText>(R.id.edit_text_view)?.setText("User input text")
        findViewById<EditText>(R.id.app_compat_edit_text_view)?.setText("AppCompat input")
    }
}
