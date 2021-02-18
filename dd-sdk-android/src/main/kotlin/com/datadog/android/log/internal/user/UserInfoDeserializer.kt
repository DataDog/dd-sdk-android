/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.user

import com.datadog.android.core.internal.domain.Deserializer
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.core.model.UserInfo
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

internal class UserInfoDeserializer : Deserializer<UserInfo> {

    override fun deserialize(model: String): UserInfo? {
        return try {
            val jsonObject = JsonParser.parseString(model).asJsonObject
            val name = jsonObject.get(UserInfoSerializer.NAME)?.asString
            val id = jsonObject.get(UserInfoSerializer.ID)?.asString
            val email = jsonObject.get(UserInfoSerializer.EMAIL)?.asString
            val extraAttributes: Map<String, Any?> =
                jsonObject.get(UserInfoSerializer.EXTRA_INFO)?.asJsonObject?.let {
                    val extraAttributes = mutableMapOf<String, Any?>()
                    it.entrySet().forEach {
                        extraAttributes[it.key] = it.value
                    }
                    extraAttributes
                } ?: emptyMap()

            UserInfo(
                id,
                name,
                email,
                extraAttributes
            )
        } catch (e: JsonParseException) {
            sdkLogger.e(DESERIALIZE_ERROR_MESSAGE_FORMAT.format(model), e)
            null
        } catch (e: IllegalStateException) {
            sdkLogger.e(DESERIALIZE_ERROR_MESSAGE_FORMAT.format(model), e)
            null
        }
    }

    companion object {
        const val DESERIALIZE_ERROR_MESSAGE_FORMAT =
            "Error while trying to deserialize the serialized UserInfo: %s"
    }
}
