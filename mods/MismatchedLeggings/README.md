# Mismatched Leggings

Independent armor product in development for Minecraft 26.1.2, Fabric and NeoForge.
The standalone skeleton and source schema are implemented. A bare
`mismatchedleggings:mixed_leggings` remains inert and non-equippable. Structurally
valid roots carry two ordered source snapshots and project legs equipment. The
composition factory now builds both complete numerical results under local rules.
Acquisition recipes and actual mixed rendering are not implemented yet. The iron-leggings icon is temporary.
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
The factory constructs each root independently from its owner's effective
components, clears managed omissions, applies shared numerical helpers, then
installs structural fields and validates the result.

Only validated roots cross the product's consumer boundary. Bare/malformed roots
cannot equip through normal use, armor-slot queries or dispensers, even when an
invalid root carries `EQUIPPABLE`. The native guards are item-local in scope and
leave unrelated armor alone. `mismatchedleggings:suspended_leggings` is reserved,
not registered: missing-mod preservation/recovery is not implemented yet.

## Composition rules

Product-owned schemas use `mismatchedleggings-attributes.toml` and
`mismatchedleggings-components.toml` through Things' TOML infrastructure.
Lifecycle loading, reload and synchronization are deferred to the workbench block;
the current factory uses defaults or an explicit immutable `CompositionRules`.
Server authority and preview state are separate even in singleplayer.

Attribute policies are average/combine/minimum/maximum/separate/reject. Vanilla
defaults match the family semantics, with maximum step height, average NeoForge
swim speed/nameplate distance and separate creative flight; unknown attributes
retain their orientation owner's modifiers. No third-party defaults/adapters
are imported from Boots. Numeric/component policy meanings match Boots, including
conservative durability averaging, maximum prior work, repair-material union,
enchantment union and maximum-plus-one duplicate-level merging.

Components support owner/omit/same/share/intersection and integer/long numeric
average/minimum/maximum where representable. Structural fields cannot be configured.
NeoForge snapshots effective source attributes on a copy and canonicalizes holders;
Fabric reads stored modifiers. There is no per-tick foreign ability simulation.
Durability is live on each root, with native armor wear, bars, Unbreaking and
Mending. Stored sources are immutable provenance, not durability wallets.

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
