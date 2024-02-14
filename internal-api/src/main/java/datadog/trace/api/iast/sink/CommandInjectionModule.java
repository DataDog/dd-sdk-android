package datadog.trace.api.iast.sink;

import datadog.trace.api.iast.IastModule;
import java.util.List;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface CommandInjectionModule extends IastModule {

  void onRuntimeExec(@NonNull String... command);

  void onRuntimeExec(@Nullable String[] env, @NonNull String... command);

  void onProcessBuilderStart(@NonNull List<String> command);
}
