package com.datadog.trace.bootstrap.instrumentation.api;

import com.datadog.trace.logger.Logger;
import com.datadog.trace.logger.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import com.datadog.android.trace.internal.compat.function.Supplier;

public class URIUtils {
  private URIUtils() {}

  // This is the � character, which is also the default replacement for the UTF_8 charset
  private static final byte[] REPLACEMENT = {(byte) 0xEF, (byte) 0xBF, (byte) 0xBD};

  private static final Logger LOGGER = LoggerFactory.getLogger(URIUtils.class);

  /**
   * Decodes a %-encoded UTF-8 {@code String} into a regular {@code String}.
   *
   * <p>All illegal % sequences and illegal UTF-8 sequences are replaced with one or more �
   * characters.
   */
  public static String decode(String encoded) {
    return decode(encoded, false);
  }

  /**
   * Decodes a %-encoded UTF-8 {@code String} into a regular {@code String}. Can also be made to
   * decode '+' to ' ' to support old query strings.
   *
   * <p>All illegal % sequences and illegal UTF-8 sequences are replaced with one or more �
   * characters.
   */
  public static String decode(String encoded, boolean plusToSpace) {
    if (encoded == null) return null;
    int len = encoded.length();
    if (len == 0) return encoded;
    if (encoded.indexOf('%') < 0 && (!plusToSpace || encoded.indexOf('+') < 0)) return encoded;

    ByteBuffer bb =
        ByteBuffer.allocate(len + 2); // The extra 2 is if we have a % last and need to replace it
    for (int i = 0; i < len; i++) {
      int c = encoded.charAt(i);
      if (c == '%') {
        if (i + 2 < len) {
          int h = Character.digit(encoded.charAt(i + 1), 16);
          int l = Character.digit(encoded.charAt(i + 2), 16);
          if ((h | l) < 0) {
            bb.put(REPLACEMENT[0]);
            bb.put(REPLACEMENT[1]);
            bb.put(REPLACEMENT[2]);
          } else {
            bb.put((byte) ((h << 4) + l));
          }
          i += 2;
        } else {
          bb.put(REPLACEMENT[0]);
          bb.put(REPLACEMENT[1]);
          bb.put(REPLACEMENT[2]);
          i = len;
        }
      } else {
        if (plusToSpace && c == '+') {
          c = ' ';
        }
        bb.put((byte) c);
      }
    }
    bb.flip();
    return new String(bb.array(), 0, bb.limit(), StandardCharsets.UTF_8);
  }

  public static URI safeParse(final String unparsed) {
    if (unparsed == null) {
      return null;
    }
    try {
      return URI.create(unparsed);
    } catch (final IllegalArgumentException exception) {
      LOGGER.debug("Unable to parse request uri {}", unparsed, exception);
      return null;
    }
  }

  /**
   * A lazily evaluated URL that can also return its path. If the URL is invalid the path will be
   * {@code null}.
   */
  public abstract static class LazyUrl implements CharSequence, Supplier<String> {
    protected String lazy;

    protected LazyUrl(String lazy) {
      this.lazy = lazy;
    }

    /**
     * The path component of this URL.
     *
     * @return The path if valid or {@code null} if invalid
     */
    public abstract String path();

    @Override
    public String toString() {
      String str = lazy;
      if (str == null) {
        str = lazy = get();
      }
      return str;
    }

    @Override
    public int length() {
      return toString().length();
    }

    @Override
    public char charAt(int index) {
      return toString().charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      return toString().subSequence(start, end);
    }

    @Override
    public int hashCode() {
      return toString().hashCode();
    }
  }

}
