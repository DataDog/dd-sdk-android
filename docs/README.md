# Documentation map

Use this map to find SDK guidance and repository validation procedures.
Contributors start with the contributor guide, then read the relevant module
instructions and run the affected module's validation tasks from the agent guide.
SDK users start with the root README and the guide for their product or integration.

| Reader need | Authoritative route |
| --- | --- |
| SDK capabilities and customer documentation | [SDK overview](../README.md) |
| Developer setup and contribution process | [Contributor guide](../CONTRIBUTING.md) |
| Module ownership, code conventions, and validation commands | [Agent guide](../AGENTS.md) |
| SDK initialization and multiple instances | [Core SDK guide](../dd-sdk-android-core/README.md) |
| RUM setup | [RUM guide](../features/dd-sdk-android-rum/README.md) |
| Log collection | [Logs guide](../features/dd-sdk-android-logs/README.md) |
| Application examples | [Sample guide](../sample/README.md) |
| Compilation and runtime problems | [Advanced troubleshooting](advanced_troubleshooting.md) |
| Measured SDK overhead and measurement conditions | [Performance evidence](sdk_performance.md) |

Product and integration READMEs remain authoritative for their modules.
Performance measurements describe the recorded revisions and devices, not all SDK versions.
Use device-dependent validation only when a connected device or emulator is available.
The agent guide distinguishes those tasks from unit tests and static analysis.

## SDK agent skills

Use the [agent guide](../AGENTS.md) to select the skill for the current task.
These links point to the tracked skill sources. The `.agents/skills/` and
`.claude/skills/` directory aliases expose the same files to agent hosts.
Edit the source once rather than creating separate copies for each host.

| Task | Skill and expected outcome |
| --- | --- |
| Write or revise Kotlin tests and fixtures | [sdk-tests](../ai/skills/sdk-tests/SKILL.md): apply the repository's test conventions. |
| Review a branch or PR | [sdk-review](../ai/skills/sdk-review/SKILL.md): assess defects and report actionable findings. |
| Write commits or PR titles | [sdk-commit](../ai/skills/sdk-commit/SKILL.md): use ticket-based titles and concise commit bodies. |
| Prepare SDK changes for a PR | [sdk-pr-prepare](../ai/skills/sdk-pr-prepare/SKILL.md): run the selected build, lint, test, and generated-file workflow. |
| Create or update a Graphite PR stack | [sdk-pr-stack](../ai/skills/sdk-pr-stack/SKILL.md): follow the selected stack workflow and its prerequisites. |

Load only the skills relevant to the task and follow their own decision points.
Stack operations apply only when a stack is requested. Device-dependent tasks
require the device or emulator described by the selected procedure.

These are repository skills, not FactoryKit invocation registrations. The empty
`skills` list in `factoryspec.yaml` does not disable their discovery by agent hosts.
Registering skills through FactoryKit would require a separately reviewed
invocation configuration and descriptors.

## Documentation ownership and review

Assess documentation impact from changed behavior and reader needs. Include necessary
documentation for changed behavior or procedures in the implementation PR.
Code-only or test-only fixes with no reader impact can require no documentation
change. Record the reason and keep correct explanations unchanged.
Revise a README or map only for a concrete reader need or navigation gap.
Review affected documentation for factual accuracy, navigation, reader purpose,
and the scope of any instructions. Preserve useful existing guidance.
Use short sentences and active voice. A mechanical result does not establish
semantic accuracy or full Simplified Technical English compliance.
Record actual validation results in task output or reports. Include these details
in PR descriptions only when relevant to review, following the
[PR preparation skill](../ai/skills/sdk-pr-prepare/SKILL.md).
Leave unrelated legacy prose for a separate change. Foundation does not require
diagrams, design documents, or a prose review exercise for an internal code or test
fix. Literate conventions apply only after explicit adoption.

## FactoryKit runtime

First inspect any repository-owned pinned environment and existing launch configuration.
This repository currently declares no FactoryKit runtime dependency or version pin.
Reuse an approved installed environment when its required commands are available.
For installation, verification, and recovery, follow the canonical
[Install FactoryKit procedure](https://github.com/ddoghq/factorykit/blob/main/docs/USER-GUIDE.md#install-factorykit).
Use an approved trusted checkout or Git source. Do not substitute a generic
package-index installation or editable contributor setup.

For an existing uv tool installation, inspect its ownership and command location:

```sh
uv tool list
factorykit_bin=$(uv tool dir --bin)
factorykit_executable="$factorykit_bin/factorykit"
"$factorykit_executable" --help
"$factorykit_executable" foundation report --help
"$factorykit_executable" foundation gate --help
"$factorykit_executable" factoryspec validate --help
"$factorykit_executable" factoryspec bindings --help
```

For another approved environment, use its documented absolute executable path.
Keep that exact executable for all subsequent commands. Its console script selects
the installed interpreter without shell activation or an ambient Python import.
Use `doctor --format json` through that executable when supported. Older distributions
can lack `doctor` while supporting the required commands. Report that limit and
verify each required command separately.

If no usable runtime exists, report dependent commands as not run. Follow the same
installation procedure or an approved pinned dependency route. Before a new installation,
state its source, command, target environment, expected writes, and possible network access.
Retain an existing installation request or approval without another permission question.
Installation does not authorize repository adoption, hooks, CI, MCP, or host configuration.

This guidance was reconciled with FactoryKit
[revision db49a01](https://github.com/ddoghq/factorykit/tree/db49a01a2a5edd2b1f89d15e948d53d0b71a7de2).
The guidance revision is not a runtime pin.

## Foundation validation

The repository-owned [Foundation entry](../ci/scripts/foundation_check.py) selects
`AGENTS.md`, this map, built-in document checks, and an advisory documentation reminder.
The [Factoryspec](../factoryspec.yaml) declares the corresponding report and nudge bindings.
Foundation is the only selected capability.

Resolve the [FactoryKit runtime](#factorykit-runtime), then run these commands from
the repository root through its exact executable:

```sh
"$factorykit_executable" --root . foundation report \
  --entry-point ci/scripts/foundation_check.py --snapshot worktree --format json
"$factorykit_executable" --root . foundation gate \
  --entry-point ci/scripts/foundation_check.py --snapshot index --against HEAD --format json
```

The report has `decision: null`. Exit status zero means report completion, not gate success.
The gate evaluates staged content against `HEAD`. It returns `allow` with status zero
or `block` with status one. Invalid invocation returns status two.
Both adapters retain the entry's fixed check and nudge selections. Preserve any
explicitly selected `--factoryspec` binding. These commands retain the current native
entry route without adding that opt-in.
Keep the entry and rule inputs aligned between the worktree and staged snapshot.
Only tracked files supply validation evidence; stage every intended configuration
path explicitly before staged validation. Do not stage unrelated files.

### Use alongside SDK preparation

`sdk-pr-prepare` owns the SDK build, lint, test, and generated-file procedures.
It does not currently invoke the Foundation gate. Run the gate separately on
the final intended snapshot, and record both SDK and documentation evidence in
task output or reports. Apply the PR description guidance above when selecting
evidence to include in the PR.
A Foundation pass does not replace any selected SDK validation.

For staged changes, use the `index` command above before the commit. If the SDK
preparation flow has already committed its changes, compare the committed branch
against its fetched PR base instead. For a PR targeting `develop`:

```sh
"$factorykit_executable" --root . foundation gate \
  --entry-point ci/scripts/foundation_check.py --snapshot HEAD --against origin/develop --format json
```

For a stacked PR, use its actual parent branch as the comparison. Repeat the
relevant validation after edits. Assess documentation accuracy and reader impact
separately; the advisory reminder does not perform that review.

The `local` modifier of `sdk-pr-prepare` skips push and PR creation, but still runs
the selected SDK checks. It is not a documentation-only validation mode.

### Configuration and coverage

With the verified FactoryKit executable available, validate configuration separately.
Stage only intended changed paths explicitly. Factoryspec reads require the root,
descriptors, and schemas to be tracked in the selected snapshot:

```sh
"$factorykit_executable" --root . factoryspec validate \
  --path factoryspec.yaml --snapshot index --format json
"$factorykit_executable" --root . factoryspec bindings \
  --path factoryspec.yaml --entry-point ci/scripts/foundation_check.py --snapshot index --format json
```

The document checks inspect tracked supported documents, including those outside
this map. They assess local targets, supported anchors, and entry-to-map navigation.
Coverage limits and evaluation errors remain part of the result.
Navigation retains canonical `ai/skills/` paths. The `.agents/skills/` and
`.claude/skills/` discovery aliases remain intact. Alias presence alone does not
prove that an agent host loads a skill.
The reminder requests human or agent review; it does not certify document quality.

No hook, CI job, or host control is activated by these files. Runtime installation
and automatic invocation require separate setup. SDK validation remains governed
by the contributor and agent guides.
