I want to build a system to do the following thing.

rum-sdk-integration-testing repository has tests that test dd-sdk-android/ios and other platforms. I want to be able to run these tests from CI of dd-sdk-android (and other repos).

I want to be able to do the following things:
1. In any PR in dd-sdk-android I want to have a manual job that allows you to execute rum-fit tests for this branch.
2. Also after merge to develop of a new commit in the SDK the version inside rum-fit of the SDK should be bumped. Probably through PR, but I am not 100% sure. If it fails the CI of develop in SDK CI should fail. The way it should work is the CI pipeline of dd-sdk-android has a child pipeline in rum-fit that itself first runs the tests on the new commit then creates a PR with the commit bump and merges it without running any ci.

What I am thinking is the following system. I know that GitLab allows you to have child pipelines from other repos. This is what I want to base the solution on. I'm thinking about having a solution that in each repo can define some sort of API that other repos can call. For example rum-sdk-integration-testing framework defines a method that allows you rum android tests of a specific commit of the SDK. Same for ios and other platforms. The resulting pipeline should run only android tests, not other platforms. This should be done by refactoring rum-fit gitlab yamls.

Also please look at the state of gitlab ci of rum fit in main branch. It has more than the current branch because current branch is in the process of migration to bazel. Before implementing the thing we are discussing we need to run android rum-fit tests on ci using bazel (just use what is already in main branch and migrate to bazel).