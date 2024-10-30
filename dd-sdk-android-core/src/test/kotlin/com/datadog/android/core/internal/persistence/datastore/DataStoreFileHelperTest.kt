package com.datadog.android.core.internal.persistence.datastore

import com.datadog.android.api.InternalLogger
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.io.File

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DataStoreFileHelperTest {

    lateinit var testedHelper: DataStoreFileHelper

    @TempDir
    lateinit var tempDir: File

    @StringForgery
    lateinit var fakeFeatureName: String

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        testedHelper = DataStoreFileHelper(mockInternalLogger)
    }

    @Test
    fun `M return datastore file W getDataStoreFile()`(
        @StringForgery fakeKey: String
    ) {
        // Given
        val expectedDataStoreDir = File(tempDir, "datastore_v0")
        val expectedFeatureDir = File(expectedDataStoreDir, fakeFeatureName)
        val expectedFile = File(expectedFeatureDir, fakeKey)

        // When
        val result = testedHelper.getDataStoreFile(tempDir, fakeFeatureName, fakeKey)

        // Then
        assertThat(result).isEqualTo(expectedFile)
        assertThat(result.parentFile).exists().canWrite()
    }

    @Test
    fun `M return datastore dir W getDataStoreDirectory()`() {
        // Given
        val expectedDataStoreDir = File(tempDir, "datastore_v0")
        val expectedFeatureDir = File(expectedDataStoreDir, fakeFeatureName)

        // When
        val result = testedHelper.getDataStoreDirectory(tempDir, fakeFeatureName)

        // Then
        assertThat(result).isEqualTo(expectedFeatureDir)
        assertThat(result).exists().canWrite()
    }
}
