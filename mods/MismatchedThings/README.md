# MismatchedThings shared core

Small runtime mod owning the family workbench and focused shared utilities. Its version is independent of
the armor products. Boots and the in-development Leggings artifacts bundle their loader-specific core through
native nested dependencies; users do not need a separate download.

Client and server must use the same loader. NeoForge rejects remote non-NeoForge
clients during configuration because their static registry IDs cannot be safely
synchronized by its protocol. Fabric API already rejects clients that do not
support Fabric registry synchronization. Integrated singleplayer connections
share registries and are unaffected.

Boots contributes admission and composition through API v1; it no longer owns
a second station. Permanent native coexistence/version-evolution controls now
cover Fabric and NeoForge .99/.114, including compiled API-v1 consumers and
unchanged Boots binaries across a core upgrade. Consolidated automated integration,
viewer and dedicated networking checks have passed. The owner also accepted the
final ordinary-client checklist on Fabric and NeoForge on 2026-10-27, completing
the shared workbench extraction/namespace plan. Leggings now has its own source
schema, composition factory and its own workbench recipe. Synthetic
consumers and their additive API exist only in tests.

Leggings' [final packaged matrix](../MismatchedLeggings/RuntimeTests/README.md#final-packaged-product-acceptance)
also sequences these maintained core/consumer controls alongside its product
profiles, using pinned unchanged Boots releases. Its consolidated owner acceptance
passed on both loaders on 2026-11-02, separately from the completed workbench
extraction above.

## Ownership

- `Common`: station implementation, shared utilities, API and family resources shared between loaders.
- `Fabric` / `NeoForge`: independent loader artifacts and entry points.
- `RuntimeTests`: disposable packaged-core controls; see its [guide](RuntimeTests/README.md).

The block, item, menu and station recipe type use
`mismatchedthings:mismatched_workbench`. The core owns acquisition, loot,
advancement, axe membership and presentation. Products still own armor construction,
state, policies and third-party behavior; Things does not contain a general armor engine.

## Shared utilities (since 1.1.0)

- `api.config`: `TomlConfigIO`, `DynamicConfigPart`, `ConfigEntry` and
  `EnumConfigEntry`. Each consumer supplies its logger, paths, schema, defaults and
  state. Missing/invalid entries normalize to consumer defaults; unreadable files
  stay untouched, and malformed TOML is replaced only after a successful backup.
  Lifecycle hooks, reload/synchronization and product config names remain local.
- `api.tooltip.TooltipSeparator.INSTANCE`: append this transient marker to a
  tooltip wherever a section boundary is wanted. Things installs the Fabric and
  NeoForge conversions and solid-line renderer; no consumer-side renderer or
  registration is needed. Server-side collection stays safe and text copying
  falls back to an ordinary blank row. Do not persist the marker as item data.
  The shared marker is text-only; NeoForge converts it to a loader-local image
  payload. Do not make the marker implement both text and image interfaces:
  viewers such as REI classify those independently and would process it twice.
- `api.composition`: `EnchantmentMerger`, `RepairableComposer`, `DurabilityMerger`
  and `RepairCostMerger`, plus `DurabilityPolicy` and `RepairCostPolicy`. These
  read inputs without mutating them; the consumer chooses which operations to
  call and builds its own outputs. Durability averages round down, repair-cost
  averages round up, repairable materials form a union, and enchantments retain
  the existing equal-level upgrade/max-level-plus-one rule (capped at 255).

## Further composition utilities (since 1.1.0)

- `AttributeMerger.merge(first, second, policies)` composes supplied modifier lists
  by registered attribute/modifier identity. It retains slot/display metadata,
  separates divergent identities and treats absent total multipliers as factor one.
  Loader-effective queries and canonicalization remain the consumer's responsibility.
- `ComponentValueMerger` composes supplied component values under `ComponentRule`,
  `ComponentPolicy` and `ComponentNumericKind`. The consumer resolves supported
  rules and supplies a warning sink, retaining ownership of logging/deduplication,
  structural exclusions and defaults. Integer averages floor without overflow;
  invalid numeric inputs reject, and unrepresentable results retain owner values.
  Damage-resistance equivalence/intersection keeps the established bounded behavior.

## Item geometry utilities (since 1.1.0)

`api.client.geometry` supplies canonical half-plane clipping and bounded polygon
tessellation: `QuadClipper`, `PolygonTessellator`, `BakedQuadClipper` and
`BakedPolygonTessellator`. Inputs carry rendered positions, UVs and separate
clipping coordinates; traced cuts retain vertex origins for consumer metadata
interpolation. Baked outputs retain native direction/material identity.
Unsupported cuts are reported before consumer mutation. These are client-side
operations, not equipment models, caches, renderer mixins or loader adapters.
Boots and Leggings own those boundaries and their pair-wide fallback decisions.

Current Boots and Leggings code requires Things `>=1.1.0 <2.0.0`. Earlier compiled consumers of the
1.0 workbench API remain supported. The extraction changes neither saved IDs nor
configuration formats; no world/item migration is needed. Boots' existing local
composition methods delegate to the new helpers without changing their signatures.
Renderer classes and
loader hooks under `internal` are not consumer API.

## Contribution boundary

### Optional Forbidden Arcanus operations (since 1.1.0)

`api.compatibility.forbiddenarcanus` provides the immutable `ForbiddenModifiers`
value/codecs and `ForbiddenModifierService`: exact native Eternal/Soulbound
reading, projection, recipe inspection and title construction. NeoForge binds
the optional native API lazily; Fabric and mod absence are safe no-ops. No
Forbidden Arcanus dependency is bundled or required.

Products register their own state IDs and retain validity/admission, composition
ordering, smithing payment/revalidation and tooltip listeners. A shared smithing
decision is pass, handled denial or a native recipe/desired-state match, never a
product output or a purchase. Finalization works on disposable outputs; consumers
must reject failures and validate their own state before publishing a result.
There is no automatic adoption of old products merely by selecting a newer core.

`applyExisting` applies only a supported native scalar holder already decoded on
a disposable output, without a world/registry lookup. Native structural-name
changes remain visible to the caller; product-owned adoption validates and
normalizes them while preserving custom names and unrelated live data.

`WorkbenchRecipe.assemblePair(input, registries)` supplies operation-local context
from both menu preview and take-time reconstruction. Its default delegates to
the original `assemblePair(input)`, retaining compiled API-v1 consumers. The
default client preview uses the contextual route; consumers with synchronized
policies may still override it. The context-free vanilla Recipe entry point stays
available. Consumers must not inject into the core menu to obtain registries.

### Optional Neo Vitae infusion (since 1.1.0)

`api.compatibility.neovitae.NeoVitaeInfusionService` exposes only new-orientation
creation from two supplied sources and exact existing-root state preservation.
It keeps the four native Spiritus/Blood Mending components: maximum capacity,
conserved compatible-type charge and owner amount/type on positive conflicts.
Recomposition preserves exact live values/presence rather than averaging again.

Consumers own source extraction, transaction payment and strict final output
validation. Both operations mutate only a disposable output; sources and the
previous root are read-only. Discard failed outputs rather than publishing a
partial tuple. No product data, tag whitelist or recipe is registered by this API.

The NeoForge provider binds optional native component codecs once after component
registration, without linking Neo Vitae classes or installing an energy capability.
Absent/unsupported native bindings and Fabric are no-ops; unsupported bindings log
a warning. With a valid binding, malformed installed containers reject the
operation. No Neo Vitae dependency or replacement wallet is bundled.

Boots currently delegates to this service. Leggings' composition integration is
separate; simply selecting this core does not reinterpret existing mixed items
or remix their stored resources.

### Optional recipe viewers (since 1.1.0)

Things registers one JEI/REI category, `mismatchedthings:mismatched_workbench`,
titled **Mismatched Workbench**. Optional APIs are compile-only; neither viewer is
required on either endpoint. Products keep their own optional plugins and contribute
independently; Things contains no armor-product lookup or composition rules.

- `api.client.viewer.WorkbenchExample` holds defensive single-item snapshots of
  four equally sized, nonempty linked slots. It has no viewer API dependency.
- JEI plugins call `api.client.viewer.jei.WorkbenchJeiRecipes.replace(owner, example)`
  on the client thread with their unique contribution ID and an optional example.
  Empty removes only that owner's cycle. Products subscribe to their own recipe/
  synchronized-rule changes, clear on unavailable authority, and dispose listeners
  when their JEI runtime stops. Things handles registration, replacement and pending
  contributions regardless of plugin callback order. `TYPE` identifies the shared category.
- REI plugins register their own `DynamicDisplayGenerator` against
  `api.client.viewer.rei.WorkbenchReiDisplay.CATEGORY` during REI's normal reload.
  Return `WorkbenchReiDisplay.create(...)` from only the main `generate` callback;
  generate from current product authority and return empty while unavailable.
  REI owns generator reload/disposal. Do not register another category or workstation.

Product candidate selection still respects viewer visibility/subtypes; outputs
come from actual loaded product recipes and synchronized product rules, never a
new factory recipe invented by Things. No recipe transfer is provided.

Published Boots 1.0.0 uses the old `mismatchedboots:mismatched_workbench` category
and example classes. Its existing API remains supported: it can coexist with
Leggings and Things 1.1.0, but keeps a separate, identically titled legacy tab.
There is no core bridge into Boots internals and no saved-data migration.

Packaged current-product and legacy-consumer viewer checks live in the
[shared viewer profile](../MismatchedLeggings/RuntimeTests/README.md#optional-jeirei-and-user-guide-acceptance).

### Workbench recipes

Use `games.alejandrocoria.mismatchedthings.api`:

1. Register a namespaced contributor with `MismatchedWorkbench.registerContributor`
   during initialization. Predicates receive a defensive, single-item stack.
   A duplicate owner ID is an error. Predicates should not depend on other
   contributors' registration order.
2. Implement `WorkbenchRecipe` and register a concrete serializer/data recipe
   in the consumer's namespace. Use `WorkbenchInput` and ordered
   `WorkbenchResult` outputs. Input records expose the supplied recipe inputs;
   recipe implementations must not mutate them. Result construction/access
   copies the output stacks.
3. Admission alone does not make pairs valid. The loaded recipe determines
   matching and assembly. Without a consumer recipe, the station creates nothing.
4. If previews need client-specific policies, override `assemblePreview` and
   obtain that context in the consumer. Do not put policy/config objects into
   the shared contract or reference client-only classes from server assembly.

`MismatchedWorkbench` exposes the station ID, registered block/item/menu and
typed recipe type. Registry-backed access requires completed registration.
Implementation classes in `internal` are not a supported consumer API.
The v1 surface is exercised by real Boots integration, synchronized previews,
recipe reload and JEI/REI acceptance. Preserve it when making additive 1.x changes.

The menu uses ordinary Minecraft transport, commits both inputs once when the
first output is taken, and retains the other paid output until collected or
returned by normal close handling. The block has no private saved inventory.

Compatible additive core updates must retain the previous consumer API. A new
product using that API must not require an old product to update; incompatible
major versions may require an intentional consumer update.
