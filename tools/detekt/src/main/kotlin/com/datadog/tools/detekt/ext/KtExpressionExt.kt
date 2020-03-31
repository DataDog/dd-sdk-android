/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.ext

import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.isPublic
import org.jetbrains.kotlin.util.containingNonLocalDeclaration

internal fun KtExpression.isContainingClassPublic(): Boolean {
    return containingClass().let { ktClass ->
        ktClass == null || ktClass.isPublic
    }
}

internal fun KtExpression.isContainingDeclarationPublic(): Boolean {
    return containingNonLocalDeclaration().let { ktDeclaration ->
        ktDeclaration == null || ktDeclaration.isPublic
    }
}

internal fun KtExpression.isContainingEntryPointPublic(): Boolean {
    return isContainingClassPublic() && isContainingDeclarationPublic()
}
