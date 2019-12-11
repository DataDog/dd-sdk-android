package com.datadog.android.sdk.integrationtests.utils.assertj

import com.google.gson.JsonObject
import org.assertj.core.api.AbstractAssert
import java.lang.reflect.Array

class JsonListAssert(actual: List<JsonObject>) : AbstractAssert<JsonListAssert, List<JsonObject>>
    (actual, JsonListAssert::class.java) {


    fun hasLogsWithMessagesInOrder(vararg messages: String):JsonListAssert {
        if(messages.isEmpty()) return this// we are ok
        var index = 0
        actual.forEach{
            if(messages[index]==it.get("message").asString){
                index++
            }
            if(index>=messages.size) return this // we are ok
        }

        if(index<messages.size){
            failWithMessage(
                "We were expecting following messages: [${messages.joinToString { "," }}] but " +
                        "they could not be found")
        }
        return this
    }

    companion object {
        internal fun assertThat(actual: List<JsonObject>): JsonListAssert =
            JsonListAssert(actual)
    }
}