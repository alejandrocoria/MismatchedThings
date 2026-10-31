# Mismatched Leggings

Independent armor product in development for Minecraft 26.1.2, Fabric and NeoForge.
Only the standalone skeleton is implemented: `mismatchedleggings:mixed_leggings`
is an inert, non-equippable diagnostic item with a temporary iron-leggings icon.
Composition, source data and actual mixed rendering are not implemented yet.
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

Import the root Gradle project in IntelliJ. The four product-named configurations
are `MismatchedLeggings — Fabric Client/Server` and
`MismatchedLeggings — NeoForge Client/Server`. Each loader uses its own
`runs/client` and `runs/server`; these do not reuse Boots directories. Existing
Boots configurations retain their names and paths.

The [runtime guide](RuntimeTests/README.md) describes isolated packaged-artifact
checks, supported baselines and the hidden-client contract. Rendering research
under `spikes/rendering` remains opt-in with `-PleggingsSpike=true`; it is not
part of the product or its normal build.
