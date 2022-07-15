package com.example.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.collections.ArrayList
import kotlin.collections.List
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

public data class Household(
    public val pets: List<Animal>? = null,
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        pets?.let { petsNonNull ->
            val petsArray = JsonArray(petsNonNull.size)
            petsNonNull.forEach { petsArray.add(it.toJson()) }
            json.add("pets", petsArray)
        }
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): Household {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                val pets = jsonObject.get("pets")?.asJsonArray?.let { jsonArray ->
                    val collection = ArrayList<Animal>(jsonArray.size())
                    jsonArray.forEach {
                        collection.add(Animal.fromJson(it.toString()))
                    }
                    collection
                }
                return Household(pets)
            } catch (e: IllegalStateException) {
                throw JsonParseException("Unable to parse json into type Household", e)
            } catch (e: NumberFormatException) {
                throw JsonParseException("Unable to parse json into type Household", e)
            } catch (e: NullPointerException) {
                throw JsonParseException("Unable to parse json into type Household", e)
            }
        }
    }

    /**
     * A representation of the animal kingdom
     */
    public sealed class Animal {
        public abstract fun toJson(): JsonElement

        public data class Fish(
            public val water: Water,
            public val size: Long? = null,
        ) : Animal() {
            public override fun toJson(): JsonElement {
                val json = JsonObject()
                json.add("water", water.toJson())
                size?.let { sizeNonNull ->
                    json.addProperty("size", sizeNonNull)
                }
                return json
            }

            public companion object {
                @JvmStatic
                @Throws(JsonParseException::class)
                public fun fromJson(jsonString: String): Fish {
                    try {
                        val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                        val water = Water.fromJson(jsonObject.get("water").asString)
                        val size = jsonObject.get("size")?.asLong
                        return Fish(water, size)
                    } catch (e: IllegalStateException) {
                        throw JsonParseException("Unable to parse json into type Fish", e)
                    } catch (e: NumberFormatException) {
                        throw JsonParseException("Unable to parse json into type Fish", e)
                    } catch (e: NullPointerException) {
                        throw JsonParseException("Unable to parse json into type Fish", e)
                    }
                }
            }
        }

        public data class Bird(
            public val food: Food,
            public val canFly: Boolean,
        ) : Animal() {
            public override fun toJson(): JsonElement {
                val json = JsonObject()
                json.add("food", food.toJson())
                json.addProperty("can_fly", canFly)
                return json
            }

            public companion object {
                @JvmStatic
                @Throws(JsonParseException::class)
                public fun fromJson(jsonString: String): Bird {
                    try {
                        val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                        val food = Food.fromJson(jsonObject.get("food").asString)
                        val canFly = jsonObject.get("can_fly").asBoolean
                        return Bird(food, canFly)
                    } catch (e: IllegalStateException) {
                        throw JsonParseException("Unable to parse json into type Bird", e)
                    } catch (e: NumberFormatException) {
                        throw JsonParseException("Unable to parse json into type Bird", e)
                    } catch (e: NullPointerException) {
                        throw JsonParseException("Unable to parse json into type Bird", e)
                    }
                }
            }
        }

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(jsonString: String): Animal {
                val errors = mutableListOf<Throwable>()
                val asFish = try {
                    Fish.fromJson(jsonString)
                } catch (e: JsonParseException) {
                    errors.add(e)
                    null
                }
                val asBird = try {
                    Bird.fromJson(jsonString)
                } catch (e: JsonParseException) {
                    errors.add(e)
                    null
                }
                val result = arrayOf(
                    asFish,
                    asBird,
                ).firstOrNull { it != null }
                if (result == null) {
                    val message = "Unable to parse json into one of type Animal"
                    throw JsonParseException(message, errors[0])
                }
                return result
            }
        }
    }

    public enum class Water(
        private val jsonValue: String,
    ) {
        SALT("salt"),
        FRESH("fresh"),
        ;

        public fun toJson(): JsonElement = JsonPrimitive(jsonValue)

        public companion object {
            @JvmStatic
            public fun fromJson(jsonString: String): Water = values().first {
                it.jsonValue == jsonString
            }
        }
    }

    public enum class Food(
        private val jsonValue: String,
    ) {
        FISH("fish"),
        BIRD("bird"),
        RODENT("rodent"),
        INSECT("insect"),
        FRUIT("fruit"),
        SEEDS("seeds"),
        POLLEN("pollen"),
        ;

        public fun toJson(): JsonElement = JsonPrimitive(jsonValue)

        public companion object {
            @JvmStatic
            public fun fromJson(jsonString: String): Food = values().first {
                it.jsonValue == jsonString
            }
        }
    }
}