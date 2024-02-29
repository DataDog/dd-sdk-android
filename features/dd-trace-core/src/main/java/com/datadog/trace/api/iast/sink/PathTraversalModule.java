package com.datadog.trace.api.iast.sink;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.net.URI;

import com.datadog.trace.api.iast.IastModule;

public interface PathTraversalModule extends IastModule {

  void onPathTraversal(@NonNull String path);

  void onPathTraversal(@Nullable String parent, @NonNull String child);

  void onPathTraversal(@NonNull String first, @NonNull String[] more);

  void onPathTraversal(@NonNull URI uri);

  void onPathTraversal(@Nullable File parent, @NonNull String child);
}
