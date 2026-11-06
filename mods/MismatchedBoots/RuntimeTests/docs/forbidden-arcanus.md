# Forbidden Arcanus modifiers

Maintained current-Boots integration runs through the existing packaged Leggings
FA harness, with both independently packaged products installed. It does not
compile against Boots classes: the narrow product calls use reflection, native
menus and actual product codecs. No new cross-product launcher is introduced.

## Commands

From the repository root, sequentially:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyForbiddenArcanus -PleggingsBoots=true -PleggingsForbiddenBoots=true --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyForbiddenArcanus -PleggingsBoots=true -PleggingsForbiddenBoots=true -PleggingsForbiddenServerOnly=true '-Pneoforge_version=26.1.2.99' --offline --console=plain
```

Restore the normal `.114` artifacts after a floor run. Omit `--offline` for the
first pinned dependency download. The flag requires current packaged Boots,
not `leggingsBootsJar` or the separate opt-in legacy spike. FA 2.7.2, Valhelsia
Core 1.1.6 and JDT 1.6.11 resolve from the existing checksum-pinned catalog, not
indev. The hidden/muted client also uses the established JEI fixture workaround
for FA's native recipe-sync subscriber. No production dependency is added.

## Coverage and evidence

- Initial native modifier union into both outputs/orientations, duplicate
  inheritance, source immutability and rejection of source-carried root authority.
- Native armor wear and actual death/respawn; independent root smithing in either
  order with normal/shift payment/credit, duplicate refusal, changed/replaced
  recipe rejection, current source/root tag exclusions and stale previews.
- Source-wise trims, rename and grindstone, plus JDT upgrades on single/dual
  modified roots and FA upgrades on charged/configured JDT roots. FE, opaque paid
  state and Eternal's missing durability must survive recomposition.
- Full old roots decoded by the production load hook, live-scalar-only adoption,
  exact unrelated root/history retention, skipped malformed/unknown shapes,
  explicit empty authority, suspension/quarantine and atomic API-failure refusal.
- Real client equipment/inventory packets and native save/disconnect/reopen,
  alongside the existing Leggings integration. No client-side item repair is used.
- Native translated/styled root prefixes (Eternal before Soulbound), unchanged
  custom and source names, ordinary/Shift/F3+H tooltips and repeated read-only
  construction, including worldless/null-player and invalid/suspended contexts.
  Title/context checks run before/after real resource reload with both independently
  packaged products installed. Before reload, native Eternal/Soulbound/dual
  backgrounds pass through the real deferred tooltip extractor and rendered
  frames, alongside native controls.

The isolated variant is `forbiddenArcanus-boots-modifiers` (or
`forbiddenArcanus-boots-server-only-modifiers`). Reports and verification logs
are under the Leggings fixture's usual `build/reports/<baseline>/` and
`build/verification/<baseline>/` directories. In addition to the ordinary Leggings
markers, require fresh `forbidden-boots-server.txt` and, when run,
`forbidden-boots-client.txt` and `forbidden-boots-tooltip.txt`. The existing
`forbidden-modifiers-tooltip.png` captures a relevant final Boots dual-effect
frame in this profile, not an unrelated screenshot suite. Server-only is not
client/persistence or presentation acceptance.

The [frozen legacy data](../fixtures/forbidden-boots-1.0.0.json) contains 15 complete
NBT roots produced by the actual unchanged published Boots 1.0.0 binary during
the bounded spike. The manifest pins the producer SHA-256 and compressed-payload
SHA-256. Seven roots carry an adoptable live scalar; historical effects on the
other roots are not retroactively granted. Native-prefixed structural names can
predate a player's later custom name, which must remain intact. These fixtures
are active regression inputs, not historical documentation or indev files.

Typed paid `long array` data is checked through world NBT and the native wire
codec. JSON cannot retain the NBT array tag kind and is not an exact-type
round-trip oracle for that payload. Plain states still retain JSON codec coverage.
The optional-adoption fault is test-only; production must preserve the original
successful decode and refuse unsafe later recomposition without adding a marker.

Automated title/background checks do not stand in for owner visual acceptance.
