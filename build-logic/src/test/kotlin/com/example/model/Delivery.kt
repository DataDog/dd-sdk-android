package com.example.model

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.IllegalStateException
import java.lang.NullPointerException
import java.lang.NumberFormatException
import kotlin.String
import kotlin.jvm.JvmStatic
import kotlin.jvm.Throws

public data class Delivery(
    public val item: String,
    public val customer: Customer,
) {
    public fun toJson(): JsonElement {
        val json = JsonObject()
        json.addProperty("item", item)
        json.add("customer", customer.toJson())
        return json
    }

    public companion object {
        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJson(jsonString: String): Delivery {
            try {
                val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                return fromJsonObject(jsonObject)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Delivery",
                    e
                )
            }
        }

        @JvmStatic
        @Throws(JsonParseException::class)
        public fun fromJsonObject(jsonObject: JsonObject): Delivery {
            try {
                val item = jsonObject.get("item").asString
                val customer = jsonObject.get("customer").asJsonObject.let {
                    Customer.fromJsonObject(it)
                }
                return Delivery(item, customer)
            } catch (e: IllegalStateException) {
                throw JsonParseException(
                    "Unable to parse json into type Delivery",
                    e
                )
            } catch (e: NumberFormatException) {
                throw JsonParseException(
                    "Unable to parse json into type Delivery",
                    e
                )
            } catch (e: NullPointerException) {
                throw JsonParseException(
                    "Unable to parse json into type Delivery",
                    e
                )
            }
        }
    }

    public data class Customer(
        public val name: String? = null,
        public val billingAddress: Address? = null,
        public val shippingAddress: Address? = null,
    ) {
        public fun toJson(): JsonElement {
            val json = JsonObject()
            name?.let { nameNonNull ->
                json.addProperty("name", nameNonNull)
            }
            billingAddress?.let { billingAddressNonNull ->
                json.add("billing_address", billingAddressNonNull.toJson())
            }
            shippingAddress?.let { shippingAddressNonNull ->
                json.add("shipping_address", shippingAddressNonNull.toJson())
            }
            return json
        }

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(jsonString: String): Customer {
                try {
                    val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                    return fromJsonObject(jsonObject)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Customer",
                        e
                    )
                }
            }

            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJsonObject(jsonObject: JsonObject): Customer {
                try {
                    val name = jsonObject.get("name")?.asString
                    val billingAddress = jsonObject.get("billing_address")?.asJsonObject?.let {
                        Address.fromJsonObject(it)
                    }
                    val shippingAddress = jsonObject.get("shipping_address")?.asJsonObject?.let {
                        Address.fromJsonObject(it)
                    }
                    return Customer(name, billingAddress, shippingAddress)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Customer",
                        e
                    )
                } catch (e: NumberFormatException) {
                    throw JsonParseException(
                        "Unable to parse json into type Customer",
                        e
                    )
                } catch (e: NullPointerException) {
                    throw JsonParseException(
                        "Unable to parse json into type Customer",
                        e
                    )
                }
            }
        }
    }

    public data class Address(
        public val streetAddress: String,
        public val city: String,
        public val state: String,
    ) {
        public fun toJson(): JsonElement {
            val json = JsonObject()
            json.addProperty("street_address", streetAddress)
            json.addProperty("city", city)
            json.addProperty("state", state)
            return json
        }

        public companion object {
            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJson(jsonString: String): Address {
                try {
                    val jsonObject = JsonParser.parseString(jsonString).asJsonObject
                    return fromJsonObject(jsonObject)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Address",
                        e
                    )
                }
            }

            @JvmStatic
            @Throws(JsonParseException::class)
            public fun fromJsonObject(jsonObject: JsonObject): Address {
                try {
                    val streetAddress = jsonObject.get("street_address").asString
                    val city = jsonObject.get("city").asString
                    val state = jsonObject.get("state").asString
                    return Address(streetAddress, city, state)
                } catch (e: IllegalStateException) {
                    throw JsonParseException(
                        "Unable to parse json into type Address",
                        e
                    )
                } catch (e: NumberFormatException) {
                    throw JsonParseException(
                        "Unable to parse json into type Address",
                        e
                    )
                } catch (e: NullPointerException) {
                    throw JsonParseException(
                        "Unable to parse json into type Address",
                        e
                    )
                }
            }
        }
    }
}
