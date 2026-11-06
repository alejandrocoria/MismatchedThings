# Forbidden Arcanus — Published Boots load/routing spike

Disposable test-only candidate for the
[joint exploration](../../../../local-docs/FORBIDDEN-ARCANUS-SHARED-MODIFIERS-EXPLORATION.md).
It does not implement production Boots compatibility or extract a Things API.

## Run

From the repository root, provide the actual pinned NeoForge Boots 1.0.0 JAR:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyForbiddenArcanus -PleggingsForbiddenBootsSpike=true -PleggingsBoots=true '-PleggingsBootsJar=path/to/MismatchedBoots-26.1.2-neoforge-1.0.0.jar' --offline --console=plain
```

Omit `--offline` for first fixture downloads. The hash must match the existing
[historical manifest](../../../MismatchedLeggings/RuntimeTests/fixtures/boots-1.0.0.json);
current/recompiled Boots is not a substitute. Optional floor:
`'-Pneoforge_version=26.1.2.99'`. Run baselines/profiles sequentially and restore
normal `.114` compilation afterward.

The existing Leggings native profile stages the unchanged Boots artifact, current
Leggings/nested Things, pinned FA 2.7.2, Valhelsia 1.1.6 and JDT 1.6.11. Its client
also stages the existing pinned JEI fixture for FA's native missing-JEI problem.
Inputs are resolved/cache/checksum-validated through the existing catalog, not
indev instances. Published Boots itself remains byte-for-byte unchanged.

## Boundary and phases

- The **server/write** phase constructs legacy roots using the published factory
  and actual old smithing menus, records raw NBT, saves a real barrel/world and
  does not run adoption. The test-only component is registered but absent from
  every produced old root.
- The **client/adopt** phase loads that saved-world copy through a disposable
  post-success `ItemStack` persistent-codec wrapper. Neither codec construction
  nor early client setup binds the optional native modifier API. The candidate
  applies the already-resolved native holder, not a global world/registry cache.
- Only a valid root's existing supported native scalar can seed flags. Source
  history is not replayed. Explicit own flags, absent scalars, unknown modifiers,
  unrecognized names, bare/malformed/suspended roots are controls.
- The temporary component is `leggings_skeleton_test:forbidden_boots_state`, using
  the existing two-boolean codec. It is deliberately **not** the proposed product
  registration `mismatchedboots:forbidden_modifiers`.
- Native container loading, full-component/source comparisons, real equipment/
  inventory packets and normal save/disconnect/reopen establish the load boundary.
  The adoption counter must not grow on reopen: re-converting an unsaved old
  barrel is not accepted as successful writeback. No client item writes,
  inventory-tick migration, tooltip mutation or screenshots are used.
- Recipe tests observe old native/source-wise routes and paid JDT root upgrades,
  plus a bounded candidate FA request classifier with scalar masking/duplicate
  rejection. They do **not** implement or certify future FA menu takeover,
  stale-pickup protection, arbitrary datapacks or shared helper ABI.

Reports/logs use the existing `forbiddenArcanus-previous-boots-legacy-spike`
variant under the Leggings runtime project's `build/{reports,verification}/<baseline>/`.
Fresh `forbidden-boots-server.txt`, `forbidden-boots-loaded.txt`,
`forbidden-boots-reloaded.txt`, `forbidden-boots-routing.txt`, `forbidden-boots-client.txt`, standard phase markers
and `matrix.ok` are evidence. `forbidden-boots-baseline.nbt` contains the raw old
roots; it is generated, not a committed world fixture.

Keep this executable experiment only until implementation tests replace its
useful checks, then remove its sources, opt-in source inclusion, dispatch and
persistent-load probe. Findings belong in the joint private document.

Executed on 2026-11-06: full producer/hidden-client load/save/reopen passed on
`.114` and `.99`, followed by normal non-spike `.114` validation. Eternal's old
direct-native route differs from Soulbound's source-wise route. Paid old JDT
recomposition retains the flags but restores finite Eternal durability, confirming
that future FA finalization must run last. This is feasibility, not shipped support.
