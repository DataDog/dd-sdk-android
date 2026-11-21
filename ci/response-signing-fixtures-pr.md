### What does this PR do?

Refresh the shared response-signing fixtures to central commit `SIGNING_COMMIT`.

### Motivation

Keep the Flags response-signing verifier aligned with the shared contract. Ref: FFLSDK-267.

### Additional Notes

The native verifier runs every manifest-listed suite. Run local verifier tests and normal SDK CI. Fix failures before review and merge.
The workflow waits while an update PR is open. It does not merge PRs or release SDKs.

### Review checklist (to be filled by reviewers)

- [ ] Feature or bugfix MUST have appropriate tests (unit, integration, e2e)
- [ ] Make sure you discussed the feature or bugfix with the maintaining team in an Issue
- [ ] Make sure each commit and the PR mention the Issue number (cf the [CONTRIBUTING](../CONTRIBUTING.md) doc)
