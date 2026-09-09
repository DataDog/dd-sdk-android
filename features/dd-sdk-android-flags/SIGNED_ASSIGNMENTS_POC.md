# Signed assignment payload POC

This branch verifies the final Feature Flags assignment payload before JSON decoding.

The SDK creates a 16-byte random nonce for each request. The edge signature binds this nonce to the request body, client token, response body, and validity period.

The verifier uses Java Security and X509 APIs. It trusts only the embedded POC root certificate.

The customer does not provide a key. The customer does not change the application trust store or network configuration.

The embedded root and deterministic origin key are test-only. Production must use the RC X509 root and certificate lifecycle.

## Example proof

Start the local edge service on port `17676`. Install the `us1Debug` sample, then run:

```sh
adb reverse tcp:17676 tcp:17676
adb shell am start -n com.datadog.android.sample/.NavActivity
adb logcat -s SignedAssignmentsPOC:I
```

The emulator prints:

```text
SignedAssignmentsPOC: verified country-message=hello-us
```

`PrecomputedAssignmentsVerifierTest` verifies the saved origin fixture. It also rejects one changed response byte.
