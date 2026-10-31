# Mismatched Leggings

Independent armor product in development for Minecraft 26.1.2, Fabric and NeoForge.
The standalone skeleton and source schema are implemented. A bare
`mismatchedleggings:mixed_leggings` remains inert and non-equippable. Structurally
valid roots carry two ordered source snapshots and project legs equipment. The
composition factory now builds both complete numerical results under local rules.
The shared workbench now crafts both ordered results under server-owned rules.
Equipped and item mixed rendering are implemented; the later
workstation/recovery features are not. This is not a complete or
publishable release.

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
Client-only lifecycle hooks live in the loader client entry points; dedicated
servers do not initialize client classes.

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
Like Boots, the logical server loads both files at startup and resets its rules
when it stops. Changes require restarting the dedicated server or leaving and
reopening the singleplayer world; there is no config command or live reload.
Server authority and preview state are separate even in singleplayer; one
complete snapshot supplies client preview rules when connecting. Until that
snapshot arrives, local examples produce no result rather than using client config
or defaults. Disconnecting clears only Leggings' preview state.

The product registers its own `mismatchedleggings:mixed_leggings_workbench` recipe
and leggings admission through the public Things API. Boots remains independent:
both products share one station, but a boots/leggings input pair has no recipe.
The core owns payment, retained outputs, shift-click and close cleanup. No private
station/menu implementation is duplicated in Leggings.

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

## Equipped appearance

The renderer retains each source's complete native leg and clips waist geometry
in reference waist space, including ordinary transformed child decorations.
Native animation, material passes, dye and trims are preserved; the live mixed
root controls foil for both sides.

Both source views are checked before either split is submitted. Unsupported
standard geometry degrades to one complete left-owner appearance where safe,
then to a product-owned neutral iron-like appearance. A valid pair with a missing
owner asset still reaches that fallback. Bare/malformed roots are not rendered.
There is no universal support claim for arbitrary custom render callbacks,
custom cube compilers or mesh deformation. Fabric's custom-armor lookup is a
small bounded adapter to the pinned API's internal registry; an incompatible
lookup fails closed to neutral rather than pretending to split custom output.

The bounded geometry cache retains immutable templates by native waist identity
and side within a resource epoch, never queued entity state/animation views.
Definitions are assumed stable within that epoch; live transforms are copied at
draw time. Resource reload invalidates the cache and fresh-model providers cannot
grow retention indefinitely.

## Item appearance

GUI, held, framed and dropped items combine native source item geometry at full
scale. Artwork is front-facing: the wearer's left source occupies the viewer's
right half. Dye, trims and native material passes stay with their sources; foil
comes from the live root. Canonical item clipping is separate from equipped
waist clipping and both halves are planned before either is changed.

Unsupported geometry uses two full-size source models with small opposing
rotations. Axis-aligned GUI views can instead use complementary native scissors;
rotated GUI views use the overlap. An empty source model selects the complete
product diagnostic, as do data-less roots. Missing assets retain native missing-model
rendering rather than crashing. Resource reload uses fresh native item models;
no product item-model cache survives it. Loader mesh adapters remain product-local.

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
