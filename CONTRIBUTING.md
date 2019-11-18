# Contributing

First of all, thanks for contributing!

This document provides some basic guidelines for contributing to this repository.
To propose improvements, feel free to submit a PR or open an Issue.

## Submitting Issues

Many great ideas for new features come from the community, and we'd be happy to consider yours!

To share your request, you can [open a Github issue](https://github.com/DataDog/dd-sdk-android/issues/new) with the
details about what you'd like to see. At a minimum, please provide:

 - The goal of the new feature;
 - A description of how it might be used or behave;
 - Links to any important resources (e.g. Github repos, websites, screenshots, specifications, diagrams).

## Found a bug?

For any urgent matters (such as outages) or issues concerning the Datadog service or UI,
contact our support team via https://docs.datadoghq.com/help/ for direct, faster assistance.

You may submit bug reports concerning the Datadog SDK for Android by [opening a Github issue](https://github.com/DataDog/dd-sdk-android/issues/new).
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

Reports that include rich detail are better, and ones with code that reproduce the bug are best.

## Have a patch?

We welcome code contributions to the library, which you can [submit as a pull request](https://github.com/DataDog/dd-sdk-android/pull/new/master).
Before you submit a PR, make sure that you first create an Issue to explain the bug or the feature your patch covers,
and make sure another Issue or PR doesn't already exist.

To create a pull request:

1. **Fork the repository** from https://github.com/DataDog/dd-sdk-android ;
2. **Make any changes** for your patch;
3. **Write tests** that demonstrate how the feature works or how the bug is fixed;
4. **Update any documentation** such as `docs/GettingStarted.md`, especially for new features;
5. **Submit the pull request** from your fork back to https://github.com/DataDog/dd-sdk-android .


The pull request will be run through our CI pipeline, and a project member will review the changes with you. At a minimum, to be accepted and merged, pull requests must:

 - Have a stated goal and detailed description of the changes made;
 - Include thorough test coverage and documentation, where applicable;
 - Pass all tests and code quality checks (linting/coverage/benchmarks) on CI;
 - Receive at least one approval from a project member with push permissions.

Make sure that your code is clean and readable, that your commits are small and atomic, with a proper commit message. We tend to use [gitmoji](https://gitmoji.carloscuesta.me/) but this is not mandatory.
