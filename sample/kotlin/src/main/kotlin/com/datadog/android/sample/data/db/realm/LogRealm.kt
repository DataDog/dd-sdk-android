/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.data.db.realm

import android.provider.BaseColumns
import com.datadog.android.sample.data.db.DatadogDbContract
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmField
import java.util.UUID

open class LogRealm(
    @RealmField(name = BaseColumns._ID)
    @PrimaryKey
    var id: String = UUID.randomUUID().toString(),
    @RealmField(name = DatadogDbContract.Logs.COLUMN_NAME_MESSAGE)
    var message: String,
    @RealmField(name = DatadogDbContract.Logs.COLUMN_NAME_TIMESTAMP)
    var timestamp: String,
    @RealmField(name = DatadogDbContract.Logs.COLUMN_NAME_TTL)
    var ttl: Long
) : RealmObject() {
    constructor() : this(message = "", timestamp = "", ttl = 0L)
}
