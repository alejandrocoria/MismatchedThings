# Mismatched Leggings

Independent armor product in development for Minecraft 26.1.2, Fabric and NeoForge.
The standalone skeleton and source schema are implemented. A bare
`mismatchedleggings:mixed_leggings` remains inert and non-equippable. Structurally
valid roots carry two ordered source snapshots and project legs equipment, but
numerical composition, acquisition recipes and actual mixed rendering are not
implemented yet. The iron-leggings icon is temporary.
This is not a playable or publishable release.

## Development

Run Gradle from the repository root:

```powershell
.\gradlew.bat :MismatchedLeggings:Fabric:assemble :MismatchedLeggings:NeoForge:assemble
.\gradlew.bat :MismatchedLeggings:verifySkeleton --console=plain
```

`Common` owns product code/resources shared between loaders. `Fabric` and
`NeoForge` own registration, metadata and native Things inclusion. Neither
loader depends on Boots. Things `>=1.1.0 <2.0.0` is required and bundled; no
separate download is needed. Product identity/version live in
[mod.properties](mod.properties); there are no publication IDs/tasks yet.
Client-only production hooks will be added when rendering needs them, rather
than placing client classes in common initialization.

## Source and equipment boundary

`mismatchedleggings:mixed_leggings_data` stores two defensive, single-item `LEGS`
sources with persistent and network codecs. Empty, wrong-slot, invalid native
components and nested mixed sources are rejected. Structural roots own their
item name/model, stack limit and equipment projection. Interaction settings and
equipment asset follow the left source; an isolated non-Minecraft equip sound
wins over a Minecraft sound in either orientation. Camera overlays are not copied.
These fields alone are not the future numerical composition implementation.

Only validated roots cross the product's consumer boundary. Bare/malformed roots
cannot equip through normal use, armor-slot queries or dispensers, even when an
invalid root carries `EQUIPPABLE`. The native guards are item-local in scope and
leave unrelated armor alone. `mismatchedleggings:suspended_leggings` is reserved,
not registered: missing-mod preservation/recovery is not implemented yet.

## Runs and tests

Import the root Gradle project in IntelliJ. The four product-named configurations
are `MismatchedLeggings — Fabric Client/Server` and
`MismatchedLeggings — NeoForge Client/Server`. Each loader uses its own
`runs/client` and `runs/server`; these do not reuse Boots directories. Existing
Boots configurations retain their names and paths.

The [runtime guide](RuntimeTests/README.md) describes isolated packaged-artifact
checks, supported baselines and the hidden-client contract. Rendering research
under `spikes/rendering` remains opt-in with `-PleggingsSpike=true`; it is not
part of the product or its normal build.
