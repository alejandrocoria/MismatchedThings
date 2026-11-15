# Equipped-entry spike

Opt-in candidate for the
[entry-point review](../../local-docs/EQUIPPED-RENDERING-ENTRY-SYMMETRY-EXPLORATION.md),
not production integration. Run from the repository root with the normal JDK/cache
permissions:

```powershell
.\spikes\equipped-entry\run.ps1
.\spikes\equipped-entry\run.ps1 -Case fabric-before,nf-native
```

## Isolation

- The explicit init script compiles a separate test mod under this spike's ignored
  `build/`. It does not add sources to normal fixture/product source sets.
- Every profile uses the existing Leggings `runEquippedServer`/`runEquippedClient`
  launches, disposable worlds, native pins and hidden/muted-window hooks. No new
  gallery, screenshot or manual world is produced.
- Candidate profiles remove **one entry** from the staged Boots client mixin JSON,
  then apply `BootsEntryMixin` from the test mod. Original product/core sources and
  normal built JARs are unchanged. Baselines retain the real production redirect.
- The runner removes its own staged test mod and restores product staging copies
  from the normal build outputs, comparing hashes. Only obsolete generated Boots
  1.0.0 copies in these exact runtime profile directories are removed; published
  input fixtures and indev instances are never inputs or targets.
- Boots-only uses the existing `viewerBootsOnly` environment/server seed branch
  solely to launch without Leggings. No viewer is installed or certified. The
  test mod replaces the client fixture's normal tick checks with this bounded
  entry probe. Existing workbench seed assertions are not equipment acceptance.

## What it measures

`EntryChecks` obtains actual baked native entity renderers and their armor layers,
then invokes `HumanoidArmorLayer.submit` with native states and a recording
collector. This executes `renderArmorPiece`, the candidate/production mixins,
native equipment materials/extensions and actual deferred model nodes; it never
calls the product equipment renderer directly. Node animation is executed too.
This is not a GPU/whole-scene screenshot comparison.

It reads the real merged armor-layer export to distinguish the old redirect,
candidate priority 1100/900 and Leggings priority 1000. A separate compatible
`WrapOperation` around the original native equipment call must survive cancellation
of owned mixed slots and continue forwarding ordinary armor. NeoForge also counts
native `getGenericArmorModel` calls.

Controls include both source orientations, wide/slim players, adult/baby zombies,
normal/small armor stands, native head/chest/other-product equipment, wrong slots,
owner fallback, absent assets, bare/invalid/suspended data, copied foreign data,
dye/trim/root foil, light/outline and unchanged histories. They repeat after real
resource reload. The NeoForge native profile adds catalog-pinned Roots Classic
and Ice and Fire with native armor positives and actual mixed-entry submissions.
Native samples are fixed as Sylvan, Wildwood and Dragonsteel Fire Boots. The
client disconnects its integrated world before stopping, keeping shutdown errors
inside the same strict log gate instead of filtering them out.

Evidence is in `build/evidence/<case>/`: server/client logs, the measured text,
actual merged class and a fresh `passed.txt`. Failed markers or runtime ERROR/FATAL
output fail the runner. Results/limits belong to the private research document,
not a marker alone. The `.99` floor profile runs last; restore normal `.114`
artifacts afterward, sequentially, with the ordinary targeted compile tasks.

Retire this entire executable tree when maintained production entry tests replace
it. Do not retain its patched staging, test-mod tick bypass or priority selection
as another production route.
