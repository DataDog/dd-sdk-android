/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sqldelight

import androidx.sqlite.db.SupportSQLiteDatabase
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import java.util.Locale

/**
 * Extends the [AndroidSqliteDriver.Callback] to intercept any Database corruption callback and to
 * automatically send a RUM error event whenever this issue occurs.
 *
 * For more information [https://www.sqlite.org/howtocorrupt.html]
 */
class DatadogSqliteCallback(schema: SqlDriver.Schema) : AndroidSqliteDriver.Callback(schema) {

    /** @inheritDoc */
    override fun onCorruption(db: SupportSQLiteDatabase) {
        super.onCorruption(db)
        GlobalRum.get()
            .addError(
                String.format(
                    DATABASE_CORRUPTION_ERROR_MESSAGE,
                    db.path,
                    Locale.US
                ),
                RumErrorSource.SOURCE,
                null,
                mapOf(
                    RumAttributes.ERROR_DATABASE_PATH to db.path,
                    RumAttributes.ERROR_DATABASE_VERSION to db.version
                )
            )
    }

    companion object {
        internal const val DATABASE_CORRUPTION_ERROR_MESSAGE =
            "Corruption reported by sqlite database: %s"
    }
}
