/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.instrumentation

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.Method

class DatadogAttachMethodTransformer(
    val api: Int, val original: MethodVisitor,
    access: Int, name: String, descriptor: String
) :
    GeneratorAdapter(api, original, access, name, descriptor) {

    override fun visitCode() {
        super.visitCode()
        val nodeHashCodeIndex = 2
        val nodeParentHashCodeIndex=3
        val nodeParenIndex=4
        val composeNodeIndex=5
        original.visitVarInsn(Opcodes.ALOAD, 1)
        invokeVirtual(LAYOUT_NODE_TYPE, Method.getMethod("int hashCode()"))
        original.visitVarInsn(Opcodes.ISTORE, nodeHashCodeIndex)
        original.visitInsn(Opcodes.ICONST_0)
        original.visitVarInsn(Opcodes.ISTORE, nodeParentHashCodeIndex)
        original.visitVarInsn(Opcodes.ILOAD, nodeHashCodeIndex)
        val ifLabel = Label()
        original.visitVarInsn(Opcodes.ALOAD, 1)
        invokeVirtual(LAYOUT_NODE_TYPE, Method.getMethod("androidx.compose.ui.layout.LayoutInfo getParentInfo()"))
        original.visitVarInsn(Opcodes.ASTORE, nodeParenIndex)
        original.visitVarInsn(Opcodes.ALOAD, nodeParenIndex)
        ifNull(ifLabel)
        original.visitVarInsn(Opcodes.ALOAD, nodeParenIndex)
        invokeInterface(LAYOUT_INFO_TYPE, Method.getMethod("int hashCode()"))
        original.visitVarInsn(Opcodes.ISTORE, nodeParentHashCodeIndex)
        visitLabel(ifLabel)
        newInstance(COMPOSE_NODE_TYPE)
        dup()
        original.visitVarInsn(Opcodes.ILOAD, nodeHashCodeIndex)
        original.visitVarInsn(Opcodes.ILOAD, nodeParentHashCodeIndex)
        original.visitVarInsn(Opcodes.ALOAD, 1)
        checkCast(MEASURABLE_TYPE)
        original.visitVarInsn(Opcodes.ALOAD, 1)
        checkCast(REMEASUREMENT_TYPE)
        original.visitVarInsn(Opcodes.ALOAD, 1)
        checkCast(LAYOUT_INFO_TYPE)
        invokeConstructor(
            COMPOSE_NODE_TYPE,
            Method.getMethod("void <init> (int, int, androidx.compose.ui.layout.Measurable, androidx.compose.ui.layout.Remeasurement, androidx.compose.ui.layout.LayoutInfo)")
        )
        original.visitVarInsn(Opcodes.ASTORE, composeNodeIndex)
        loadThis()
        checkCast(VIEW_TYPE)
        original.visitVarInsn(Opcodes.ALOAD, composeNodeIndex)
        invokeStatic(
            SESSION_REPLAY_TYPE,
            Method.getMethod("void addNode (android.view.View, com.datadog.android.sessionreplay.ComposeNode)")
        )
    }

    companion object {
        const val INT_CLASSNAME = "Ljava/lang/Integer;"
        val INT_TYPE = Type.getType(INT_CLASSNAME)
        const val LAYOUT_NODE_CLASSNAME = "Landroidx/compose/ui/node/LayoutNode;"
        val LAYOUT_NODE_TYPE = Type.getType(LAYOUT_NODE_CLASSNAME)
        val VIEW_TYPE = Type.getType("Landroid/view/View;")
        const val SESSION_REPLAY_CLASSNAME = "Lcom/datadog/android/sessionreplay/SessionReplay;"
        const val COMPOSE_NODE_CLASSNAME = "Lcom/datadog/android/sessionreplay/ComposeNode;"
        val SESSION_REPLAY_TYPE = Type.getType(SESSION_REPLAY_CLASSNAME)
        val COMPOSE_NODE_TYPE = Type.getType(COMPOSE_NODE_CLASSNAME)
        val MODIFIER_CLASS_NAME = "Landroidx/compose/ui/Modifier;"
        val MODIFIER_TYPE = Type.getType(MODIFIER_CLASS_NAME)
        val LAYOUT_INFO_CLASSNAME = "Landroidx/compose/ui/layout/LayoutInfo;"
        val LAYOUT_INFO_TYPE = Type.getType(LAYOUT_INFO_CLASSNAME)
        val MEASURABLE_CLASSNAME = "Landroidx/compose/ui/layout/Measurable;"
        val MEASURABLE_TYPE = Type.getType(MEASURABLE_CLASSNAME)
        val REMEASUREMENT_CLASSNAME = "Landroidx/compose/ui/layout/Remeasurement;"
        val REMEASUREMENT_TYPE = Type.getType(REMEASUREMENT_CLASSNAME)
    }
}