# Frozen synthetic response signing public v1 vectors

`corpus.json` is a byte-for-byte copy of the [shared corpus](https://github.com/DataDog/ffe-system-test-data/blob/7caa9706dcab035854a34ea41311f5eeb6ce6743/response-signing/v1/corpus.json)
at revision `7caa9706dcab035854a34ea41311f5eeb6ce6743`,
SHA256 `393490bfefe382b9b75cf2964c131a910faa679888689457f99c2da2dc57fbbd`.
Profile SHA256 `a32c6919d1f730b076994f89317ef441fa4c4b4556c093edf0216632a882f783`.
The native verifier runs all 46 response cases (6 accept, 40 reject). The two
server private-key cases are outside the SDK verifier profile. Private leaf
credentials in this synthetic corpus are public test material, never production.

`key-usage-supplement.json`, SHA256
`f7b55015240951b90596692c5edec4209660a842b6f5e82e9d39da2be7c83cd1`, independently
signs canonical and nonminimal named-bit-string KU encodings. Its expected results
follow X.690 11.2.2; it contains no private keys. Neither fixture is rewritten by tests.

Applicability profile `response-signing-public-v1`; the corpus revision and hash
above identify the corrected fixture data used by this adapter.
The FFE LEAN spreadsheet remains decision authority; this adaptation makes no Lean proof claim.
