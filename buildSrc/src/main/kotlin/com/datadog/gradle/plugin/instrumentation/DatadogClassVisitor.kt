/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.instrumentation

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

class DatadogClassVisitor(private val apiVersion: Int, originalVisitor: ClassVisitor) :
    ClassVisitor(apiVersion, originalVisitor) {

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        val v = super.visitMethod(access, name, descriptor, signature, exceptions)
        return if (name == "onAttach") {
            DatadogMethodVisitor(
                apiVersion,
                DatadogAttachMethodTransformer(apiVersion, v, access, name, descriptor)
            )
        } else if (name == "onDetach") {
            DatadogMethodVisitor(
                apiVersion,
                DatadogDetachMethodTransformer(apiVersion, v, access, name, descriptor)
            )
        } else {
            v
        }
    }
}