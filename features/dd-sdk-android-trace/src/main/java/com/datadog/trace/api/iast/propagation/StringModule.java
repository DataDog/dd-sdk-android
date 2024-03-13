package com.datadog.trace.api.iast.propagation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.datadog.trace.api.iast.IastModule;

import java.util.Locale;

public interface StringModule extends IastModule {

  void onStringConcat(@NonNull String left, @Nullable String right, @NonNull String result);

  void onStringBuilderInit(@NonNull CharSequence builder, @Nullable CharSequence param);

  void onStringBuilderAppend(@NonNull CharSequence builder, @Nullable CharSequence param);

  void onStringBuilderToString(@NonNull CharSequence builder, @NonNull String result);

  void onStringConcatFactory(
      @Nullable String result,
      @Nullable String[] args,
      @Nullable String recipe,
      @Nullable Object[] dynamicConstants,
      @NonNull int[] recipeOffsets);

  void onStringSubSequence(
      @NonNull String self, int beginIndex, int endIndex, @Nullable CharSequence result);

  void onStringJoin(
      @Nullable String result, @NonNull CharSequence delimiter, @NonNull CharSequence[] elements);

  void onStringToUpperCase(@NonNull String self, @Nullable String result);

  void onStringToLowerCase(@NonNull String self, @Nullable String result);

  void onStringTrim(@NonNull String self, @Nullable String result);

  void onStringRepeat(@NonNull String self, int count, @NonNull String result);

  void onStringConstructor(@NonNull String self, @NonNull String result);

  void onStringFormat(@NonNull String pattern, @NonNull Object[] params, @NonNull String result);

  void onStringFormat(
      @Nullable Locale locale,
      @NonNull String pattern,
      @NonNull Object[] params,
      @NonNull String result);

  void onStringFormat(
      @NonNull Iterable<String> literals, @NonNull Object[] params, @NonNull String result);

  void onSplit(final @NonNull String self, final @NonNull String[] result);
}
