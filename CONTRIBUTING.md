# Contributing

First of all, thanks for contributing!

This document provides some basic guidelines for contributing to this repository.
To propose improvements, feel free to submit a PR or open an Issue.

## Setup your developer Environment

To setup your environment, make sure you installed [Android Studio](https://developer.android.com/studio).

**Note**: you can also compile and develop using only the Android SDK and your IDE of choice, e.g.: IntelliJ Idea, Vim, etc.

In addition, to be able to run the static analysis tools locally, you should run the `local-ci.sh` script locally as follow.

```shell
./local_ci.sh --setup
```

### Modules

This project hosts the following modules:

  - `dd-sdk-android-core`: the main library implementing the core functionality of SDK (storage and upload of data, core APIs);
  - `features/***`: a set of libraries implementing Datadog products: 
    - `features/dd-sdk-android-logs`: a library to send logs to Datadog;
    - `features/dd-sdk-android-rum`: a library to track user navigation and interaction;
    - `features/dd-sdk-android-ndk`: a lightweight library to track crashes from NDK libraries;
    - `features/dd-sdk-android-session-replay`: a library to capture the window content;
    - `features/dd-sdk-android-session-replay-material`: an extension for Session Replay to integrate with the Material Design library;
    - `features/dd-sdk-android-session-trace`: a library to measure performance of operations locally;
    - `features/dd-sdk-android-session-webview`: a library to forward logs and RUM events captured in a webview to be linked with the mobile session;
  - `integrations/***`: a set of libraries integrating Datadog products in third party libraries:
    - `integrations/dd-sdk-android-coil`: a lightweight library providing a bridge integration between Datadog SDK and [Coil](https://coil-kt.github.io/coil/);
    - `integrations/dd-sdk-android-compose`: a lightweight library providing a bridge integration between Datadog SDK and [Jetpack Compose](https://developer.android.com/jetpack/compose);
    - `integrations/dd-sdk-android-fresco`: a lightweight library providing a bridge integration between Datadog SDK and [Fresco](https://frescolib.org/);
    - `integrations/dd-sdk-android-okhttp`: a lightweight library providing an instrumentation for [OkHttp](https://square.github.io/okhttp/);
    - `integrations/dd-sdk-android-rx`: a lightweight library providing a bridge integration between Datadog SDK and [RxJava](https://github.com/ReactiveX/RxJava);
    - `integrations/dd-sdk-android-sqldelight`: a lightweight library providing a bridge integration between Datadog SDK and [SQLDelight](https://cashapp.github.io/sqldelight/);
    - `integrations/dd-sdk-android-tv`: a lightweight library providing extensions for [Android TV](https://www.android.com/tv/)
    - `integrations/dd-sdk-android-ktx`: a set of Kotlin extensions to make the Datadog SDK more Kotlin friendly;
    - `integrations/dd-sdk-android-glide`: a lightweight library providing a bridge integration between Datadog SDK and [Glide](https://bumptech.github.io/glide/);
    - `integrations/dd-sdk-android-timber`: a lightweight library providing a bridge integration between Datadog SDK and [Timber](https://github.com/JakeWharton/timber);
  - `instrumented/***`: a set of modules used to run instrumented tests:
    - `instrumented/integration`: a test module with integration tests using Espresso;
    - `instrumented/nightly-tests`: a test module with E2E tests using Espresso;
  - `tools/*`: a set of modules used to extend the tools we use in our workflow:
    - `tools/detekt`: a few custom [Detekt](https://github.com/arturbosch/detekt) static analysis rules;
    - `tools/lint`: a custom [Lint](https://developer.android.com/studio/write/lint) static analysis rule;
    - `tools/noopfactory`: an annotation processor generating no-op implementation of interfaces;
    - `tools/unit`: a utility library with code to help writing unit tests;
  - `sample/***`: a few sample applications showcasing how to use the library features in production code;
    - `sample/kotlin`: a sample mobile application;
    - `sample/vendor-lib`: a sample android library, to showcase vendors using Datadog in a host app also using Datadog;
    - `sample/wear`: a sample watch application;

### Building the SDK

You can build the SDK using the following Gradle command:

```shell script
./gradlew assembleAll
```

### Running the tests

The whole project is covered by a set of static analysis tools, linters and tests. To mimic the steps taken by our CI, you can run the `local_ci.sh` script:

```shell script
# cleans the repo
./local_ci.sh --clean

# runs the static analysis
./local_ci.sh --analysis

# compiles all the different library modules and tools
./local_ci.sh --compile

# Runs the unit tests
./local_ci.sh --test

# Update session replay payloads
./local_ci.sh --update-session-replay-payloads
```

## Submitting Issues

Many great ideas for new features come from the community, and we'd be happy to
consider yours!

To share your request, you can open an [issue](https://github.com/DataDog/dd-sdk-android/issues/new?labels=enhancement&template=feature_request.md) 
with the details about what you'd like to see. At a minimum, please provide:

 - The goal of the new feature;
 - A description of how it might be used or behave;
 - Links to any important resources (e.g. Github repos, websites, screenshots,
     specifications, diagrams).

## Found a bug?

For any urgent matters (such as outages) or issues concerning the Datadog service
or UI, contact our support team via https://docs.datadoghq.com/help/ for direct,
faster assistance.

You may submit bug reports concerning the Datadog SDK for Android by 
[opening a Github issue](https://github.com/DataDog/dd-sdk-android/issues/new?labels=bug&template=bug_report.md).
At a minimum, please provide:

 - A description of the problem;
 - Steps to reproduce;
 - Expected behavior;
 - Actual behavior;
 - Errors (with stack traces) or warnings received;
 - Any details you can share about your configuration including:
    - Android API level;
    - Datadog SDK version;
    - Versions of any other relevant dependencies (OkHttp, …);
    - Your proguard configuration;
    - The list of Gradle plugins applied to your project.

If at all possible, also provide:

 - Logs (from the tracer/application/agent) or other diagnostics;
 - Screenshots, links, or other visual aids that are publicly accessible;
 - Code sample or test that reproduces the problem;
 - An explanation of what causes the bug and/or how it can be fixed.

Reports that include rich detail are better, and ones with code that reproduce
the bug are best.

## Have a patch?

We welcome code contributions to the library, which you can 
[submit as a pull request](https://github.com/DataDog/dd-sdk-android/pull/new/develop).
Before you submit a PR, make sure that you first create an Issue to explain the
bug or the feature your patch covers, and make sure another Issue or PR doesn't
already exist.

To create a pull request:

1. **Fork the repository** from https://github.com/DataDog/dd-sdk-android ;
2. **Make any changes** for your patch;
3. **Write tests** that demonstrate how the feature works or how the bug is fixed;
4. **Update any documentation**, especially for new features. It can be found either in the `docs` folder of this repository, or in [documentation repository](https://github.com/DataDog/documentation);
5. **Submit the pull request** from your fork back to this [repository](https://github.com/DataDog/dd-sdk-android).


The pull request will be run through our CI pipeline, and a project member will
review the changes with you. At a minimum, to be accepted and merged, pull
requests must:

 - Have a stated goal and detailed description of the changes made;
 - Include thorough test coverage and documentation, where applicable;
 - Pass all tests and code quality checks (linting/coverage/benchmarks) on CI;
 - Receive at least one approval from a project member with push permissions.

Make sure that your code is clean and readable, that your commits are small and
atomic, with a proper commit message. We tend to use 
[gitmoji](https://gitmoji.carloscuesta.me/), but this is not mandatory.

## Coding Conventions

Our repository uses Kotlin, as it is now the recommended language for Android.
But because this library can still be used by Java based application, make sure
any change you introduce are still compatible with Java. If you want to add
Kotlin specific features (DSL, lambdas, …), make sure there is a way to get the
same feature from a Java source code.

### Code quality

Our code uses [Detekt](https://detekt.dev/) static analysis with a shared configuration, slightly
stricter than the default one. A Detekt check is ran on every on every PR to ensure that all new code
follow this rule.
Current Detekt version: 1.22.0

### Code style

Our coding style is ensured by [KtLint](https://ktlint.github.io/), with the
default settings. A KtLint check is ran on every PR to ensure that all new code
follow this rule.
Current KtLint version: 0.47.1

Classes should group their methods in folding regions named after the declaring
class. Private methods should be grouped in an `Internal` named folding region. 
For example, a class inheriting from `Runnable` and `Observable` should use the
following regions.

```kotlin

class Foo : Observable(), Runnable {
    
    // region Foo
    
    fun fooSpecificMethod(){}
    
    // endregion
    
    // region Observable

    override fun addObserver(o: Observer?) {
        super.addObserver(o)
        doSomething()
    }

    // endregion

    // region Runnable

    override fun run() {}

    // endregion
    
    // region Internal
    
    private fun doSomething() {}
    
    // endregion
}

```

There is also a command that you can use to automatically format the code following the
required styling rules (require `ktlint` installed on your machine):

```console
ktlint -F "**/*.kt" "**/*.kts" '!**/build/generated/**' '!**/build/kspCaches/**'
```

### #TestMatters

It is important to be sure that our library work properly in any scenario. All
non trivial code must be tested. If you're not used to writing tests, you can
take a look at the `test` folder to get some ideas on how we write them at Datadog.

We use a variety of tools to help us write tests easy to read and maintain:

 - [JUnit5 Jupiter](https://junit.org/junit5/): the test runner, quite similar to
     JUnit4;
 - [Mockito](https://site.mockito.org/): a mocking framework to decouple concerns
     in the Unit Tests;
 - [AssertJ](https://assertj.github.io/doc/): a framework to write fluent
     assertions;
 - [Elmyr](https://github.com/xgouchet/Elmyr): a framework to generate fake data
     in the Unit Tests.

### Test Conventions

In order to make the test classes more readable, here are a set of naming conventions and coding style.

#### Classes

The accepted convention is to use the name of the class under test, with the suffix Test.
E.g.: the test class corresponding to the class `Foo` must be named `FooTest`.

Some classes need to be created in the `test` sourceSets to integrate with our testing tools 
(AssertJ, Elmyr, …). Those classes must be placed in a package named 
`{module_package}.tests.{test_library}`, and be named by combining the base class name and 
the new class purpose. 

E.g.:
 - A custom assertion class for class `Foo` in module `com.datadog.module` will be 
    `com.datadog.module.tests.assertj.FooAssert`
- A custom forgery factory class for class `Foo` in module `com.datadog.module` will be
    `com.datadog.module.tests.elmyr.FooForgeryFactory`

#### Fields & Test Method parameters

Fields should appear in the following order, and be named as explained by these rules:

- The object(s) under test must be named from their class, and prefixed by `tested`. 
    E.g.: `testedListener: Listener`, `testedHandler: Handler`.
- Stubbed objects (mocks with predefined behavior) must be named from their class (with an optional qualifier), and prefixed by `stub`.
    E.g.: `stubDataProvider: DataProvider`, `stubReader: Reader`.
- Mocked objects (mocks being verified) must be named from their class (with an optional qualifier), and prefixed by `mock`. 
    E.g.: `mockListener: Listener`, `mockLogger: Logger`.
- Fixtures (data classes or primitives with no behavior) must be named from their class (with an optional qualifier), and prefixed by `fake`.
    E.g.: `fakeContext: Context`, `fakeApplicationId: UUID`, `fakeRequest: NetworkRequest`.
- Other fields can be named on case by case basis, but a few rules can still apply:
    - If the field is annotated by a JUnit 5 extension (e.g.: `@TempDir`), then it should be named after the extension (e.g.: `tempOutputDir`).
    
#### Test Methods

Test methods must follow the Given-When-Then principle, that is they must all consist of three steps: 

- Given (optional): sets up the instance under test to be in the correct state;
- When (optional): performs an action — directly or indirectly — on the instance under test;
- Then (mandatory): performs any number of assertions on the instance under test’s state, the mocks or output values. It must perform at least one assertion. 

If present, these steps will always be intruded by one line comments, i.e.: `// Given`, `// When`, `// Then`.

Based on this principle, the test name should reflect the intent, and use the following pattern: `MUST expected behavior WHEN method() GIVEN context`. 
To avoid being too verbose, `MUST` will be written `M`, and `WHEN` will be written `W`. The `context` part should be concise, and wrapped in curly braces to avoid duplicate names 
(e.g.: `M create a span with info W intercept() {statusCode=5xx}`)

Parameters shall have simple local names reflecting their intent (see above), whether they use an `@Forgery` or `@Mock` annotation (or none).

Here's a test method following those conventions: 

```kotlin
    @Test
    fun `M forward boolean attribute to handler W addAttribute()`(
        @StringForgery(StringForgeryType.ALPHABETICAL) fakeMessage : String,
        @StringForgery(StringForgeryType.ALPHABETICAL) fakeKey : String,
        @BoolForgery value : Boolean,
        @Mock mockLogHandler: InternalLogger
    ) {
        // Given
        testedLogger = Logger(mockLogHandler)
    
        // When
        testedLogger.addAttribute(key, value)
        testedLogger.v(fakeMessage)

        // Then
        verify(mockLogHandler)
            .handleLog(
                Log.VERBOSE,
                fakeMessage,
                null,
                mapOf(key to value),
                emptySet()
            )
    }
```

#### Test Utility Methods

Because we sometimes need to reuse some setup or assertions in our tests, we tend to write utility methods. 
Those methods should be private (or internal in a dedicated class/file if they need to be shared across tests).

- `fun stubSomething(mock, [args])`: methods setting up a mock (or rarely a fake). These methods must be of Unit type, and only stub responses for the given mock;
- `fun forgeSomething([args]): T`: methods setting up a forgery or an instance of a concrete class. These methods must return the forged instance;
- `fun assertObjectMatchesCondition(object, [args])`: methods verifying that a given object matches a given condition. These methods must be of Unit type, and only call assertions with the AssertJ framework (or native assertions);
- `fun verifyMockMatchesState(mock, [args])`: methods verifying that a mock’s interaction. These methods must be of Unit type, and only call verifications with the Mockito framework.
- `fun setupSomething()`: method to setup a complex test (should only be used in the Given part of a test).

#### Clear vs Closed Box testing

Clear Box testing is an approach to testing where the test knows 
the implementation details of the production code. It usually involves making a class property visible
in the test (via the `internal` keyword instead of `private`).

Closed Box testing on the contrary will only use `public` fields and 
functions without checking the internal state of the object under test.

While both can be useful, relying too much on Clear Box testing will make maintenance more complex: 

 - the tiniest change in the production code will make the test break;
 - Clear Box testing often leads to higher coupling and repeating the tested logic in the test class;
 - it focuses more on the way the object under test works, and less on the behavior and usage.
 
It is recommended to use Closed Box testing as much as possible.

#### Property Based Testing

To ensure that our tests cover the widest range of possible states and inputs, we use property based 
testing thanks to the Elmyr library. Given a unit under test, we must make sure that the whole range 
of possible input is covered for all tests.

### Nightly Tests

#### Update Session Replay functional tests payloads

Session Replay has a suite of functional tests which can be found in the `instrumentation:integration` module.
Those tests are assessing the recorded payload for a specific scenario against a given payload from `assets/session_replay_payloads` in the `androidTest` source set.
In case you need to update these payloads after a change in the SDK, you can run the following command:
    
```shell
 ./local_ci.sh --update-session-replay-payloads
```

#### Implementation

Each public API method in the SDK is covered by a test case in the `nightly-tests` module. All test cases are executed on a Bitrise emulator by a Datadog Synthetic Test every 12 hours. Each test case
output is measured by 2 Datadog Monitors (one for performance and one for functionality). There are some best practices when writing a nightly test as follows: 

- The test method name must follow the following format: `[rootFeature]_[subFeature]_[method]_[additionalInfo]` where:

    1. `rootFeature` is one of the top level features (logs, rum, apm, et)
    
    2. `subFeature` is the feature under test (logger, monitor, …)
    
    3. `method` is the method under test (not necessarily exactly the exact method name but the purpose of the feature from a customer’s PoV, e.g: DataScrubbing)
    
    4. `additionalInfo` is some context to distinguish multiple test on the same method (could be related to the argument, the context, a state)
    
- We need to add an identifier in the method documentation following the method signature in the [apiSurface](dd-sdk-android-core/api/apiSurface). 
  This will be used by our test coverage tool.
  

We have created a Live Template that you can add in your development environment (Android Studio, IntelliJ IDEA) to ease your work when creating a nightly test:
```
/**
 * apiMethodSignature: THE API METHOD SIGNATURE HERE
 */
@org.junit.Test
fun $EXP$() {
    val testMethodName = "$EXP$"
    measure(testMethodName) {
        // API call here
    }
}

```

#### Execution

Because of our crash handling [test cases](instrumented/nightly-tests/src/androidTest/kotlin/com/datadog/android/nightly/crash), when running the nightly tests through gradle,  
the process does not finish properly so it may happen that will keep hanging until it will eventually timeout.To avoid this issue you should run the tests through adb shell directly 
using the instrumentation tool: 
```
./gradlew :instrumented:nightly-tests:installDebug
./gradlew :instrumented:nightly-tests:installDebugAndroidTest
adb shell am instrument -w -e package com.datadog.android.nightly.[feature] com.datadog.android.nightly.test/androidx.test.runner.AndroidJUnitRunner
```
where `feature` refers to the specific collection of tests (feature to test) and can take one of the following values: `rum`, `crash`, `log`, `trace`. If you want to run
all the nightly tests just omit the feature in the `package` definition.
