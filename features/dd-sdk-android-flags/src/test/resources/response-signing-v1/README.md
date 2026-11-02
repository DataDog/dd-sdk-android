# Frozen synthetic response signing public v1 vectors

`corpus.json` is an unchanged copy of the independently generated shared corpus,
SHA256 `f749069c3344eb72eac11a614bdb92215208e472feeb98467367d78ef42782c5`.
Profile SHA256 `a32c6919d1f730b076994f89317ef441fa4c4b4556c093edf0216632a882f783`.
The native verifier runs all 46 response cases (6 accept, 40 reject). The two
server private-key cases are outside the SDK verifier profile. Private leaf
credentials in this synthetic corpus are public test material, never production.

`key-usage-supplement.json`, SHA256
`f7b55015240951b90596692c5edec4209660a842b6f5e82e9d39da2be7c83cd1`, independently
signs canonical and nonminimal named-bit-string KU encodings. Its expected results
follow X.690 11.2.2; it contains no private keys. Neither fixture is rewritten by tests.

Applicability profile `response-signing-public-v1`, manifest SHA256
`18193622653a5867ff5e8501fb4fabebcbe6b979233b07406e2d70d172005544`.
The FFE LEAN spreadsheet remains decision authority; this adaptation makes no Lean proof claim.
