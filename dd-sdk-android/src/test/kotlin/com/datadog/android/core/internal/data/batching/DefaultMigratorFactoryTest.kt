/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.batching

import com.datadog.android.core.internal.data.batching.migrators.MoveDataMigrator
import com.datadog.android.core.internal.data.batching.migrators.NoOpBatchedDataMigrator
import com.datadog.android.core.internal.data.batching.migrators.WipeDataMigrator
import com.datadog.android.privacy.TrackingConsent
import fr.xgouchet.elmyr.Forge
import java.util.stream.Stream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class DefaultMigratorFactoryTest {

    lateinit var underTest: DefaultMigratorFactory

    @ParameterizedTest
    @MethodSource("provideMigratorStatesData")
    fun `M generate the right migrator W required`(
        previousConsentFlag: TrackingConsent,
        newConsentFlag: TrackingConsent,
        pendingFolderPath: String,
        acceptedFolderPath: String,
        expected: ExpectedMigrator
    ) {

        // GIVEN
        underTest = DefaultMigratorFactory(pendingFolderPath, acceptedFolderPath)

        // WHEN
        val migrator = underTest.resolveMigrator(previousConsentFlag, newConsentFlag)

        // THEN
        when (expected) {
            is ExpectedMigrator.WipeMigrator -> {
                assertThat(migrator).isInstanceOf(WipeDataMigrator::class.java)
                assertThat((migrator as WipeDataMigrator).folderPath).isEqualTo(expected.folderPath)
            }
            is ExpectedMigrator.MoveMigrator -> {
                assertThat(migrator).isInstanceOf(MoveDataMigrator::class.java)
                val moveDataMigrator = migrator as MoveDataMigrator
                assertThat(moveDataMigrator.pendingFolderPath).isEqualTo(expected.fromFolderPath)
                assertThat(moveDataMigrator.approvedFolderPath).isEqualTo(expected.toFolderPath)
            }
            else -> {
                assertThat(migrator).isInstanceOf(NoOpBatchedDataMigrator::class.java)
            }
        }
    }

    companion object {

        var forge = Forge()

        @JvmStatic
        fun provideMigratorStatesData(): Stream<Arguments> {
            val pendingFolderPath = forge.aStringMatching("[a-zA-z]+/[a-zA-z]")
            val grantedFolderPath = forge.aStringMatching("[a-zA-z]+/[a-zA-z]")
            return Stream.of(
                // initial state migrator
                Arguments.arguments(
                    TrackingConsent.PENDING,
                    TrackingConsent.PENDING,
                    pendingFolderPath,
                    grantedFolderPath,
                    ExpectedMigrator.NoOpMigrator
                ),
                Arguments.arguments(
                    TrackingConsent.PENDING,
                    TrackingConsent.NOT_GRANTED,
                    pendingFolderPath,
                    grantedFolderPath,
                    ExpectedMigrator.WipeMigrator(pendingFolderPath)
                ),
                Arguments.arguments(
                    TrackingConsent.PENDING,
                    TrackingConsent.GRANTED,
                    pendingFolderPath,
                    grantedFolderPath,
                    ExpectedMigrator.MoveMigrator(pendingFolderPath, grantedFolderPath)
                ),
                // initial state migrator
                Arguments.arguments(
                    TrackingConsent.GRANTED,
                    TrackingConsent.GRANTED,
                    pendingFolderPath,
                    grantedFolderPath,
                    ExpectedMigrator.NoOpMigrator
                ),
                Arguments.arguments(
                    TrackingConsent.GRANTED,
                    TrackingConsent.PENDING,
                    pendingFolderPath,
                    grantedFolderPath,
                    ExpectedMigrator.NoOpMigrator
                ),
                Arguments.arguments(
                    TrackingConsent.GRANTED,
                    TrackingConsent.NOT_GRANTED,
                    pendingFolderPath,
                    grantedFolderPath,
                    ExpectedMigrator.NoOpMigrator
                ),
                // initial state migrator
                Arguments.arguments(
                    TrackingConsent.NOT_GRANTED,
                    TrackingConsent.NOT_GRANTED,
                    pendingFolderPath,
                    grantedFolderPath,
                    ExpectedMigrator.NoOpMigrator
                ),
                Arguments.arguments(
                    TrackingConsent.NOT_GRANTED,
                    TrackingConsent.PENDING,
                    pendingFolderPath,
                    grantedFolderPath,
                    ExpectedMigrator.NoOpMigrator
                ),
                Arguments.arguments(
                    TrackingConsent.NOT_GRANTED,
                    TrackingConsent.GRANTED,
                    pendingFolderPath,
                    grantedFolderPath,
                    ExpectedMigrator.NoOpMigrator
                )
            )
        }
    }

    internal sealed class ExpectedMigrator {
        object NoOpMigrator : ExpectedMigrator()
        class WipeMigrator(val folderPath: String) : ExpectedMigrator()
        class MoveMigrator(val fromFolderPath: String, val toFolderPath: String) :
            ExpectedMigrator()
    }
}
