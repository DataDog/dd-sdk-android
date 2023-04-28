/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class ReflectJava {

    // Supposed to be run only for JVM
    @SuppressWarnings("NewApi")
    static void removeFinalModifierWithVarHandle(Field field) {
        try {
            var lookup = MethodHandles.privateLookupIn(Field.class, MethodHandles.lookup());
            //noinspection JavaLangInvokeHandleSignature
            var handle = lookup.findVarHandle(Field.class, "modifiers", int.class);
            handle.set(field, field.getModifiers() & ~Modifier.FINAL);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
