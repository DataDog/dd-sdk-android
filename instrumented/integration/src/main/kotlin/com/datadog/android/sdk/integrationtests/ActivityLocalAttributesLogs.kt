package com.datadog.android.sdk.integrationtests

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.datadog.android.log.Logger
import java.util.Locale

internal class ActivityLocalAttributesLogs : AppCompatActivity() {

    lateinit var logger: Logger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger = Runtime.logger(this)
        setContentView(R.layout.main_activity_layout)
        logger.i("MainActivity/onCreate", null, localAttributes)
    }

    override fun onStart() {
        super.onStart()
        logger.i("MainActivity/onStart", null, localAttributes)
    }

    override fun onResume() {
        super.onResume()
        logger.i("MainActivity/onResume", null, localAttributes)
    }

    companion object {
        val localAttributes = mapOf(
            "activity" to ActivityLocalAttributesLogs::class.java.simpleName,
            "year" to 2019,
            "locale" to Locale.US,
            "null_value" to null
        )
    }
}
