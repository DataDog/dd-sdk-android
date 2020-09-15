/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sqlite

import android.database.DatabaseErrorHandler
import android.database.DefaultDatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import java.util.Locale

/**
 * Provides an implementation of [DatadogDatabaseErrorHandler] already set up to send
 * relevant information to Datadog.
 *
 * It will automatically send RUM Error events whenever a Database corruption was signaled.
 * For more information [https://www.sqlite.org/howtocorrupt.html]
 */
class DatadogDatabaseErrorHandler(
    internal val defaultErrorHandler: DatabaseErrorHandler = DefaultDatabaseErrorHandler()
) : DatabaseErrorHandler {

    /** @inheritDoc */
    override fun onCorruption(dbObj: SQLiteDatabase) {
        defaultErrorHandler.onCorruption(dbObj)
        GlobalRum.get()
            .addError(
                String.format(DATABASE_CORRUPTION_ERROR_MESSAGE, dbObj.path, Locale.US),
                RumErrorSource.SOURCE,
                null,
                mapOf(
                    RumAttributes.ERROR_DATABASE_PATH to dbObj.path,
                    RumAttributes.ERROR_DATABASE_VERSION to dbObj.version
                )
            )
    }

    companion object {
        internal const val DATABASE_CORRUPTION_ERROR_MESSAGE =
            "Corruption reported by sqlite database: %s"
    }
}
