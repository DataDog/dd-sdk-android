# Contributing

First of all, thanks for contributing!

This document provides some basic guidelines for contributing to this repository.
To propose improvements, feel free to submit a PR or open an Issue.

## Setup your developer Environment

To setup your enviroment, make sure you installed [Android Studio](https://developer.android.com/studio).

**Note**: you can also compile and develop using only the Android SDK and your IDE of choice, e.g.: IntelliJ Idea, Vim, etc.

### Modules

This project hosts the following modules:

  - `dd-sdk-android`: the main library implementing all Datadog features (Logs, Traces, RUM, Crash reports);
  - `dd-sdk-android-ktx`: a set of Kotlin extensions to make the `dd-sdk-android` library more Kotlin friendly;
  - `dd-sdk-android-ndk`: a Plugin to allow tracking NDK information;
  - `dd-sdk-android-glide`: a lightweight library providing a bridge integration between `dd-sdk-android` and [Glide](https://bumptech.github.io/glide/);
  - `dd-sdk-android-timber`: a lightweight library providing a bridge integration between `dd-sdk-android` and [Timber](https://github.com/JakeWharton/timber);
  - `instrumented/benchmark`: a test module to verify the performance of the library;
  - `instrumented/integration`: a test module with integration tests using Espresso;
  - `tools/detekt`: a few custom [Detekt](https://github.com/arturbosch/detekt) static analysis rules;
  - `tools/noopfactory`: an annotation processor generating no-op implementation of interfaces;
  - `tools/unit`: a utility library with code to help writing unit tests;
  - `sample/***`: a few sample application showcasing how to use the library features in production code;

### Building the SDK

You can build the SDK using the following Gradle command:

```shell script
./gradlew assembleAll
```

### Running the tests

The whole project is covered by a set of static analysis tools, linters and tests, each triggered by a custom global Gradle task, as follows:

```shell script
# launches the debug and release unit tests for all modules 
./gradlew unitTestAll

# launches the instrumented tests for all modules
./gradlew instrumentTestAll

# launches the detekt static analysis for all modules
./gradlew detektAll

# launches the ktlint format check for all modules
./gradlew ktlintCheckAll

# launches the Android linter for all modules
./gradlew lintCheckAll

# launches all the tests described above
./gradlew checkAll
```

## Submitting Issues

Many great ideas for new features come from the community, and we'd be happy to
consider yours!

To share your request, you can open an [issue](https://github.com/DataDog/dd-sdk-android/issues/new) 
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
[opening a Github issue](https://github.com/DataDog/dd-sdk-android/issues/new).
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
[submit as a pull request](https://github.com/DataDog/dd-sdk-android/pull/new/master).
Before you submit a PR, make sure that you first create an Issue to explain the
bug or the feature your patch covers, and make sure another Issue or PR doesn't
already exist.

To create a pull request:

1. **Fork the repository** from https://github.com/DataDog/dd-sdk-android ;
2. **Make any changes** for your patch;
3. **Write tests** that demonstrate how the feature works or how the bug is fixed;
4. **Update any documentation** such as `docs/GettingStarted.md`, especially for
    new features;
5. **Submit the pull request** from your fork back to this 
    [repository](https://github.com/DataDog/dd-sdk-android) .


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

### Code style

Our coding style is ensured by [KtLint](https://ktlint.github.io/), with the
default settings. A KtLint check is ran on every PR to ensure that all new code
follow this rule.

Classes should group their methods in folding regions named after the declaring
class. Private methods should be grouped in an `Internal` named folding region. 
For example, a class inheriting from `Runnable` and `Observable` should use the
following regions.

```kotlin

class Foo :Observable(), Runnable {
    
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
There is also a gradle task that you can use to automatically format the code following the
required styling rules:

```console

./gradlew :dd-sdk-android:ktlintFormat

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

### Fields

Fields should appear in the following order, and be named as explained by these rules:

- The object(s) under test must be named from their class, and prefixed by `tested`. E.g.: `testedListener: Listener`, `testedVisitor: KotlinFileVisitor`.
- Mocked objects must be named from their class (with an optional qualifier), and prefixed by `mock`. E.g.: `mockListener: Listener`, `mockService: ExecutorService`).
- Fake data must be named from their class (with an optional qualifier), and prefixed by `fake`. E.g.: `fakeContext: Context`, `fakeApplicationId: UUID`, `fakeRequest: NetworkRequest`.
- Other fields can be named on case by case basis, but a few rules can still apply:
    - If the field is annotated by a JUnit 5 extension (e.g.: `@TempDir`), then it should be named after the extension (e.g.: `tempOutputDir`).
    
### Test Methods

Test methods must follow the Given-When-Then principle, that is they must all consist of three steps: 

- Given (optional): sets up the instance under test to be in the correct state;
- When (optional): performs an action — directly or indirectly — on the instance under test;
- Then (mandatory): performs any number of assertions on the instance under test’s state, the mocks or output values. It must perform at least one assertion. 

If present, these steps will always be intruded by one line comments, e.g.: `// Given`.

Based on this principle, the test name should reflect the intent, and use the following pattern: `MUST expected behavior WHEN method() withContext`. To avoid being too verbose, `MUST` will be written `𝕄`, and `WHEN` will be written `𝕎`. The `withContext` part should be concise, and can have a trailing curly braces context section to avoid duplicate names (e.g.: `𝕄 create a span with info 𝕎 intercept() for failing request {5xx}`)

Parameters shall have simple local names reflecting their intent, whether they use an `@Forgery` or `@Mock` annotation (or none).

Here's a test method following those conventions: 

```kotlin
    @Test
    fun `𝕄 forward boolean attribute to handler 𝕎 addAttribute()`(
        @StringForgery(StringForgeryType.ALPHABETICAL) key : String,
        @BoolForgery value : Boolean
    ) {
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

### Test Utility Methods

Because we sometimes need to reuse some setup or assertions in our tests, we tend to write utility methods. Those methods should be private (or internal in a dedicated class/file if they need to be shared across tests).

- `fun mockSomething([args]): T`: methods setting up a mock. These methods must return the mocked instance;
- `fun stubSomething(mock, [args])`: methods setting up a mock (or rarely a fake). These methods must be of Unit type, and only stub responses for the given mock;
- `fun forgeSomething([args]): T`: methods setting up a forgery or an instance of a concrete class. These methods must return the forged instance;
- `fun assertObjectMatchesCondition(object, [args])`: methods verifying that a given object matches a given condition. These methods must be of Unit type, and only call assertions with the AssertJ framework (or native assertions);
- `fun verifyMockMatchesState(mock, [args])`: methods verifying that a mock’s interaction. These methods must be of Unit type, and only call verifications with the Mockito framework.
- `fun setupSomething()`: method to setup a complex test (should only be used in the Given part of a test).



