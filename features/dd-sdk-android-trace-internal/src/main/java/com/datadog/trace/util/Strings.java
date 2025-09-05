package com.datadog.trace.util;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;

public final class Strings {

  public static String toEnvVar(String string) {
    return string.replace('.', '_').replace('-', '_').toUpperCase(Locale.US);
  }

  public static String join(CharSequence joiner, CharSequence... strings) {
    int len = strings.length;
    if (len > 0) {
      if (len == 1) {
        return strings[0].toString();
      }
      StringBuilder sb = new StringBuilder(strings[0]);
      for (int i = 1; i < len; ++i) {
        sb.append(joiner).append(strings[i]);
      }
      return sb.toString();
    }
    return "";
  }

  // reimplementation of string functions without regex
  public static String replace(String str, String delimiter, String replacement) {
    StringBuilder sb = new StringBuilder(str);
    int matchIndex, curIndex = 0;
    while ((matchIndex = sb.indexOf(delimiter, curIndex)) != -1) {
      sb.replace(matchIndex, matchIndex + delimiter.length(), replacement);
      curIndex = matchIndex + replacement.length();
    }
    return sb.toString();
  }

  public static String replaceFirst(String str, String delimiter, String replacement) {
    StringBuilder sb = new StringBuilder(str);
    int i = sb.indexOf(delimiter);
    if (i != -1) {
      sb.replace(i, i + delimiter.length(), replacement);
    }
    return sb.toString();
  }

  /**
   * Converts the property name, e.g. 'service.name' into a public environment variable name, e.g.
   * `DD_SERVICE_NAME`.
   *
   * @param setting The setting name, e.g. `service.name`
   * @return The public facing environment variable name
   */
  @NonNull
  public static String propertyNameToEnvironmentVariableName(final String setting) {
    return "DD_" + toEnvVar(setting);
  }

  /**
   * Converts the system property name, e.g. 'dd.service.name' into a public environment variable
   * name, e.g. `DD_SERVICE_NAME`.
   *
   * @param setting The system property name, e.g. `dd.service.name`
   * @return The public facing environment variable name
   */
  @NonNull
  public static String systemPropertyNameToEnvironmentVariableName(final String setting) {
    return setting.replace('.', '_').replace('-', '_').toUpperCase(Locale.US);
  }

  /**
   * Converts the property name, e.g. 'service.name' into a public system property name, e.g.
   * `dd.service.name`.
   *
   * @param setting The setting name, e.g. `service.name`
   * @return The public facing system property name
   */
  @NonNull
  public static String propertyNameToSystemPropertyName(final String setting) {
    return "dd." + setting;
  }

  @NonNull
  public static String normalizedHeaderTag(String str) {
    if (str.isEmpty()) {
      return "";
    }
    StringBuilder builder = new StringBuilder(str.length());
    int firstNonWhiteSpace = -1;
    int lastNonWhitespace = -1;
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      if (Character.isWhitespace(c)) {
        builder.append('_');
      } else {
        firstNonWhiteSpace = firstNonWhiteSpace == -1 ? i : firstNonWhiteSpace;
        lastNonWhitespace = i;
        if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '/') {
          builder.append(Character.toLowerCase(c));
        } else {
          builder.append('_');
        }
      }
    }
    if (firstNonWhiteSpace == -1) {
      return "";
    } else {
      str = builder.substring(firstNonWhiteSpace, lastNonWhitespace + 1);
      return str;
    }
  }

  @NonNull
  public static String trim(final String string) {
    return null == string ? "" : string.trim();
  }

  private static String hex(char ch) {
    return Integer.toHexString(ch).toUpperCase(Locale.US);
  }

  public static String sha256(String input) throws NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
    StringBuilder hexString = new StringBuilder(2 * hash.length);
    for (int i = 0; i < hash.length; i++) {
      String hex = Integer.toHexString(0xFF & hash[i]);
      if (hex.length() == 1) {
        hexString.append('0');
      }
      hexString.append(hex);
    }
    return hexString.toString();
  }

  /**
   * Generates a random string of the given length from lowercase characters a-z
   *
   * @param length length of the string
   * @return random string containing lowercase latin characters
   */
  public static String random(int length) {
    char[] c = new char[length];
    for (int i = 0; i < length; i++) {
      c[i] = (char) ('a' + ThreadLocalRandom.current().nextInt(26));
    }
    return new String(c);
  }
}
