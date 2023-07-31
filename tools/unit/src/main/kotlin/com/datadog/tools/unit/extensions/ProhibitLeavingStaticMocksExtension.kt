/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.extensions

import com.datadog.tools.unit.annotations.ProhibitLeavingStaticMocksIn
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.mockito.internal.util.MockUtil
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.LinkedList
import java.util.logging.Logger
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.jvmName

/**
 * Extension which allows to see mocks left in static fields after test run.
 * Check [ProhibitLeavingStaticMocksIn] for more details.
 *
 * **NOTE**: JUnit extensions are executed in the declaration order for beforeXXX callbacks, but
 * in the reversed order for afterXXX callbacks. So since this extension relies on the afterEach,
 * it should be declared before TestConfigurationExtension, so that classes referenced
 * by TestConfiguration could be reset before running this extension.
 */
class ProhibitLeavingStaticMocksExtension : AfterEachCallback {

    private val logger = Logger.getLogger(ProhibitLeavingStaticMocksExtension::class.jvmName)

    override fun afterEach(context: ExtensionContext) {
        val annotationOnMethod =
            context.requiredTestMethod
                .getAnnotation(ProhibitLeavingStaticMocksIn::class.java)
        val annotationOnClass =
            context.requiredTestClass
                .getAnnotation(ProhibitLeavingStaticMocksIn::class.java)

        val scanRoots = mutableListOf<KClass<*>>().apply {
            addAll(annotationOnMethod?.value ?: emptyArray())
            addAll(annotationOnClass?.value ?: emptyArray())
        }

        val packagePrefixes = mutableListOf<String>().apply {
            addAll(annotationOnMethod?.packagePrefixes ?: emptyArray())
            addAll(annotationOnClass?.packagePrefixes ?: emptyArray())
        }

        scanForStaticMocksLeft(scanRoots, packagePrefixes)
    }

    internal fun scanForStaticMocksLeft(
        roots: List<KClass<*>>,
        packagePrefixes: List<String>
    ) {
        val visited = mutableSetOf<KClass<*>>()

        val queue = LinkedList<VisitEntry>()
        queue.addAll(roots.map { VisitEntry(it, emptyList()) })

        while (!queue.isEmpty()) {
            val visitEntry = queue.pollFirst() ?: continue

            val visitedClass = visitEntry.clazz
            visited.add(visitedClass)

            val watchFields = findFieldsToWatch(visitedClass)

            for (fieldInstanceSpec in watchFields) {
                val value = fieldInstanceSpec.fieldValue ?: continue

                if (MockUtil.isMock(value)) {
                    reportViolation(
                        fieldInstanceSpec.fieldDescriptor,
                        fieldInstanceSpec.hostClass,
                        visitEntry.pathUntil,
                        fieldInstanceSpec.isLateinit
                    )
                }
                if (packagePrefixes.any { value::class.java.name.startsWith(it) } &&
                    !visited.contains(value::class.java.kotlin)
                ) {
                    val nextEntry = VisitEntry(
                        value::class,
                        pathUntil = visitEntry.pathUntil + CallStackEntry(
                            fieldInstanceSpec.hostClass,
                            fieldInstanceSpec.fieldDescriptor
                        )
                    )
                    queue.add(nextEntry)
                }
            }
        }
    }

    private fun findFieldsToWatch(visitedClass: KClass<*>): List<FieldCallSpec> {
        val kotlinObjectFields = if (visitedClass.simpleName != null &&
            visitedClass.objectInstance != null
        ) {
            findTargetFieldsInKotlin(
                visitedClass,
                visitedClass.objectInstance
            )
        } else {
            emptyList()
        }

        val kotlinCompanionObjectFields = if (visitedClass.simpleName != null &&
            visitedClass.companionObjectInstance != null
        ) {
            findTargetFieldsInKotlin(
                visitedClass.companionObject,
                visitedClass.companionObjectInstance
            )
        } else {
            emptyList()
        }

        val plainStaticFields =
            if (kotlinObjectFields.isEmpty() && kotlinCompanionObjectFields.isEmpty()) {
                findTargetPlainStaticFields(visitedClass.java)
            } else {
                emptyList()
            }

        return mutableListOf<FieldCallSpec>().apply {
            addAll(kotlinObjectFields)
            addAll(kotlinCompanionObjectFields)
            addAll(plainStaticFields)
        }
    }

    @Suppress("ThrowingInternalException") // not an issue in unit tests
    private fun reportViolation(
        fieldDescriptor: FieldDescriptor,
        hostClass: KClass<*>,
        pathUntil: List<CallStackEntry>,
        isLateinit: Boolean
    ) {
        var message = "Unexpected mock remaining in the field" +
            " ${fieldDescriptor.fieldName} of ${hostClass.jvmName}."

        if (pathUntil.isNotEmpty()) {
            val callingSequence = pathUntil.reversed()
                .joinToString(separator = "\n") {
                    "${it.hostClass.jvmName}.${it.fieldDescriptor.fieldName}"
                }
            message += "\nCalling sequence:\n$callingSequence"
        }

        if (isLateinit) {
            logger.info(message)
        } else {
            throw UnwantedStaticMockException(message)
        }
    }

    private fun findTargetFieldsInKotlin(
        clazz: KClass<*>?,
        hostInstance: Any?
    ): List<FieldCallSpec> {
        if (clazz == null) return emptyList()
        return clazz.memberProperties
            .filterNot { it.isConst }
            .map {
                FieldCallSpec(FieldDescriptor.Kotlin(it), it.isLateinit, hostInstance, clazz)
            }
    }

    private fun findTargetPlainStaticFields(
        clazz: Class<*>
    ): List<FieldCallSpec> {
        return clazz.declaredFields
            .filter {
                Modifier.isStatic(it.modifiers) && !Modifier.isFinal(it.modifiers)
            }
            .map {
                FieldCallSpec(FieldDescriptor.Java(it), false, null, clazz.kotlin)
            }
    }

    private data class FieldCallSpec(
        val fieldDescriptor: FieldDescriptor,
        val isLateinit: Boolean,
        val hostInstance: Any?,
        val hostClass: KClass<*>
    ) {
        val fieldValue: Any?
            get() {
                return when (fieldDescriptor) {
                    is FieldDescriptor.Java -> {
                        fieldDescriptor.field.isAccessible = true
                        fieldDescriptor.field.get(hostInstance)
                    }
                    is FieldDescriptor.Kotlin -> {
                        val property = fieldDescriptor.property
                        property.isAccessible = true
                        // this place is extremely fragile: for lateinit we cannot call getter
                        // if property is not initialized - we get an error. Also some properties
                        // don't require instance to get the value and it is not clear how to
                        // understand that, thing with javaGetter check is just a quick
                        // solution (but probably not the proper one).
                        if (isLateinit && property.javaField?.get(hostInstance) == null) {
                            null
                        } else {
                            if (property.getter.instanceParameter == null) {
                                property.getter.call()
                            } else {
                                if (!hostClass.isCompanion && property.javaGetter == null) {
                                    property.getter.call()
                                } else {
                                    property.getter.call(hostInstance)
                                }
                            }
                        }
                    }
                }
            }
    }

    private sealed class FieldDescriptor {
        data class Java(val field: Field) : FieldDescriptor()
        data class Kotlin(val property: KProperty1<*, *>) : FieldDescriptor()

        val fieldName: String
            get() {
                return when (this) {
                    is Java -> this.field.name
                    is Kotlin -> property.name
                }
            }
    }

    private data class CallStackEntry(
        val hostClass: KClass<*>,
        val fieldDescriptor: FieldDescriptor
    )

    private data class VisitEntry(val clazz: KClass<*>, val pathUntil: List<CallStackEntry>)
}

/**
 * Exception thrown by [ProhibitLeavingStaticMocksExtension] if any mocks found in the static
 * fields after test run.
 */
class UnwantedStaticMockException(message: String) : IllegalStateException(message)
