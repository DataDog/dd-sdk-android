package datadog.trace.api.iast.sink;

import datadog.trace.api.iast.IastModule;
import java.io.File;
import java.net.URI;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface PathTraversalModule extends IastModule {

  void onPathTraversal(@NonNull String path);

  void onPathTraversal(@Nullable String parent, @NonNull String child);

  void onPathTraversal(@NonNull String first, @NonNull String[] more);

  void onPathTraversal(@NonNull URI uri);

  void onPathTraversal(@Nullable File parent, @NonNull String child);
}
