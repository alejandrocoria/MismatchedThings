# Mismatched Leggings

MismatchedLeggings combines two ordinary leggings stacks into two oppositely
oriented mixed pairs on Fabric and NeoForge for Minecraft 26.1.2.
Packaged automated checks and consolidated owner acceptance have passed.
The initial functional implementation is complete; the product is not yet published.

## Installation

Install it on both the client and the server, using the same mod loader on both
sides. Cross-loader connections are not supported. Fabric also requires Fabric API.
MismatchedThings is bundled;
no separate core download is required. Boots is optional: when both products are
installed, they share one workbench and keep their own items and configuration.

## Crafting

Craft a **Mismatched Workbench** with six planks (any combination), shears and a
diamond in this 3x3 arrangement:

```text
plank  shears   plank
plank  diamond  plank
plank  empty    plank
```

Place one ordinary, legs-equippable leggings stack in each workbench input. The
left input supplies the wearer's left leg and half-waist in the primary result;
the right input supplies the right side. The inverse result exchanges them.
Item artwork is front-facing, so the wearer's left appears on the viewer's right.

Both results are visible separately. Taking either consumes the two inputs once;
the other remains available until taken or returned when the screen closes.
Existing mixed pairs and Boots/Leggings cross-product inputs cannot be combined.
Crafting grids and Crafters do not compose leggings; they can repair an existing
mixed pair with an ordinary donor matching either source.

## Combined behavior

- Maximum and remaining durability use the conservative average by default,
  rounded down. Mending, Unbreaking and subsequent wear operate on the live pair.
- Attributes use server-configurable policies. Common vanilla bonuses average
  compatible modifiers; unknown attributes retain each orientation owner's
  contribution by default.
- Both results inherit enchantments from both sources. Equal levels gain one,
  capped at the enchantment's normal maximum plus one. Swift Sneak uses the legs slot.
- Unknown data components follow each orientation's left source by default;
  supported component policies can be configured on the server.

An anvil supports naming, compatible books, composed repair materials and ordinary
donors matching either source. A grindstone disenchants or performs donor repair
with bounded XP paid only on extraction. Smithing applies loaded trims/upgrades
to eligible sources for one addition and, where required, one template per result.
Mixed/Mixed repair is unsupported. See the sections below for transaction details.

## Compatibility and recipe viewers

Normal legs equipment, components, attributes, durability, enchantments and
standard humanoid equipment models provide automatic compatibility. Special
behavior implemented only in a source item's class, custom events or foreign
upgrade tables is not generically transferred. Boots' dedicated mod adapters do
not automatically apply to Leggings.

Advanced Netherite pacification is inherited from either source: Iron protects
against unprovoked phantoms, Gold against piglins, Emerald against enderman
staring, and Diamond supplies all three. No full set is required; different
source perks combine without duplicating a benefit. Native provocation and
retaliation rules still apply. Native source tooltips are retained; the mixed
root keeps its ordinary durability-bar color rather than the optional
material-colored Advanced Netherite bar.

On Fabric, either Cincinnasite, Nether Ruby or Fireruby source from Better Nether
grants its native piglin-ignore recognition in both outputs while Better Nether's
option is enabled. No full set or original armor piece is required. Other native
reasons for piglins to remain neutral are preserved, as are provocation rules.

The bounded Mystical Agriculture adapter reads the six original Essence leggings'
immutable installed augments. Both outputs inherit the strongest supported native
rank per family (10 built-in families/22 IDs); capacity remains local to each
source. Abilities require the actual worn LEGS root. Native player equipment order,
Health Boost/Step Assist arbitration and Absorption timing are preserved, including
optional mixed Boots. Health cleanup clamps safely after removal or actual break.
Each broken survival output refunds only its left source's valid physical purchases,
including weaker ranks and repeated IDs in distinct slots; copied root augment data
does not grant effects or refunds. Source tooltips retain native purchase names/styles
without a second effective augment list at the root. No new persistent state is used.

The original NeoForge MA `9.0.11`/Cucumber `9.0.7` and independent Fabric port
`9.0.10.3`/`9.0.6.2` are separate targets. NeoForge's real-input Speed positive is
retained; the pinned Fabric port has no native connected-input acceleration and
mixed leggings preserve that limitation. Tinkering Table editing/admission
and addon augments are excluded. See the
[implementation contract and acceptance status](local-docs/MYSTICAL-AGRICULTURE-AUGMENTS-IMPLEMENTATION.md)
and [maintained testing coverage](RuntimeTests/README.md#mystical-agriculture-presentation-and-consolidation-block-c).

The separate Awakened set adapter lets either registered Awakened Supremium
Leggings source satisfy the native LEGS comparison in both mixed outputs.
It works alone or alongside mixed Boots' independent FEET recognition.
Two matching sources count as one piece; ordinary Supremium, invalid/suspended
roots, wrong slots and inventory-only items do not qualify. The original Awakened
helmet/chestplate, native option, growth, particles and twenty-tick cadence stay
required. No second growth dispatcher or new state/tooltip is added.
An incompatible local predicate disables only its product's set extension.
See the [joint contract and acceptance status](../../local-docs/MYSTICAL-AGRICULTURE-AWAKENED-SET-IMPLEMENTATION.md)
and [focused test matrix](RuntimeTests/README.md#mystical-agriculture-awakened-set-recognition).

Equipped rendering keeps each native leg and splits standard waist geometry.
Unsupported geometry may fall back to a complete left-owner appearance, then to
neutral leggings. Unsupported item models fall back to two superimposed complete
source models. Arbitrary custom renderers and mesh deformation are best-effort,
not a universal compatibility guarantee.

JEI and REI are optional. Each shows a **Mismatched Workbench** category
with linked inputs and both results, using currently loaded recipes and
synchronized server rules. Cycling examples illustrate combinations rather than
enumerating every possible pair. Recipe reload, disconnect and a new session
refresh those examples independently of Boots. Current family products contribute
to that one category when installed; published Boots 1.0.0 retains its separate
legacy category until updated. Inputs must be placed manually;
there is no automatic recipe-transfer integration.

The bare registry item has no source data and is absent from creative tabs.
Bare command-created roots remain diagnostic and non-equippable. Temporary
missing-source preservation is described below; it is distinct from a bare item.

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

The final `:MismatchedLeggings:verifyProduct` matrix requires explicit unchanged
Boots 1.0.0 JARs for both loaders. See the
[runtime guide](RuntimeTests/README.md#final-packaged-product-acceptance) for
inputs, bounded coverage and evidence paths. It is separate from publication
and from the owner's consolidated manual acceptance.

### Release changelogs

All family mods use one change list for the upcoming release, without version
headings or accumulated beta history. Use `Added:` followed by the new functionality
as a natural continuation of "Added", `Changed:` for the new observable behavior,
and `Fixed:` for the problem that occurred, normally without implementation details.
New compatibilities belong in a separate generated section, not duplicate `Added:`
entries. Only compatibilities added in that release belong there; an empty loader
list must omit the entire section.

Leggings does not yet have release templates, new-compatibility fragments or
publication configuration. Aligning that workflow with
[Boots](../MismatchedBoots/README.md#release-changelogs) is separate release-preparation
work; the format above already applies, without introducing publication tasks here.

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
leave unrelated armor alone. Admitted suspended history is the only inactive
exception allowed to remain equipped; it grants no active armor behavior.

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

## Tooltips and vanilla behavior

Source tooltip sections follow Boots' layout: left and right names, native
visual details and two Things separators, followed by the live root's effective
enchantments/attributes. F3+H adds each namespaced source ID directly below its
name. Custom names retain the natural identity; nested attributes, enchantments,
durability and component-count diagnostics are not duplicated. Native tooltip
visibility and source-provider Shift behavior are respected.

The item directly belongs to `minecraft:enchantable/armor`, `enchantable/leg_armor`,
`enchantable/durability` and `enchantable/equippable`, preserving vanilla leggings
enchantment eligibility without general `leg_armor` or `trimmable_armor` membership.
This avoids diagnostic mixed items appearing as misleading native smithing examples
in recipe viewers; valid source-wise smithing still works. Swift Sneak uses the
actual legs slot. The only virtual
source tags are `piglin_safe_armor` and `freeze_immune_wearables`, queried live
on either source of a valid root; this is not arbitrary tag inheritance.
Future books require support from a source and retain native conflict/level
checks, independently of the factory's composition-time enchantment union.
Bare roots retain their diagnostic and cannot obtain normal book admission.
Source-wise smithing and missing-source recovery are described below.

## Temporary missing sources

The required early ItemStack persistence wrapper preserves the complete protected
root/source patch before native decoding can discard unavailable registrations.
The product-owned `mismatchedleggings:suspended_leggings` component stores bounded,
typed opaque data and synchronizes it without foreign component registrations.
Raw source-item presence is checked first. Full native success and strict valid
root/sources permit activation; missing sources and failed/partial native reads
stay inactive. Successful native normalization is accepted, not audited as a backup
of every possible change inside a foreign codec.

Admitted history keeps a non-wearing legs shell with no active attributes,
enchantments or source abilities. Movement and safe naming remain possible;
enchanting, repair, grindstone and smithing do not modify parked state. Bare
command-created roots do not gain this equipment permission. Conflicting edits
are retained without selecting a winner; malformed bounded archives remain opaque
in quarantine. Tooltips distinguish these inactive states.

Recovery retries on persistent load after restoring the missing registrations,
not every tick. It restores the saved live root plus allowed passive edits/removals,
without reapplying current composition policies, reconstructing from sources or
refilling resources. A required-hook installation failure refuses startup before
a world is loaded. The wrapper is product-namespaced and chains with Boots;
this does not migrate Boots schemas into Things or Leggings.

## Anvil and crafting repair

The native anvil supports naming/name removal, compatible enchanted books,
materials in the composed repair-material union, and an ordinary leggings donor
whose item matches either stored source. Native level/conflict, prior-work,
survival cost limits and payment rules remain in force; composition-time
overlevel merging is not repeated. Mixed/Mixed and unrelated donors are rejected.
Repair changes live root durability, never the stored sources or composed capacity.
Bare/malformed roots cannot use these repair/enchantment paths.

Player crafting grids, crafting tables and the Crafter accept one valid mixed
root plus one ordinary donor matching either source, in either input order.
They add both remaining durabilities and five percent of the root's capacity,
clamped to that capacity. Non-curse enchantments, custom names and prior-work
cost are removed; each curse retains its greater input level. There is no XP
reward. Other live-root components and ordered source snapshots are preserved.
NeoForge's native combine-repair veto is respected.

Repair materials are a composition-time snapshot, as in Boots: changing a repair
tag affects newly composed roots, not an existing root's stored union. Ordinary
source items continue to follow their own native tags. This does not add foreign
repair-table or arbitrary class-based item compatibility.

## Grindstone

One valid mixed root can be disenchanted in either input slot. Native removal
keeps curses and the root's custom name, recomputes prior work and leaves live
damage, capacity, source snapshots and other live components intact. A second
input may be ordinary leggings matching either source, in either physical order;
repair adds the donor's remaining durability and five percent of root capacity,
without importing a larger donor capacity. Unrelated, Mixed/Mixed, bare and
malformed inputs are rejected. NeoForge's native combine-repair veto is respected.

XP is paid only on extraction, through Minecraft's normal random payout. Since
composition duplicates enchantments into two outputs, inherited XP potential is
capped against the stored sources and halved; later positive potential above
that original merged baseline counts fully. An ordinary donor contributes its
full removable-enchantment potential. Closing or cancelling a preview pays no XP.

## Smithing

Loaded smithing recipes are evaluated separately against both ordinary sources.
Netherite upgrades transform eligible halves; native trims apply to eligible
source appearances rather than becoming an invisible root-only trim. One root
costs one addition and one template when the actual recipe requires it, even if
both halves change. Recipes requiring an empty template slot keep that requirement.
Dedicated native results for the mixed item take priority over the generic
source fallback. Opaque external effects and dedicated mod upgrade tables are
not emulated.

Source order stays fixed. Capacity, effective attributes, equipment and repair
materials are refreshed under the current server rules, while paid root damage
is retained (clamped to the new capacity), along with live enchantments, name and
prior work. Other projected fields follow their transformed source only if the
root still matches the old projection; live edits/removals win. Smithing does not
recreate resource wallets from source snapshots. No-op or invalid source results
do not count as changes. Removed/replaced recipes invalidate unpaid source-wise
previews, and each contributing distinct recipe is credited only on extraction.

Enchantments newly granted by a source upgrade also reach the live pair, without
restoring old source enchantments removed from the root. Repeated grants keep the
higher granted/live level rather than applying the composition-time level bonus.

## Runs and tests

Import the root Gradle project in IntelliJ. The four product-named configurations
are `MismatchedLeggings — Fabric Client/Server` and
`MismatchedLeggings — NeoForge Client/Server`. Each loader uses its own
`runs/client` and `runs/server`; these do not reuse Boots directories. Existing
Boots configurations retain their names and paths.

The [runtime guide](RuntimeTests/README.md) describes isolated packaged-artifact
checks, supported baselines and the hidden-client contract. The executable
rendering spike and its opt-in project have been retired after promotion to
maintained product profiles. Its [historical record](spikes/rendering/README.md)
remains; `-PleggingsSpike=true` no longer includes a project.
