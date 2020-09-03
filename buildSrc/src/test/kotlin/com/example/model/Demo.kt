package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.Boolean
import kotlin.Double
import kotlin.Long
import kotlin.Nothing
import kotlin.String

internal data class Demo(
    val s: String,
    val i: Long,
    val n: Double,
    val b: Boolean,
    val l: Nothing? = null,
    val ns: String? = null,
    val ni: Long? = null,
    val nn: Double? = null,
    val nb: Boolean? = null,
    val nl: Nothing? = null
) {
    fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("s", s)
        json.addProperty("i", i)
        json.addProperty("n", n)
        json.addProperty("b", b)
        json.add("l", null)
        if (ns != null) json.addProperty("ns", ns)
        if (ni != null) json.addProperty("ni", ni)
        if (nn != null) json.addProperty("nn", nn)
        if (nb != null) json.addProperty("nb", nb)
        return json
    }
}
