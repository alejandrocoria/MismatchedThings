# MismatchedThings

A family of independently installable Minecraft mods built from one repository.
This branch targets Minecraft 26.1.2 with Fabric and NeoForge.

## Mods

- [Mismatched Boots](mods/MismatchedBoots/README.md) combines two pairs of boots
  into two oppositely oriented mixed pairs. It is the only armor product so far.
- [MismatchedThings core](mods/MismatchedThings/README.md) owns the shared
  workbench and is bundled inside each Boots artifact; no separate download is needed.

## Development

Run Gradle from this repository root using the checked-in wrapper. Boots sources,
resources and test fixtures live under `mods/MismatchedBoots/`, with projects
under `:MismatchedBoots` such as `:MismatchedBoots:Fabric` and
`:MismatchedBoots:RuntimeTests:NeoForge`. Mod identity and release metadata live
in [mod.properties](mods/MismatchedBoots/mod.properties); root `gradle.properties`
contains the technical baseline, not a default mod identity.

Publication is mod-scoped: `:MismatchedBoots:publishMod`; there is no root
`publishMod` task. Package checks, pinned test artifacts and startup/network
orchestration also belong to `:MismatchedBoots`, with outputs in that mod's
`build/` directory. The root retains six explicit verification conveniences:
`prepareTestModArtifacts`, `verifyTestModArtifacts`, `verifyPackagedDependencies`,
`verifyPackagedDependencyChecks`, `verifyLoaderServerStartup` and
`verifyLoaderNetworking`. Each delegates only to its Boots counterpart.

See [the runtime test guide](mods/MismatchedBoots/RuntimeTests/README.md) for
maintained validation commands. Production artifacts remain separate Fabric
and NeoForge builds of Mismatched Boots, each with its independently versioned
loader-specific core nested inside. Core sources and standalone controls live
under `mods/MismatchedThings/` and `:MismatchedThings`.
