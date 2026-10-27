# MismatchedThings shared workbench

Small runtime mod owning the family workbench. Its version is independent of
Mismatched Boots. Boots artifacts bundle their loader-specific core through
native nested dependencies; users do not need a separate download.

Boots contributes admission and composition through API v1; it no longer owns
a second station. Permanent native coexistence/version-evolution controls now
cover Fabric and NeoForge .99/.114, including compiled API-v1 consumers and
unchanged Boots binaries across a core upgrade. Final consolidated/manual
acceptance remains before publication. No mixed helmets or other sibling product
is included; synthetic consumers and their additive API exist only in tests.

## Ownership

- `Common`: station implementation, API and family resources shared between loaders.
- `Fabric` / `NeoForge`: independent loader artifacts and entry points.
- `RuntimeTests`: disposable packaged-core controls; see its [guide](RuntimeTests/README.md).

The block, item, menu and station recipe type use
`mismatchedthings:mismatched_workbench`. The core owns acquisition, loot,
advancement, axe membership and presentation, but does not compose armor itself.

## Contribution boundary

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
