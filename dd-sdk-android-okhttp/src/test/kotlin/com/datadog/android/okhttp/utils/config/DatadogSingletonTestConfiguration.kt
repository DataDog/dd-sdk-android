/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.utils.config

import com.datadog.android.Datadog
import com.datadog.android.v2.api.SdkCore
import com.datadog.tools.unit.extensions.config.MockTestConfiguration
import com.datadog.tools.unit.setStaticValue
import fr.xgouchet.elmyr.Forge
import kotlin.reflect.full.memberFunctions

// TODO RUMM-2949 Share forgeries/test configurations between modules
internal class DatadogSingletonTestConfiguration :
    MockTestConfiguration<SdkCore>(SdkCore::class.java) {

    override fun setUp(forge: Forge) {
        super.setUp(forge)
        Datadog::class.java.setStaticValue("globalSdkCore", mockInstance)
    }

    override fun tearDown(forge: Forge) {
        Datadog::class.memberFunctions.first { it.name == "stop" }.call(Datadog::class.objectInstance)
        super.tearDown(forge)
    }
}
