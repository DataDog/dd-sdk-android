# Precomputed Fixtures

These files are copied unchanged from [DataDog/ffe-system-test-data](https://github.com/DataDog/ffe-system-test-data/tree/c8cc8da239341748dc8c30c2145c0f0b5432c958/precomputed-assignments/cases/evaluator), pinned to commit `c8cc8da239341748dc8c30c2145c0f0b5432c958`:

- `all-types-success.json`
- `type-mismatch-all-types.json`

The codec tests wrap each fixture's `response` in a portable envelope and check assignment values, native types, metadata, and round trips. They do not execute the fixture's evaluation or event expectations. Separate typed-read tests preserve Android's existing conversion rules, including integer-to-Double promotion.

Source license: Apache License 2.0, as in this repository.

Datadog ffe-system-test-data
Copyright 2026-Present Datadog, Inc.

This product includes software developed at Datadog (https://www.datadoghq.com/).
