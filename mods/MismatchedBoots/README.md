# MismatchedBoots

MismatchedBoots combines any two pairs of boots into two oppositely oriented
mixed pairs on Fabric and NeoForge.

## Crafting

Craft a **Mismatched Workbench** with six planks (any combination), shears, and
a diamond in this 3x3 arrangement:

```text
plank  shears   plank
plank  diamond  plank
plank  empty    plank
```

Place one ordinary, feet-equippable boot stack in each workbench input. The left
input supplies the left boot of the primary output; the right supplies its right
boot. Both inverse pairs are visible as separate results. Taking either consumes
the two inputs once; the other result remains available until taken or returned
when the screen closes. Existing MismatchedBoots cannot be composition inputs.

Crafting grids and Crafters do not combine boots. They can instead repair an
existing mixed pair with an ordinary boot matching either stored source.

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

An anvil repairs the live mixed pair with a material or ordinary boot matching
either source, and supports renaming and compatible enchantments. A grindstone
can disenchant one mixed pair or repair it with a matching ordinary boot; its
XP payout is bounded and occurs only on extraction. Smithing applies loaded
trim and upgrade recipes to each eligible source, using one template and one
addition for the mixed result. Combining two mixed pairs in an anvil or
grindstone remains unsupported, as do opaque smithing side effects outside the
assembled item's identity and components.

## Rendering and compatibility

Item views display both source pairs side by side. Equipped standard humanoid
armor is split by leg. Custom item or armor renderers are best-effort: when a
safe split is unavailable, the renderer may show the complete owner appearance
and the tooltip still identifies the left and right sources.

Compatibility is automatic when other mods use normal feet-equipment semantics,
data components, attributes, durability, enchantments, and standard equipment
models. Behavior implemented only in a custom source `Item` class cannot be
generically transferred.

The bare registry item has no valid source data, so it is intentionally absent
from creative tabs. Commands that create a bare stack produce an invalid item
with a diagnostic tooltip rather than inventing source boots.
Such a bare stack cannot be equipped through ordinary use, armor slots, or a
dispenser; valid crafted pairs remain equippable.

## Development

This mod lives in the [MismatchedThings repository](../../README.md). Run the
Gradle wrapper from the repository root, not this directory. See
[the runtime test guide](RuntimeTests/README.md) for validation commands and paths.

The production client tasks are `:MismatchedBoots:Fabric:runClient` and
`:MismatchedBoots:NeoForge:runClient`. Generated IntelliJ launch names identify
the mod, loader and side. Release metadata belongs to [mod.properties](mod.properties).

The publication entry is `:MismatchedBoots:publishMod` (both loaders). Package
verification, the pinned runtime artifact catalog and startup/network acceptance
are owned here as well; their outputs are under this directory's `build/`.
