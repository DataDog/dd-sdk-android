/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.compress.v3.zstd;

import io.airlift.compress.v3.IncompatibleJvmException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteOrder;

import static java.lang.String.format;

/**
 * Wrapper for sun.misc.Unsafe that uses reflection to access it at runtime.
 * This is necessary because Android SDK doesn't expose sun.misc.Unsafe at compile time,
 * but it is available at runtime.
 */
final class UnsafeUtil
{
    public static final Object UNSAFE;
    public static final long ARRAY_BYTE_BASE_OFFSET;

    // Cached method references for performance
    private static final Method getByte;
    private static final Method putByte;
    private static final Method getShort;
    private static final Method putShort;
    private static final Method getInt;
    private static final Method putInt;
    private static final Method getLong;
    private static final Method putLong;

    private UnsafeUtil() {}

    static {
        ByteOrder order = ByteOrder.nativeOrder();
        if (!order.equals(ByteOrder.LITTLE_ENDIAN)) {
            throw new IncompatibleJvmException(format("Zstandard requires a little endian platform (found %s)", order));
        }

        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = theUnsafe.get(null);

            // Get arrayBaseOffset
            Method arrayBaseOffset = unsafeClass.getMethod("arrayBaseOffset", Class.class);
            ARRAY_BYTE_BASE_OFFSET = (long) (int) arrayBaseOffset.invoke(UNSAFE, byte[].class);

            // Cache method references
            getByte = unsafeClass.getMethod("getByte", Object.class, long.class);
            putByte = unsafeClass.getMethod("putByte", Object.class, long.class, byte.class);
            getShort = unsafeClass.getMethod("getShort", Object.class, long.class);
            putShort = unsafeClass.getMethod("putShort", Object.class, long.class, short.class);
            getInt = unsafeClass.getMethod("getInt", Object.class, long.class);
            putInt = unsafeClass.getMethod("putInt", Object.class, long.class, int.class);
            getLong = unsafeClass.getMethod("getLong", Object.class, long.class);
            putLong = unsafeClass.getMethod("putLong", Object.class, long.class, long.class);
        }
        catch (Exception e) {
            throw new IncompatibleJvmException("Zstandard requires access to sun.misc.Unsafe: " + e.getMessage());
        }
    }

    public static byte getByte(Object base, long offset)
    {
        try {
            return (byte) getByte.invoke(UNSAFE, base, offset);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void putByte(Object base, long offset, byte value)
    {
        try {
            putByte.invoke(UNSAFE, base, offset, value);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static short getShort(Object base, long offset)
    {
        try {
            return (short) getShort.invoke(UNSAFE, base, offset);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void putShort(Object base, long offset, short value)
    {
        try {
            putShort.invoke(UNSAFE, base, offset, value);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static int getInt(Object base, long offset)
    {
        try {
            return (int) getInt.invoke(UNSAFE, base, offset);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void putInt(Object base, long offset, int value)
    {
        try {
            putInt.invoke(UNSAFE, base, offset, value);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static long getLong(Object base, long offset)
    {
        try {
            return (long) getLong.invoke(UNSAFE, base, offset);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void putLong(Object base, long offset, long value)
    {
        try {
            putLong.invoke(UNSAFE, base, offset, value);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Copy memory between byte arrays.
     * Android's Unsafe doesn't have the 5-parameter copyMemory method,
     * so we use System.arraycopy instead.
     */
    public static void copyMemory(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes)
    {
        if (srcBase instanceof byte[] && destBase instanceof byte[]) {
            byte[] src = (byte[]) srcBase;
            byte[] dest = (byte[]) destBase;
            int srcIndex = (int) (srcOffset - ARRAY_BYTE_BASE_OFFSET);
            int destIndex = (int) (destOffset - ARRAY_BYTE_BASE_OFFSET);
            System.arraycopy(src, srcIndex, dest, destIndex, (int) bytes);
        }
        else {
            throw new UnsupportedOperationException("copyMemory only supports byte arrays on Android");
        }
    }
}
