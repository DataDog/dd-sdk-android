/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sqlite

import android.database.DatabaseErrorHandler
import android.database.DefaultDatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import com.datadog.android.SdkReference
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.v2.api.InternalLogger
import java.util.Locale

/**
 * Provides an implementation of [DatadogDatabaseErrorHandler] already set up to send
 * relevant information to Datadog.
 *
 * It will automatically send RUM Error events whenever a Database corruption was signaled.
 * For more information [https://www.sqlite.org/howtocorrupt.html]
 *
 * @param sdkInstanceName the SDK instance name to bind to, or null to check the default instance.
 * Instrumentation won't be working until SDK instance is ready.
 * @param defaultErrorHandler the corruption error handler, by default it is [DefaultDatabaseErrorHandler].
 */
class DatadogDatabaseErrorHandler(
    private val sdkInstanceName: String? = null,
    internal val defaultErrorHandler: DatabaseErrorHandler = DefaultDatabaseErrorHandler()
) : DatabaseErrorHandler {

    private val sdkReference = SdkReference(sdkInstanceName)

    /** @inheritDoc */
    override fun onCorruption(dbObj: SQLiteDatabase) {
        defaultErrorHandler.onCorruption(dbObj)
        val sdkCore = sdkReference.get()
        if (sdkCore != null) {
            GlobalRum.get(sdkCore)
                .addError(
                    String.format(Locale.US, DATABASE_CORRUPTION_ERROR_MESSAGE, dbObj.path),
                    RumErrorSource.SOURCE,
                    null,
                    mapOf(
                        RumAttributes.ERROR_DATABASE_PATH to dbObj.path,
                        RumAttributes.ERROR_DATABASE_VERSION to dbObj.version
                    )
                )
        } else {
            val prefix = if (sdkInstanceName == null) {
                "Default SDK instance"
            } else {
                "SDK instance with name=$sdkInstanceName"
            }
            InternalLogger.UNBOUND.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                "$prefix is not found, skipping" +
                    " reporting the corruption of sqlite database: %s"
            )
        }
    }

    internal companion object {
        internal const val DATABASE_CORRUPTION_ERROR_MESSAGE =
            "Corruption reported by sqlite database: %s"
    }
}
