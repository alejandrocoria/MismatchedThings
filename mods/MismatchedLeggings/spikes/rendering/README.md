# Retired equipped-rendering spike

Retired in Leggings block M on 2026-11-01. The opt-in Gradle projects, marker
fixture, interceptors and duplicate rendering implementation have been removed.
`-PleggingsSpike=true` no longer adds any project. No spike code is shipped.

The research established native per-leg rendering plus CPU-split waist geometry,
reference-space ownership for transformed waist descendants, and buffered
pair-wide preflight/fallback. Its retained results and limitations are in the
[rendering exploration](../../local-docs/RENDERING-EXPLORATION.md).

Equivalent maintained checks now exercise factory-created products and production
renderers, not marked vanilla stacks:

- [Equipped profiles](../../RuntimeTests/README.md#equipped-rendering): vertex
  area/UV/normal and hierarchy controls, independent animation, queued reuse,
  pose variants, material metadata, dye/trim/foil, fallback and cache/reload.
- The optional NeoForge Ice and Fire graph covers all 30 native replacements,
  including the 13 decorated waists.
- [Item profiles](../../RuntimeTests/README.md#item-rendering): actual GUI, hands,
  ground/frame contexts, anatomical orientation and item-model fallback.

Earlier source and Gradle commands remain recoverable from Git history; they are
not current instructions. Existing ignored spike build outputs are historical
local evidence only and are not consulted by normal verification. See the
[implementation record](../../local-docs/INITIAL-IMPLEMENTATION.md#m--packaged-consolidation-and-completion)
for final acceptance and the [runtime guide](../../RuntimeTests/README.md) for
runnable commands. Owner visual acceptance is tracked separately from automation.
