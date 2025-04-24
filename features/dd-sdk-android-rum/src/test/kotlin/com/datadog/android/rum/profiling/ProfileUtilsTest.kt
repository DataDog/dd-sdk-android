/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.profiling

import com.datadog.android.rum.profiling.ProfileUtils.functionId
import com.datadog.android.rum.profiling.ProfileUtils.locationId
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.junit.jupiter.MockitoExtension
import java.io.File
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(Configurator::class)
internal class ProfileUtilsTest {

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun `set up`() {
        // Nothing to set up
    }

    @Test
    fun `M create valid profile W createProfile()`() {
        // When
        val profile = ProfileUtils.createProfile()

        // Then
        assertThat(profile).isNotNull
        assertThat(profile.sampleTypeList).isNotEmpty
        assertThat(profile.sampleTypeList[0].type).isEqualTo(1) // "cpu" string index
        assertThat(profile.sampleTypeList[0].unit).isEqualTo(2) // "nanoseconds" string index
        assertThat(profile.periodType).isEqualTo(profile.sampleTypeList[0])
        assertThat(profile.period).isEqualTo(TimeUnit.MILLISECONDS.toNanos(10))
        assertThat(profile.timeNanos).isPositive
        assertThat(profile.durationNanos).isZero
    }

    @Test
    fun `M create valid profile W createProfile(threadStackTraces)`() {
        // Given
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
            period: 10000000
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
            string_table: "samples"
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
        val profile = ProfileUtils.createProfile(threadStackTraces, currentTimeInNanos)

        // Then
        val toString = profile.toString()
        val profileString = toString.removeRange(0, toString.indexOfFirst { it=='\n' }+1)
        assertThat(profileString).isEqualTo(expectedOutput)
//        assertThat(profile.sampleList).hasSize(1)
//        val sample = profile.sampleList.first()
//        assertThat(sample.valueList[0]).isEqualTo(1L)
//        assertThat(sample.labelList[0].key).isEqualTo(9) // "thread" string index
//        assertThat(profile.stringTableList[sample.labelList[0].str.toInt()]).isEqualTo("main")
    }
    @Test
    fun `M create valid profile W merge { 2 stackTraces }`() {
        // Given
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
            period: 10000000
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
              value: 1
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
              value: 1
            }
            sample_type {
              type: 1
              unit: 2
            }
            string_table: ""
            string_table: "cpu"
            string_table: "samples"
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

        val profile1 = ProfileUtils.createProfile(profile1ThreadsStackTraces, firstProfileTimeInNanos)
        val profile2 = ProfileUtils.createProfile(profile2ThreadsStackTraces, secondProfileTimeInNanos)

        // When
        val mergedProfile = ProfileUtils.merge(listOf(profile1, profile2))
        val mergedProfileString = mergedProfile.toString().removeFirstLine()



        // Then
        assertThat(mergedProfile.sampleList).hasSize(2)
        assertThat(mergedProfileString).isEqualTo(expectedProfileString)
    }

    @Test
    fun `M skip current thread W createProfile(threadStackTraces)`() {
        // Given
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
        val profile = ProfileUtils.createProfile(threadStackTraces)

        // Then
        assertThat(profile.sampleList).hasSize(1)
        val sample = profile.sampleList.first()
        assertThat(profile.stringTableList[sample.labelList[1].str.toInt()]).isEqualTo("main")
    }

    @Test
    fun `M skip non-main threads W createProfile(threadStackTraces)`() {
        // Given
        val otherThread = Thread("other")
        val stackTrace = arrayOf(
            StackTraceElement("com.example.Test", "testMethod", "Test.kt", 42)
        )
        val threadStackTraces = mapOf(
            otherThread to stackTrace
        )

        // When
        val profile = ProfileUtils.createProfile(threadStackTraces)

        // Then
        assertThat(profile.sampleList).isEmpty()
    }

    @Test
    fun `M skip empty stack traces W createProfile(threadStackTraces)`() {
        // Given
        val mainThread = Thread("main")
        val threadStackTraces:Map<Thread, Array<StackTraceElement>> = mapOf(
            mainThread to emptyArray()
        )

        // When
        val profile = ProfileUtils.createProfile(threadStackTraces)

        // Then
        assertThat(profile.sampleList).isEmpty()
    }

    private fun String.removeFirstLine():String{
        return removeRange(0, indexOfFirst { it=='\n' }+1)
    }
} 