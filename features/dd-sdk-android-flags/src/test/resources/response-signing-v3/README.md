# Frozen synthetic response signing v3 vectors

`corpus.json` is an unchanged copy of the independently generated shared corpus,
SHA256 `cc444ec8a2890430fa8dd8aee6cbde5a92d388ee55938596aac67891cfc099b6`.
Profile SHA256 `15649074caeb9a51ce233eb0d29d5725be6b33ee1c2497493f33acf992adff81`.
The native verifier runs all 43 response cases (6 accept, 37 reject). The two
server private-key cases are outside the SDK verifier profile. Private leaf
credentials in this synthetic corpus are public test material, never production.

`key-usage-supplement.json`, SHA256
`e6e2bc29ab28b490a1e17bb7c46a9342cf9496d9962c04aa5ca0e3ce6b08d347`, independently
signs canonical and nonminimal named-bit-string KU encodings. Its expected results
follow X.690 11.2.2; it contains no private keys. Neither fixture is rewritten by tests.
