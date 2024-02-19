package datadog.trace.api.iast.propagation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import datadog.trace.api.iast.IastModule;

public interface CodecModule extends IastModule {

  void onUrlDecode(@NonNull String value, @Nullable String encoding, @NonNull String result);

  void onStringFromBytes(@NonNull byte[] value, @Nullable String charset, @NonNull String result);

  void onStringGetBytes(@NonNull String value, @Nullable String charset, @NonNull byte[] result);

  void onBase64Encode(@Nullable byte[] value, @Nullable byte[] result);

  void onBase64Decode(@Nullable byte[] value, @Nullable byte[] result);
}
