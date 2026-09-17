# Android protected assignments wargame implementation

Status: production-shaped wargame code. This code is not production-approved.

Tracking issue: `FFLSDK-178`.

## Decision under test

The Android SDK supports one local `assignmentProtection` policy:

| Policy | Required response | Customer authorization |
| --- | --- | --- |
| `DISABLED` | Existing unsigned response | Not accepted by this feature |
| `SIGNED` | Valid Datadog Feature Flags version 2 signature | Not sent |
| `SIGNED_AND_AUTHORIZED` | Valid version 2 signature | Current customer JWT required |

The default is `DISABLED`. This preserves behavior for customers who do not enroll.

The SDK selects the policy from local configuration. Network fields cannot enable, disable, or reduce the policy.

An existing convenience API remains source-compatible. `assignmentAuthorization(...)` selects `SIGNED_AND_AUTHORIZED` when the customer did not select a policy.

An explicit incompatible configuration does not crash or downgrade. The SDK logs one bounded error and sends no assignment request.

```kotlin
val signedOnly = FlagsConfiguration.Builder()
    .assignmentProtection(AssignmentProtection.SIGNED)
    .build()

val signedAndAuthorized = FlagsConfiguration.Builder()
    .assignmentProtection(AssignmentProtection.SIGNED_AND_AUTHORIZED)
    .assignmentAuthorization(
        AssignmentAuthorization(
            bearerToken = customerJwt,
            expiresAt = customerJwtExpiration
        )
    )
    .build()
```

The application can replace or remove a JWT without rebuilding the client:

```kotlin
Flags.setAssignmentAuthorization(
    AssignmentAuthorization(
        bearerToken = refreshedCustomerJwt,
        expiresAt = refreshedCustomerJwtExpiration
    )
)
```

The SDK keeps the JWT in memory. The SDK does not persist or log the JWT.

## Security behavior

Both protected policies fail closed.

- The SDK sends signature protocol version `2` and a random 16-byte nonce.
- `SIGNED` rejects any request `Authorization` header and any response authorization policy header.
- `SIGNED_AND_AUTHORIZED` requires one bounded Bearer token and one bounded policy version.
- The SDK verifies the response before it decodes assignments.
- The SDK rejects missing, duplicate, malformed, expired, oversized, or invalid signature fields.
- The SDK checks that the response subject equals the request `targeting_key`.
- The SDK permits protected delivery only over HTTPS.
- The protected HTTP client does not follow HTTP or HTTPS redirects.
- The SDK rejects a protected request body larger than 1 MiB before network delivery.
- The downloader reads at most 2 MiB. It does not buffer an unbounded response.
- The verifier accepts only a P-256 end certificate under the embedded assignment-signing root.
- The verifier requires the leaf certificate to permit digital signatures.
- The signed response lifetime cannot exceed 300 seconds.

The version 2 signature binds these fields in this order:

1. Domain: `datadog.feature-flags.precomputed-assignments.v2\0`.
2. HTTP method.
3. URL scheme.
4. URL authority, including a non-default port.
5. Encoded URL path. Queries are not permitted.
6. Request nonce bytes.
7. SHA-256 of the exact request body.
8. Authorization-presence byte.
9. SHA-256 of the exact JWT, when authorization is present.
10. SHA-256 of the client token.
11. Authorization policy version, when authorization is present.
12. Bounded rules revision. An empty value is valid for the first wargame.
13. Presence and exact value for `content-type`, `dd-application-id`, `x-rkyv`, and `x-use-cache`.
14. Response status.
15. Issued and expiration times.
16. Response byte length and SHA-256.

All variable fields use a four-byte big-endian length. Integers use big-endian encoding.

The edge and Android tests share two golden vectors:

| Policy | Transcript bytes | Transcript SHA-256 |
| --- | ---: | --- |
| `SIGNED` | 288 | `b2dcfe21420f79ac6745a0e054d159d4f3197304bafb239e51c3a7aecf54f2e4` |
| `SIGNED_AND_AUTHORIZED` | 334 | `b0d8cd615160a59a69a0024fdca85213b9ca6d2a542f4490706cc3d67cde3148` |

## Persisted state

Protected mode never trusts the decoded `flags` field in local storage.

The SDK persists the exact verified response body and its signed envelope. The envelope contains:

- local protection policy;
- request nonce;
- response status;
- authorization policy version, when used;
- rules revision;
- issued and expiration times;
- certificate ID and certificate;
- response signature.

At startup, the SDK stages this artifact. It does not publish assignments yet.

After the application supplies an evaluation context, the SDK rebuilds the normal production request. It uses the current endpoint, client token, environment, SDK fields, subject, attributes, and JWT. It reuses only the persisted nonce.

The SDK then verifies the persisted response again. This check enforces the certificate validity and signed expiration time at restore time. The SDK decodes assignments only after successful verification.

The SDK rejects or clears the artifact when any bound value changed. This includes the endpoint, client token, context, JWT, policy version, rules revision, body, certificate, or signature.

## Response ordering

The SDK uses one request generation for context, authorization, reset, and feature stop events.

- A context update increments the generation.
- A JWT update or removal increments the generation and clears assignments.
- A reset or feature stop increments the generation and clears assignments.
- A delayed operation can commit only when its generation and context remain current.
- Cache verification also checks its repository generation before publication.

These rules prevent an older valid response from replacing newer context or authorization state.

## Test evidence

The unit tests cover these cases:

- both edge-generated version 2 golden signatures;
- a changed response body;
- missing protected fields;
- policy mismatch between signed-only and signed-with-authorization;
- duplicate signed headers;
- oversized certificate metadata;
- expired responses;
- response lifetimes longer than 300 seconds;
- protected cleartext endpoints and oversized requests;
- changed rules revision;
- default and explicit public configuration behavior;
- production request construction for both protected policies;
- unsigned network state in protected mode;
- persisted envelope serialization and deserialization;
- protected cache reconstruction and envelope equality;
- reverse response order;
- delayed success after JWT removal;
- delayed success after reset;
- bounded network reads.

`LiveSignedAssignmentTest` constructs its request with `PrecomputedAssignmentsRequestFactory`. This is the same factory used by the SDK. The test does not construct protocol headers or JSON by hand.

The instrumentation test emits only bounded wargame diagnostics:

- protection policy;
- verification result;
- test request generation;
- certificate ID;
- cache source and age;
- bounded fixture value.

It does not emit the JWT, client token, full subject, or response payload.

Use signed-only mode by omitting `assignmentJwt`:

```sh
./gradlew :features:dd-sdk-android-flags:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.datadog.android.flags.internal.net.LiveSignedAssignmentTest \
  -Pandroid.testInstrumentationRunnerArguments.clientToken=<staging-client-token>
```

Use signed-with-authorization mode by also supplying `assignmentJwt`:

```sh
./gradlew :features:dd-sdk-android-flags:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.datadog.android.flags.internal.net.LiveSignedAssignmentTest \
  -Pandroid.testInstrumentationRunnerArguments.clientToken=<staging-client-token> \
  -Pandroid.testInstrumentationRunnerArguments.assignmentJwt=<customer-jwt>
```

Optional arguments are `assignmentEndpoint` and `fixtureKey`.

## Remaining blockers and decisions

The following items block production approval. They do not block the first controlled wargame.

1. Replace the POC root and leaf with a production assignment-signing certificate lifecycle.
2. Define root overlap, leaf rotation, emergency revocation, and long-lived SDK compatibility.
3. Decide whether a signing-purpose or scope extension is required in each leaf certificate.
4. Deploy trusted customer enrollment and signing secrets to the staging edge service.
5. Run both protected policies against the staging edge service on an Android emulator.
6. Run the attacker cases through a TLS interception proxy and modified local storage.
7. Freeze the version 2 protocol after Android, iOS, and edge implementations pass the same vectors.
8. Define product behavior after a protected cached artifact expires while the device is offline.
9. Add telemetry names and sampling after the security review.
10. Complete an Android SDK API review for naming, stability, and documentation.

The signed `rulesRevision` is empty in the first wargame. This field reserves a future origin-integrity value. It does not claim that the edge received verified origin rules.

Payload signing protects an unmodified SDK from a spoofed assignment response. It does not protect a modified application process or a device owner who changes SDK execution.
