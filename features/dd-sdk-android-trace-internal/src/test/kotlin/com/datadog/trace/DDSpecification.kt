package com.datadog.trace

import com.datadog.tools.unit.createInstance
import com.datadog.tools.unit.setStaticValue
import com.datadog.trace.api.Config
import com.datadog.trace.api.InstrumenterConfig
import com.datadog.trace.api.naming.SpanNaming
import com.datadog.trace.bootstrap.config.provider.ConfigProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables
import uk.org.webcompere.systemstubs.jupiter.SystemStub
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension

@Extensions(
    ExtendWith(SystemStubsExtension::class)
)
abstract class DDSpecification {
    private val removedProperties: MutableMap<String, String> = mutableMapOf()

    @SystemStub
    private val environmentVariables = EnvironmentVariables()

    @BeforeEach
    open fun setup() {
        clearSystemProperties()
        assertThat(System.getProperties().any { it.key.toString().lowercase().startsWith("dd_") }).isFalse()
        assertThat(System.getenv().any { it.key.lowercase().startsWith("dd_") }).isFalse()
        resetConfigs()
    }

    @AfterEach
    open fun cleanup() {
        restoreSystemProperties()
    }

    private fun clearSystemProperties() {
        System.getProperties().keys.forEach {
            val keyAsString = it.toString()
            if (keyAsString.lowercase().startsWith("dd_")) {
                System.getProperty(keyAsString)?.let { keyValue -> removedProperties.put(keyAsString, keyValue) }
            }
        }
        removedProperties.keys.forEach { System.clearProperty(it) }

        val keys = System.getenv().keys.toList()
        keys.forEach {
            val keyAsString = it.toString()
            if (keyAsString.lowercase().startsWith("dd_")) {
                environmentVariables.remove(keyAsString)
            }
        }
    }

    private fun restoreSystemProperties() {
        removedProperties.forEach { (key, value) -> System.setProperty(key, value) }
        removedProperties.clear()
    }

    private fun resetConfigs() {
        val newConfig = createInstance(Config::class.java)
        Config::class.java.setStaticValue("INSTANCE", newConfig)
        val newInstrumenterConfig = createInstance(
            InstrumenterConfig::class.java,
            ConfigProvider.getInstance()
        )
        InstrumenterConfig::class.java.setStaticValue("INSTANCE", newInstrumenterConfig)
        val newSpanNaming = createInstance(SpanNaming::class.java)
        SpanNaming.Singleton::class.java.setStaticValue("INSTANCE", newSpanNaming)
    }
}
