/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.profiling

import com.datadog.android.rum.profiling.ProfileUtils.functionId
import com.datadog.android.rum.profiling.ProfileUtils.locationId
import com.datadog.android.rum.utils.forge.Configurator
import com.google.perftools.profiles.ProfileProto.Profile
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import java.io.File
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(Configurator::class)
internal class ProfileUtilsTest {

    private val profileDumpDirectory = File("profiling").apply {
        if (!exists()) {
            mkdirs()
        }
    }

    @Test
    fun `M create valid profile W createProfile(threadStackTraces)`(forge: Forge) {
        // Given
        val snapshotInterval = forge.aPositiveLong()
        val currentTimeInNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
        val mainThread = Thread("main")
        val otherThread = Thread("other")
        val stackTrace = arrayOf(
            StackTraceElement("com.example.Test", "testMethod", "Test.kt", 42),
            StackTraceElement("com.example.Other", "otherMethod", "Other.kt", 123)
        )
        val threadStackTraces = mapOf(
            mainThread to stackTrace,
            otherThread to stackTrace
        )
        val expectedOutput = """
            default_sample_type: 0
            doc_url: 0
            drop_frames: 0
            duration_nanos: 0
            function {
              filename: 5
              id: ${stackTrace[0].functionId()}
              name: 3
              start_line: 0
              system_name: 4
            }
            function {
              filename: 8
              id: ${stackTrace[1].functionId()}
              name: 6
              start_line: 0
              system_name: 7
            }
            keep_frames: 0
            location {
              address: 0
              id: ${stackTrace[0].locationId()}
              line {
                column: 0
                function_id: ${stackTrace[0].functionId()}
                line: 42
              }
              mapping_id: 0
            }
            location {
              address: 0
              id: ${stackTrace[1].locationId()}
              line {
                column: 0
                function_id: ${stackTrace[1].functionId()}
                line: 123
              }
              mapping_id: 0
            }
            period: $snapshotInterval
            period_type {
              type: 1
              unit: 2
            }
            sample {
              label {
                key: 9
                num: ${mainThread.id}
                num_unit: 0
                str: 0
              }
              label {
                key: 10
                num: 0
                num_unit: 0
                str: 11
              }
              location_id: ${stackTrace[0].locationId()}
              location_id: ${stackTrace[1].locationId()}
              value: 1
            }
            sample_type {
              type: 1
              unit: 2
            }
            string_table: ""
            string_table: "cpu"
            string_table: "nanoseconds"
            string_table: "testMethod"
            string_table: "com.example.Test.testMethod"
            string_table: "Test.kt"
            string_table: "otherMethod"
            string_table: "com.example.Other.otherMethod"
            string_table: "Other.kt"
            string_table: "thread_id"
            string_table: "thread_name"
            string_table: "main"
            time_nanos: $currentTimeInNanos
        """.trimIndent()

        // When
        val profile = ProfileUtils.createProfile(threadStackTraces, currentTimeInNanos, snapshotInterval)

        // Then
        val toString = profile.toString()
        val profileString = toString.removeRange(0, toString.indexOfFirst { it == '\n' } + 1)
        assertThat(profileString).isEqualTo(expectedOutput)
    }

    @Test
    fun `M create valid profile W merge { 2 stackTraces }`(forge: Forge) {
        // Given
        val snapshotInterval = forge.aPositiveLong()
        val firstProfileTimeInNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
        Thread.sleep(1000)
        val secondProfileTimeInNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
        val expectedDuration = secondProfileTimeInNanos - firstProfileTimeInNanos
        val mainThread = Thread("main")
        val otherThread = Thread("other")
        val profile1MainStackTrace = arrayOf(
            StackTraceElement("com.example.Test", "testMethod", "Test.kt", 42),
            StackTraceElement("com.example.Other", "otherMethod", "Other.kt", 123)
        )
        val profile2MainStackTrace = arrayOf(
            StackTraceElement("com.example.Test", "testMethod", "Test.kt", 42)
        )
        val profile1OtherStackTrace = arrayOf(
            StackTraceElement("com.example.Foo", "barMethod", "Foo.kt", 123)
        )
        val profile2OtherStackTrace = arrayOf(
            StackTraceElement("com.example.Foo", "barMethod", "Foo.kt", 123)
        )
        val profile1ThreadsStackTraces = mapOf(
            mainThread to profile1MainStackTrace,
            otherThread to profile1OtherStackTrace
        )
        val profile2ThreadsStackTraces = mapOf(
            mainThread to profile2MainStackTrace,
            otherThread to profile2OtherStackTrace
        )
        val function1Id = profile1MainStackTrace[0].functionId()
        val function2Id = profile1MainStackTrace[1].functionId()
        val location1Id = profile1MainStackTrace[0].locationId()
        val location2Id = profile1MainStackTrace[1].locationId()
        val expectedProfileString = """
            default_sample_type: 0
            doc_url: 0
            drop_frames: 0
            duration_nanos: $expectedDuration
            function {
              filename: 5
              id: $function1Id
              name: 3
              start_line: 0
              system_name: 4
            }
            function {
              filename: 8
              id: $function2Id
              name: 6
              start_line: 0
              system_name: 7
            }
            keep_frames: 0
            location {
              address: 0
              id: $location1Id
              line {
                column: 0
                function_id: $function1Id
                line: 42
              }
              mapping_id: 0
            }
            location {
              address: 0
              id: $location2Id
              line {
                column: 0
                function_id: $function2Id
                line: 123
              }
              mapping_id: 0
            }
            period: $snapshotInterval
            period_type {
              type: 1
              unit: 2
            }
            sample {
              label {
                key: 9
                num: ${mainThread.id}
                num_unit: 0
                str: 0
              }
              label {
                key: 10
                num: 0
                num_unit: 0
                str: 11
              }
              location_id: $location1Id
              location_id: $location2Id
              value: $snapshotInterval
            }
            sample {
              label {
                key: 9
                num: ${mainThread.id}
                num_unit: 0
                str: 0
              }
              label {
                key: 10
                num: 0
                num_unit: 0
                str: 11
              }
              location_id: $location1Id
              value: $snapshotInterval
            }
            sample_type {
              type: 1
              unit: 2
            }
            string_table: ""
            string_table: "cpu"
            string_table: "nanoseconds"
            string_table: "testMethod"
            string_table: "com.example.Test.testMethod"
            string_table: "Test.kt"
            string_table: "otherMethod"
            string_table: "com.example.Other.otherMethod"
            string_table: "Other.kt"
            string_table: "thread_id"
            string_table: "thread_name"
            string_table: "main"
            time_nanos: $firstProfileTimeInNanos
        """.trimIndent()

        val profile1 = ProfileUtils.createProfile(profile1ThreadsStackTraces, firstProfileTimeInNanos, snapshotInterval)
        val profile2 =
            ProfileUtils.createProfile(profile2ThreadsStackTraces, secondProfileTimeInNanos, snapshotInterval)

        // When
        val mergedProfile = ProfileUtils.merge(listOf(profile1, profile2), snapshotInterval)
        val mergedProfileString = mergedProfile.toString().removeFirstLine()

        // Dump the merged profile to file for debugging
        dumpProfileToFile(mergedProfile, "merged_profile")

        // Then
        assertThat(mergedProfile.sampleList).hasSize(2)
        assertThat(mergedProfileString).isEqualTo(expectedProfileString)
    }

    @Test
    fun `M skip current thread W createProfile(threadStackTraces)`(forge: Forge) {
        // Given
        val timestamp = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
        val snapshotInterval = forge.aPositiveLong()
        val currentThread = Thread.currentThread()
        val mainThread = Thread("main")
        val stackTrace = arrayOf(
            StackTraceElement("com.example.Test", "testMethod", "Test.kt", 42)
        )
        val threadStackTraces = mapOf(
            currentThread to stackTrace,
            mainThread to stackTrace
        )

        // When
        val profile = ProfileUtils.createProfile(threadStackTraces, timestamp, snapshotInterval)

        // Then
        assertThat(profile.sampleList).hasSize(1)
        val sample = profile.sampleList.first()
        assertThat(profile.stringTableList[sample.labelList[1].str.toInt()]).isEqualTo("main")
    }

    @Test
    fun `M skip non-main threads W createProfile(threadStackTraces)`(forge: Forge) {
        // Given
        val timestamp = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
        val snapshotInterval = forge.aPositiveLong()
        val otherThread = Thread("other")
        val stackTrace = arrayOf(
            StackTraceElement("com.example.Test", "testMethod", "Test.kt", 42)
        )
        val threadStackTraces = mapOf(
            otherThread to stackTrace
        )

        // When
        val profile = ProfileUtils.createProfile(threadStackTraces, timestamp, snapshotInterval)

        // Then
        assertThat(profile.sampleList).isEmpty()
    }

    @Test
    fun `M skip empty stack traces W createProfile(threadStackTraces)`(forge: Forge) {
        // Given
        val timestamp = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
        val snapshotInterval = forge.aPositiveLong()
        val mainThread = Thread("main")
        val threadStackTraces: Map<Thread, Array<StackTraceElement>> = mapOf(
            mainThread to emptyArray()
        )

        // When
        val profile = ProfileUtils.createProfile(threadStackTraces, timestamp, snapshotInterval)

        // Then
        assertThat(profile.sampleList).isEmpty()
    }

    @Test
    fun `M create profile W single thread with n methods`(forge: Forge) {
        // Given
        val timestamp = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
        val snapshotInterval = forge.aPositiveLong()
        val thread = Thread("main")
        val numberOfMethodsInStackTrace = forge.anInt(30, 100)
        val stack = Array(numberOfMethodsInStackTrace) { idx ->
            StackTraceElement(
                "com.example.Class$idx",
                "method$idx",
                "Class$idx.kt",
                idx * 10
            )
        }

        // When
        val profile = ProfileUtils.createProfile(mapOf(thread to stack), timestamp, snapshotInterval)

        assertThat(profile.functionCount).isEqualTo(numberOfMethodsInStackTrace)
        assertThat(profile.locationCount).isEqualTo(numberOfMethodsInStackTrace)
        assertThat(profile.locationList.size).isEqualTo(numberOfMethodsInStackTrace)
        assertThat(profile.sampleCount).isEqualTo(1)
        assertThat(profile.sampleList[0].locationIdCount).isEqualTo(numberOfMethodsInStackTrace)
    }

    @Test
    fun `M merge multiple profiles W createProfile and merge`(forge: Forge) {
        // Given
        val snapshotInterval = forge.aPositiveLong()
        val mainThread = Thread("main")
        val stack1Size = forge.anInt(1, 10)
        val stack2Size = stack1Size + 5
        val stack3Size = stack2Size + 5
        val stack1 = Array(stack1Size) { idx ->
            StackTraceElement("com.example.A$idx", "methodA$idx", "A$idx.kt", idx)
        }
        val stack2 = Array(stack2Size) { idx ->
            StackTraceElement("com.example.A$idx", "methodA$idx", "A$idx.kt", idx)
        }
        val stack3 = Array(stack3Size) { idx ->
            StackTraceElement("com.example.A$idx", "methodA$idx", "A$idx.kt", idx)
        }
        val expectedFunctionCount = stack1Size + 10 // 5 from stack2 and 5 from stack3
        val expectedLocationCount = stack1Size + 10 // 5 from stack2 and 5 from stack3
        val expectedSampleCount = 3 // 1 from each profile

        val timestamp1 = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
        val profile1 = ProfileUtils.createProfile(mapOf(mainThread to stack1), timestamp1, snapshotInterval)
        Thread.sleep(10)
        val timestamp2 = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
        val profile2 = ProfileUtils.createProfile(mapOf(mainThread to stack2), timestamp2, snapshotInterval)
        Thread.sleep(10)
        val timestamp3 = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
        val profile3 = ProfileUtils.createProfile(mapOf(mainThread to stack3), timestamp3, snapshotInterval)

        // When
        val mergedProfile = ProfileUtils.merge(listOf(profile1, profile2, profile3), snapshotInterval)

        // Then
        val expectedDuration = timestamp3 - timestamp1
        assertThat(mergedProfile.durationNanos).isEqualTo(expectedDuration)
        // Depending on the merge implementation, the merged profile might combine functions and locations.
        // This sample assumes that all function and location entries are merged.
        assertThat(mergedProfile.functionCount).isEqualTo(expectedFunctionCount)
        assertThat(mergedProfile.locationCount).isEqualTo(expectedLocationCount)
        // We expect at least one sample representing the merged stack(s)
        assertThat(mergedProfile.sampleList.size).isEqualTo(expectedSampleCount)
        assertThat(mergedProfile.sampleList[0].locationIdCount).isEqualTo(stack1Size)
        assertThat(mergedProfile.sampleList[1].locationIdCount).isEqualTo(stack2Size)
        assertThat(mergedProfile.sampleList[2].locationIdCount).isEqualTo(stack3Size)

        // assess the locationIds in sampleList
        assertThat(mergedProfile.sampleList[2].locationIdList).containsAll(mergedProfile.sampleList[0].locationIdList)
        assertThat(mergedProfile.sampleList[2].locationIdList).containsAll(mergedProfile.sampleList[1].locationIdList)
        assertThat(mergedProfile.sampleList[1].locationIdList).containsAll(mergedProfile.sampleList[0].locationIdList)
    }

    @Test
    fun `M merge multiple profiles W createProfile and merge {same method different line}`(forge: Forge) {
        // Given
        val mainThread = Thread("main")
        val snapshotInterval = TimeUnit.MILLISECONDS.toNanos(2)

        val stack1 = arrayOf(
            StackTraceElement("com.example.A", "methodA", "A.kt", 4),
            StackTraceElement("com.example.B", "methodB", "B.kt", 3),
            StackTraceElement("com.example.A", "methodA", "A.kt", 2),
            StackTraceElement("com.example.F", "methodF", "F.kt", 1)
        )
        val stack2 = arrayOf(
            StackTraceElement("com.example.D", "methodD", "D.kt", 5),
            StackTraceElement("com.example.A", "methodA", "A.kt", 4),
            StackTraceElement("com.example.B", "methodB", "B.kt", 3),
            StackTraceElement("com.example.A", "methodA", "A.kt", 2),
            StackTraceElement("com.example.F", "methodF", "F.kt", 1)
        )
        val expectedSampleCount = 2
        val expectedFunctionCount = 3 + 1 // 2 from stack1 and 1 extra from stack2
        val expectedLocationCount = 4 + 1 // 4 from stack1 and 1 extra from stack2

        val timestamp1 = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
        val profile1 = ProfileUtils.createProfile(mapOf(mainThread to stack1), timestamp1, snapshotInterval)
        Thread.sleep(10)
        val timestamp2 = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
        val profile2 = ProfileUtils.createProfile(mapOf(mainThread to stack2), timestamp2, snapshotInterval)

        // When
        val mergedProfile = ProfileUtils.merge(listOf(profile1, profile2), snapshotInterval)

        // Then
        val expectedDuration = timestamp2 - timestamp1
        assertThat(mergedProfile.durationNanos).isEqualTo(expectedDuration)
        // Depending on the merge implementation, the merged profile might combine functions and locations.
        // This sample assumes that all function and location entries are merged.
        assertThat(mergedProfile.functionCount).isEqualTo(expectedFunctionCount)
        assertThat(mergedProfile.locationCount).isEqualTo(expectedLocationCount)
        assertThat(mergedProfile.sampleList.size).isEqualTo(expectedSampleCount)
        assertThat(mergedProfile.sampleList[0].locationIdCount).isEqualTo(stack1.size)
        assertThat(mergedProfile.sampleList[1].locationIdCount).isEqualTo(stack2.size)

        // assess the locationIds in sampleList
        assertThat(mergedProfile.sampleList[1].locationIdList).containsAll(mergedProfile.sampleList[0].locationIdList)
        dumpProfileToFile(mergedProfile, "merged_profile_same_method_different_line")
    }

    private fun dumpProfileToFile(profile: Profile, fileName: String) {
        val mergedFileAsBinary = File(profileDumpDirectory, "$fileName.pprof")
        val mergedFileAsText = File(profileDumpDirectory, "$fileName.text")
        mergedFileAsBinary.writeBytes(profile.toByteArray())
        mergedFileAsText.writeText(profile.toString())
    }

    private fun String.removeFirstLine(): String {
        return removeRange(0, indexOfFirst { it == '\n' } + 1)
    }
}
