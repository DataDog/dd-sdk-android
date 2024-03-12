package datadog.trace.api.http;

import java.util.function.Supplier;
import androidx.annotation.NonNull;

public interface StoredBodySupplier extends Supplier<CharSequence> {
  @NonNull
  CharSequence get();
}
