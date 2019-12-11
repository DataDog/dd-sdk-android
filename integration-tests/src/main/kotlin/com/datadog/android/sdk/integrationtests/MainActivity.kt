package com.datadog.android.sdk.integrationtests

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity:AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity_layout)
        Runtime.appLogger.i("MainActivity/onCreate")
    }

    override fun onStart() {
        super.onStart()
        Runtime.appLogger.i("MainActivity/onStart")
    }


    override fun onResume() {
        super.onResume()
        Runtime.appLogger.i("MainActivity/onResume")
    }

}