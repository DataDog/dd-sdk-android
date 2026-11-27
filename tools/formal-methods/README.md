# Async protocol diagnostics

This opt-in suite adapts the formal-methods experiment from
`DataDog/dd-trace-dotnet#9575` to asynchronous ownership boundaries in the
Android SDK. It is diagnostic evidence, not a production fix.

The current draft records eleven candidate behaviors. Each has two layers:

1. a small TLA+ model whose expected result is a named invariant violation;
2. a JUnit replay against the current Kotlin implementation.

The tests intentionally pass when they reproduce the recorded behavior. They
must not be interpreted as regression tests for desired behavior.

Start with [REVIEW-GUIDE.md](REVIEW-GUIDE.md), then use [FINDINGS.md](FINDINGS.md)
for the implementation evidence and contract questions.

## Run the implementation replays

```bash
./gradlew \
  :dd-sdk-android-core:testDebugUnitTest \
  :features:dd-sdk-android-flags:testDebugUnitTest \
  --tests '*FormalMethods*'
```

## Run the models

Download a `tla2tools.jar` release from the TLA+ project, then run:

```bash
tools/formal-methods/run-models.sh /path/to/tla2tools.jar
```

The runner succeeds only when every configuration produces its expected named
counterexample. An unexpected model pass is a failure because it means the
recorded implementation order and the model no longer agree.
