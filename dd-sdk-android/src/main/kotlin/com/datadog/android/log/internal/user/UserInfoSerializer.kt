/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.user

import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.core.internal.utils.toJsonElement
import com.google.gson.JsonObject

internal class UserInfoSerializer :
    Serializer<UserInfo> {

    override fun serialize(model: UserInfo): String {
        val jsonObject = JsonObject()
        if (!model.id.isNullOrEmpty()) {
            jsonObject.addProperty(ID, model.id)
        }
        if (!model.name.isNullOrEmpty()) {
            jsonObject.addProperty(NAME, model.name)
        }
        if (!model.email.isNullOrEmpty()) {
            jsonObject.addProperty(EMAIL, model.email)
        }
        // add extra info
        if (model.extraInfo.isNotEmpty()) {
            val extra = JsonObject()
            model.extraInfo.forEach {
                extra.add(it.key, it.value.toJsonElement())
            }
            jsonObject.add(EXTRA_INFO, extra)
        }
        return jsonObject.toString()
    }

    companion object {
        internal const val EMAIL: String = "email"
        internal const val ID: String = "id"
        internal const val NAME: String = "name"
        internal const val EXTRA_INFO = "extra"
    }
}
