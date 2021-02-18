/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.user

import com.datadog.android.core.internal.utils.toJsonElement
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(Configurator::class)
internal class UserInfoSerializerTest {

    lateinit var testedSerializer: UserInfoSerializer

    @BeforeEach
    fun `set up`() {
        testedSerializer = UserInfoSerializer()
    }

    @Test
    fun `M serialize the event W serialize`(@Forgery fakeUserInfo: UserInfo) {
        // WHEN
        val serializedObject = testedSerializer.serialize(fakeUserInfo)

        // THEN
        val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
        assertThat(jsonObject)
            .hasField(UserInfoSerializer.ID, fakeUserInfo.id!!)
            .hasField(UserInfoSerializer.NAME, fakeUserInfo.name!!)
            .hasField(UserInfoSerializer.EMAIL, fakeUserInfo.email!!)

        assertThat(jsonObject).hasField(UserInfoSerializer.EXTRA_INFO) {
            fakeUserInfo.extraInfo.forEach { (key, keyValye) ->
                this.hasField(key, keyValye.toJsonElement())
            }
        }
    }

    @Test
    fun `M serialize the event W serialize { id is null }`(@Forgery userInfo: UserInfo) {
        // WHEN
        val fakeUserInfo = userInfo.copy(id = null)
        val serializedObject = testedSerializer.serialize(fakeUserInfo)

        // THEN
        val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
        assertThat(jsonObject)
            .doesNotHaveField(UserInfoSerializer.ID)
            .hasField(UserInfoSerializer.NAME, fakeUserInfo.name!!)
            .hasField(UserInfoSerializer.EMAIL, fakeUserInfo.email!!)

        assertThat(jsonObject).hasField(UserInfoSerializer.EXTRA_INFO) {
            fakeUserInfo.extraInfo.forEach { (key, keyValye) ->
                this.hasField(key, keyValye.toJsonElement())
            }
        }
    }

    @Test
    fun `M serialize the event W serialize { name is null }`(@Forgery userInfo: UserInfo) {
        // WHEN
        val fakeUserInfo = userInfo.copy(name = null)
        val serializedObject = testedSerializer.serialize(fakeUserInfo)

        // THEN
        val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
        assertThat(jsonObject)
            .hasField(UserInfoSerializer.ID, fakeUserInfo.id!!)
            .doesNotHaveField(UserInfoSerializer.NAME)
            .hasField(UserInfoSerializer.EMAIL, fakeUserInfo.email!!)

        assertThat(jsonObject).hasField(UserInfoSerializer.EXTRA_INFO) {
            fakeUserInfo.extraInfo.forEach { (key, keyValye) ->
                this.hasField(key, keyValye.toJsonElement())
            }
        }
    }

    @Test
    fun `M serialize the event W serialize { email is null }`(@Forgery userInfo: UserInfo) {
        // WHEN
        val fakeUserInfo = userInfo.copy(email = null)
        val serializedObject = testedSerializer.serialize(fakeUserInfo)

        // THEN
        val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
        assertThat(jsonObject)
            .hasField(UserInfoSerializer.ID, fakeUserInfo.id!!)
            .hasField(UserInfoSerializer.NAME, fakeUserInfo.name!!)
            .doesNotHaveField(UserInfoSerializer.EMAIL)

        assertThat(jsonObject).hasField(UserInfoSerializer.EXTRA_INFO) {
            fakeUserInfo.extraInfo.forEach { (key, keyValye) ->
                this.hasField(key, keyValye.toJsonElement())
            }
        }
    }

    @Test
    fun `M serialize the event W serialize { extra is empty }`(@Forgery userInfo: UserInfo) {
        // WHEN
        val fakeUserInfo = userInfo.copy(extraInfo = emptyMap())
        val serializedObject = testedSerializer.serialize(fakeUserInfo)

        // THEN
        val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
        assertThat(jsonObject)
            .hasField(UserInfoSerializer.ID, fakeUserInfo.id!!)
            .hasField(UserInfoSerializer.NAME, fakeUserInfo.name!!)
            .hasField(UserInfoSerializer.EMAIL, fakeUserInfo.email!!)

        assertThat(jsonObject).doesNotHaveField(UserInfoSerializer.EXTRA_INFO)
    }
}
