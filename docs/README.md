# Documentation map

Use this map to find SDK guidance and repository validation procedures.
Contributors start with the contributor guide, then read the relevant module
instructions and run the affected module's validation tasks from the agent guide.
SDK users start with the root README and the guide for their product or integration.

| Reader need | Authoritative route |
| --- | --- |
| SDK capabilities and customer documentation | [SDK overview](../README.md) |
| Developer setup, contribution process, and local CI | [Contributor guide](../CONTRIBUTING.md) |
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

## Documentation ownership and review

Include documentation for changed behavior or procedures in the implementation PR.
Review factual accuracy against the affected code, navigation, reader purpose,
and the scope of any instructions. Preserve useful existing guidance.
Use short sentences and active voice. A mechanical result does not establish
semantic accuracy or full Simplified Technical English compliance.
Record the affected-document decision and actual validation results in PR evidence.
Leave unrelated legacy prose for a separate change.

## Foundation validation

The repository-owned [Foundation entry](../ci/scripts/foundation_check.py) selects
`AGENTS.md`, this map, built-in document checks, and an advisory documentation reminder.
The [Factoryspec](../factoryspec.yaml) declares the corresponding report and nudge bindings.
Foundation is the only selected capability.

These commands require a separately provisioned FactoryKit runtime. Run them from
the repository root with the Python interpreter from that runtime's environment:

```sh
python ci/scripts/foundation_check.py --snapshot worktree --report-only --format json
python ci/scripts/foundation_check.py --snapshot index --against HEAD --format json
```

The first command reports findings; exit status zero does not mean the gate passed.
The second evaluates staged content against `HEAD` and returns `allow` or `block`.
Keep the entry and rule inputs aligned between the worktree and staged snapshot.
Only tracked files supply validation evidence; stage every intended configuration
path explicitly before staged validation. Do not stage unrelated files.

With the verified FactoryKit executable available, validate configuration separately:

```sh
factorykit factoryspec validate --path factoryspec.yaml --snapshot index --format json
factorykit factoryspec bindings --path factoryspec.yaml --entry-point ci/scripts/foundation_check.py --snapshot index --format json
```

The document checks inspect tracked supported documents, including those outside
this map. They assess local targets, supported anchors, and entry-to-map navigation.
Coverage limits and evaluation errors remain part of the result.
The reminder requests human or agent review; it does not certify document quality.

No hook, CI job, or host control is activated by these files. Runtime installation
and automatic invocation require separate setup. SDK validation remains governed
by the contributor and agent guides.
