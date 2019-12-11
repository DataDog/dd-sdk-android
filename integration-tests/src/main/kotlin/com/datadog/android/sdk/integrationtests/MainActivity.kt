package com.datadog.android.sdk.integrationtests

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity:AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity_layout)
    }
}