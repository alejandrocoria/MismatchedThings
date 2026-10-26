# MismatchedThings

A family of independently installable Minecraft mods built from one repository.
This branch targets Minecraft 26.1.2 with Fabric and NeoForge.

## Mods

- [Mismatched Boots](mods/MismatchedBoots/README.md) combines two pairs of boots
  into two oppositely oriented mixed pairs. It is the only implemented mod so far.

## Development

Run Gradle from this repository root using the checked-in wrapper. Boots sources,
resources and test fixtures live under `mods/MismatchedBoots/`, with projects
under `:MismatchedBoots` such as `:MismatchedBoots:Fabric` and
`:MismatchedBoots:RuntimeTests:NeoForge`. Mod identity and release metadata live
in [mod.properties](mods/MismatchedBoots/mod.properties); root `gradle.properties`
contains the technical baseline, not a default mod identity.

See [the runtime test guide](mods/MismatchedBoots/RuntimeTests/README.md) for
maintained validation commands. Production artifacts remain separate Fabric
and NeoForge builds of Mismatched Boots; no shared family runtime mod is added.
