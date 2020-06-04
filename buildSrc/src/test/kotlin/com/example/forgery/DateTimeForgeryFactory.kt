/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.example.forgery

import com.example.model.Date
import com.example.model.DateTime
import com.example.model.Time
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class DateTimeForgeryFactory : ForgeryFactory<DateTime> {
    override fun getForgery(forge: Forge): DateTime {
        return DateTime(
            date = Date(
                year = forge.aNullable { anInt() },
                month = forge.aNullable { getForgery() },
                day = forge.aNullable { anInt(1, 31) }
            ),
            time = Time(
                hour = forge.aNullable { anInt(0, 24) },
                minute = forge.aNullable { anInt(0, 60) },
                seconds = forge.aNullable { anInt(0, 24) }
            )
        )
    }
}
