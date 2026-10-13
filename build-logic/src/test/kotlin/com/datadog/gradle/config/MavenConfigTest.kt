/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import com.datadog.gradle.utils.Version
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource

class MavenConfigTest {

    @ParameterizedTest
    @EnumSource(PublishingDestination::class)
    fun internalRepositoryForDogfoodVersionIsDogfood(destination: PublishingDestination) {
        // When
        val repository = internalRepositoryFor(Version.Type.Dogfood("a1b2c3d"), destination)

        // Then
        assertThat(repository).isEqualTo(
            InternalMavenRepository(MavenConfig.DOGFOOD_REPOSITORY, MavenConfig.DOGFOOD_REPOSITORY_URL)
        )
    }

    @ParameterizedTest
    @MethodSource("nonDogfoodVersionTypes")
    fun internalRepositoryForInternalBenchmarkIsBenchmark(versionType: Version.Type) {
        // When
        val repository = internalRepositoryFor(versionType, PublishingDestination.INTERNAL_BENCHMARK)

        // Then
        assertThat(repository).isEqualTo(
            InternalMavenRepository(MavenConfig.BENCHMARK_REPOSITORY, MavenConfig.BENCHMARK_REPOSITORY_URL)
        )
    }

    @ParameterizedTest
    @MethodSource("nonDogfoodVersionTypes")
    fun internalRepositoryForMavenCentralIsNull(versionType: Version.Type) {
        // When
        val repository = internalRepositoryFor(versionType, PublishingDestination.MAVEN_CENTRAL)

        // Then
        assertThat(repository).isNull()
    }

    companion object {
        @JvmStatic
        fun nonDogfoodVersionTypes(): List<Version.Type> = listOf(
            Version.Type.Release,
            Version.Type.ReleaseCandidate(1),
            Version.Type.Beta(2),
            Version.Type.Alpha(3),
            Version.Type.Dev,
            Version.Type.Snapshot
        )
    }
}
