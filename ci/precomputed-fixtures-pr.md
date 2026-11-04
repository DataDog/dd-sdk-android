### What does this PR do?

Refresh the shared precomputed response fixtures to `SOURCE_COMMIT`.

### Motivation

Keep mobile response tests aligned with the canonical JSON contract. Ref: FFL-3568.

### Additional Notes

The existing fixture loop runs new cases in normal SDK CI. Review those results before merge.

### Review checklist (to be filled by reviewers)

- [ ] Feature or bugfix MUST have appropriate tests (unit, integration, e2e)
- [ ] Make sure you discussed the feature or bugfix with the maintaining team in an Issue
- [ ] Make sure each commit and the PR mention the Issue number (cf the [CONTRIBUTING](../CONTRIBUTING.md) doc)
