/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit;

import android.annotation.SuppressLint;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

public class FieldWriter {

    // Supposed to be run only for JVM.
    // Since JDK 18 the `modifiers` field of `Field` can't be changed anymore, so static final
    // fields are written through `sun.misc.Unsafe`. It is accessed by reflection, because it is
    // not part of android.jar. Other fields can be set directly once the field is accessible.
    static void set(Object instance, Field field, Object value) throws IllegalAccessException {
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
            setStaticFinal(field, value);
        } else {
            field.set(instance, value);
        }
    }

    private static void setStaticFinal(Field field, Object value) throws IllegalAccessException {
        if (field.getType().isPrimitive()) {
            throw new IllegalArgumentException("Primitive static final fields are not supported: " + field);
        }
        // Unsafe doesn't check the type, unlike Field.set
        if (value != null && !field.getType().isInstance(value)) {
            throw new IllegalArgumentException("Can't set " + field + " to " + value.getClass());
        }
        // Make sure the class is initialized, otherwise its static initializer would override the value
        field.get(null);
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            @SuppressLint("DiscouragedPrivateApi")
            Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            Object base = unsafeClass.getMethod("staticFieldBase", Field.class).invoke(unsafe, field);
            //noinspection DataFlowIssue
            long offset = (long) unsafeClass.getMethod("staticFieldOffset", Field.class).invoke(unsafe, field);
            unsafeClass.getMethod("putObjectVolatile", Object.class, long.class, Object.class)
                    .invoke(unsafe, base, offset, value);
        } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException
                 | InvocationTargetException e) {
            throw new IllegalStateException("Unable to set static final field " + field, e);
        }
    }
}
