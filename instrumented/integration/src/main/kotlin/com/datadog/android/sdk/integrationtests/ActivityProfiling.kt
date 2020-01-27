package com.datadog.android.sdk.integrationtests

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.datadog.android.log.Logger
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig

internal class ActivityProfiling : AppCompatActivity() {

    lateinit var logger: Logger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger = RuntimeConfig.logger()
        setContentView(R.layout.main_activity_layout)
    }
}
