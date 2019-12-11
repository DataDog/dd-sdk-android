package com.datadog.android.sdk.integrationtests

import android.app.Application
import com.datadog.android.Datadog
import com.datadog.android.log.Logger

class IntegrationTestsApplication:Application() {

    override fun onCreate() {
        super.onCreate()
        // we just make an initialisation here. We will not need the clientToken for now as we
        // will not send any events to the server. The endpointUrl will be set to the
        // MockWebServer url in the test rule.
        Datadog.initialize(this, "", "")

    }
}