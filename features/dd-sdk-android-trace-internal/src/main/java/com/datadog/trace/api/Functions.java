package com.datadog.trace.api;

import com.datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;

import java.util.Locale;
import com.datadog.android.trace.internal.compat.function.Function;

public final class Functions {

  private Functions() {}

  public static final Function<String, UTF8BytesString> UTF8_ENCODE = UTF8BytesString::create;

  public static final class LowerCase implements Function<String, String> {

    public static final LowerCase INSTANCE = new LowerCase();

    @Override
    public String apply(String key) {
      return key.toLowerCase(Locale.ROOT);
    }
  }

}
