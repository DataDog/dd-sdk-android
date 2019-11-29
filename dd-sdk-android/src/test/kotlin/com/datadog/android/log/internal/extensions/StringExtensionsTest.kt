package com.datadog.android.log.internal.extensions

import com.datadog.android.log.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Java6Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings()
internal class StringExtensionsTest {

    @Test
    fun `calling the asDataDogTag will apply the DataDog Tag prefix`(@Forgery forge: Forge) {
        val randomTag = forge.anAlphabeticalString(size = forge.anInt(0, 30))
        assertThat(randomTag.asDataDogTag()).isEqualTo("DD_LOG+$randomTag")
    }
}
