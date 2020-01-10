/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.gradle.plugin.gitdiff

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Before
import org.junit.Test

class GitConditionalDependencyLinkerTest {

    lateinit var testedLinker: GitConditionalDependencyLinker

    lateinit var mockModifiedPathsProvider: GitModifiedPathsProvider

    @Before
    fun setUp() {
        mockModifiedPathsProvider = mock()
        testedLinker = GitConditionalDependencyLinker(mockModifiedPathsProvider)
    }

    @Test
    fun linksTaskWithMatchingRegex() {

        // given
        val matching = Regex("dd-sdk-android/.*")
        val dependencies = mapOf(
            matching to listOf(
                Dependency(mock(), listOf("task1", "task2")),
                Dependency(mock(), listOf("task3", "task4"))
            )
        )
        whenever(mockModifiedPathsProvider.getModifiedPaths()).doReturn(
            listOf(
                "dd-sdk-android/folder1/Class1.java",
                "dd-sdk-android-timber/folder1/Class1.java",
                "instrumentation/folder2/Class1.java",
                "benchmarking/folder2/Class1.java"
            )
        )

        // when
        testedLinker.linkConditionalDependencies(dependencies)

        // then

        dependencies[matching]?.forEach {
            verify(it.task).dependsOn(it.dependsOn)
        }
    }

    @Test
    fun doesNotLinkTaskWithoutMatchingRegex() {

        // given
        val notMatching = Regex("dd-sdk-ios/.*")
        val dependencies = mapOf(
            notMatching to listOf(
                Dependency(mock(), listOf("task5", "task6")),
                Dependency(mock(), listOf("task2", "task8"))
            )
        )
        whenever(mockModifiedPathsProvider.getModifiedPaths()).doReturn(
            listOf(
                "dd-sdk-android/folder1/Class1.java",
                "dd-sdk-android-timber/folder1/Class1.java",
                "instrumentation/folder2/Class1.java",
                "benchmarking/folder2/Class1.java"
            )
        )

        // when
        testedLinker.linkConditionalDependencies(dependencies)

        // then
        dependencies[notMatching]?.forEach {
            verify(it.task, never()).dependsOn(it.dependsOn)
        }
    }
}