/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.utils

import android.os.Build
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext

class ApiLevelExtension :
    BeforeTestExecutionCallback {

    override fun beforeTestExecution(context: ExtensionContext) {
        val method = context.requiredTestMethod
        val targetApi = method.getAnnotation(TestTargetApi::class.java)

        val apiLevel = targetApi?.value ?: DEFAULT_TEST_API_LEVEL
        setApiLevel(apiLevel)
    }

    private fun setApiLevel(apiLevel: Int) {
        val versionClass = Build.VERSION::class.java
        val field = versionClass.getDeclaredField("SDK_INT")

        // Make it accessible
        field.isAccessible = true

        // Make it non final
        val modifiersField = Field::class.java.getDeclaredField("modifiers")
        modifiersField.isAccessible = true
        modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())

        // Change the api level :)
        field.set(null, apiLevel)
    }

    companion object {
        const val DEFAULT_TEST_API_LEVEL = Build.VERSION_CODES.BASE
    }
}
