/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.data.db.realm

import android.provider.BaseColumns
import com.datadog.android.sample.data.db.DatadogDbContract
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PersistedName
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.UUID

internal open class LogRealm(
    @PersistedName(name = BaseColumns._ID)
    @PrimaryKey
    var id: String = UUID.randomUUID().toString(),
    @PersistedName(name = DatadogDbContract.Logs.COLUMN_NAME_MESSAGE)
    var message: String,
    @PersistedName(name = DatadogDbContract.Logs.COLUMN_NAME_TIMESTAMP)
    var timestamp: String,
    @PersistedName(name = DatadogDbContract.Logs.COLUMN_NAME_TTL)
    var ttl: Long
) : RealmObject {
    constructor() : this(message = "", timestamp = "", ttl = 0L)
}
