# MismatchedBoots

MismatchedBoots combines two pairs of boots into two oppositely oriented
mixed pairs on Fabric and NeoForge.

## Installation

Install it on both the client and the server, using the same mod loader on both
sides. Cross-loader connections are not supported. Fabric also requires Fabric API.

MismatchedThings is bundled; no separate core download is required. Other
Mismatched armor mods are optional: installed together, they share one workbench
while keeping their own items and configuration.

## Crafting

Craft a **Mismatched Workbench** with six planks (any combination), shears and a
diamond in this 3x3 arrangement:

```text
plank  shears   plank
plank  diamond  plank
plank  empty    plank
```

Place one ordinary pair of boots in each workbench input. The left input
supplies the left side of the primary output; the right supplies its right side.
Both inverse pairs are visible as separate results. Taking either consumes the
two inputs once; the other result remains available until taken or returned
when the screen closes. Existing mixed pairs cannot be composition inputs.

Crafting grids and Crafters do not combine boots. They can instead repair an
existing mixed pair with ordinary boots matching either stored source.

## Combined behavior

- By default, maximum and remaining durability are the conservative average of
  the two sources, rounded down. Mending and Unbreaking operate normally afterward.
- Attributes follow configurable per-attribute policies. Common vanilla bonuses
  average compatible modifiers; unknown attributes retain the left source's
  contribution in each orientation by default, rather than being halved.
- Enchantments from both sources are copied to both results. Equal levels gain
  one level, capped at the enchantment's normal maximum plus one.
- Unknown components are inherited from the left source of each result by
  default; supported component policies can be configured on the server.

An anvil repairs the live mixed pair with a material or ordinary boots
matching either source, and supports renaming and compatible enchantments.
A grindstone can disenchant one mixed pair or repair it with matching ordinary
boots; its XP payout is bounded and occurs only on extraction. Smithing applies
loaded trim and upgrade recipes to each eligible source, using one template where
required and one addition for the mixed result. Combining two mixed pairs in an
anvil or grindstone remains unsupported, as do opaque smithing side effects outside
the assembled item's identity and components.

Enchantments newly granted by a source upgrade also reach the live pair, without
restoring old source enchantments removed from the root. Repeated grants keep the
higher granted/live level rather than applying the composition-time level bonus.

## Rendering and compatibility

Item views combine both source appearances; the artwork is front-facing, so the
wearer's left appears on the viewer's right.

Equipped standard humanoid armor is split by leg, preserving native animation,
dye and trims.

Custom item or armor renderers are best-effort: when a safe split is unavailable,
the renderer may show a complete source appearance instead. Unsupported item
geometry can fall back to two superimposed complete source models. The tooltip
still identifies the left and right sources.

Compatibility is automatic when other mods use normal equipment semantics,
data components, attributes, durability, enchantments and standard equipment
models. Behavior implemented only in a custom source item or event cannot be
generically transferred.

The bare registry item has no valid source data, so it is intentionally absent
from creative tabs. Commands that create a bare stack produce an invalid item
with a diagnostic tooltip and a workbench hint rather than inventing source boots.
Such a bare stack cannot be equipped through ordinary use, armor slots or a
dispenser; valid crafted pairs remain equippable.

## Optional recipe viewers

JEI and REI show examples in the shared **Mismatched Workbench** category supplied
by Things. Each current family product contributes its own inputs and both ordered
results only when installed, using loaded recipes and synchronized server rules.
Published Boots 1.0.0 keeps its separate legacy category when used with newer
family products. There is no automatic recipe-transfer integration.

## Development

This mod lives in the [MismatchedThings repository](../../README.md). Run the
Gradle wrapper from the repository root, not this directory. See
[the runtime test guide](RuntimeTests/README.md) for validation commands and paths.

The production client tasks are `:MismatchedBoots:Fabric:runClient` and
`:MismatchedBoots:NeoForge:runClient`. Generated IntelliJ launch names identify
the mod, loader and side. Each loader has its own `runs/client` and `runs/server`,
separate from other family mods. Mod identity and version live in
[mod.properties](mod.properties).

Build both loader artifacts with `:MismatchedBoots:Fabric:assemble` and
`:MismatchedBoots:NeoForge:assemble`.

For promotional photos and Replay Mod recordings, use the separate
[Fabric media client](media/README.md), not the ordinary indev client.

The publication entry is `:MismatchedBoots:publishMod` (both loaders). Package
verification, the pinned runtime artifact catalog and startup/network acceptance
are owned here as well; their outputs are under this directory's `build/`.

### Release changelogs

All family mods use one change list for the upcoming release, without version
headings or accumulated beta history. Use `Added:` followed by the new functionality
as a natural continuation of "Added", `Changed:` for the new observable behavior,
and `Fixed:` for the problem that occurred, normally without implementation details.
New compatibilities belong in a separate generated section, not duplicate `Added:`
entries. Only compatibilities added in that release belong there; an empty loader
list must omit the entire section.

[doc/changelog.md](doc/changelog.md) is the shared release template. The
[Fabric](doc/compatibility/fabric.md) and [NeoForge](doc/compatibility/neoforge.md)
fragments contain only Markdown list entries for compatibility added in the
release being prepared, not a cumulative catalog. Replace their contents for
each release; leave a fragment empty when that loader has no new compatibility.

Each loader's `processDocs` task inserts its fragment under `Added compatibility`,
with no blank line between the heading and list. An empty or whitespace-only
fragment omits the entire section. Both CurseForge and Modrinth publication
tasks depend on this generation and use the resulting `build/doc/changelog.md`.
To preview both changelogs without publishing, run
`./gradlew :MismatchedBoots:Fabric:processDocs :MismatchedBoots:NeoForge:processDocs`.
