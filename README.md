# MismatchedBoots

MismatchedBoots combines any two pairs of boots into two oppositely oriented
mixed pairs on Fabric, NeoForge, and Forge.

## Crafting

Place exactly two count-one, feet-equippable boot stacks anywhere in a 2x2 or
3x3 crafting grid. The first stack in left-to-right, top-to-bottom reading order
provides the left boot of the primary result; the second provides its right
boot. Taking that result consumes both inputs and returns the inverse pair as a
crafting remainder. A Crafter dispenses both results.

Existing MismatchedBoots cannot be used as inputs. The recipe is special and is
not automatically placed by the recipe book.

## Combined behavior

- Maximum and remaining durability are the conservative average of the two
  sources, rounded down. Mending and Unbreaking operate normally afterward.
- Attributes receive half of each source contribution.
- Enchantments from both sources are copied to both results. Equal levels gain
  one level, capped at the enchantment's normal maximum plus one.
- Unknown components are inherited from the left source of each result rather
  than guessed or merged.

Direct ingredient repair, combining two mixed pairs in an anvil, smithing, and
grindstone processing are intentionally unsupported. Renaming and normally
compatible enchanted books remain supported.

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
