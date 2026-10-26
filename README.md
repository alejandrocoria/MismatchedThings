# MismatchedThings

A family of independently installable Minecraft mods built from one repository.
This branch targets Minecraft 26.1.2 with Fabric and NeoForge.

## Mods

- [Mismatched Boots](mods/MismatchedBoots/README.md) combines two pairs of boots
  into two oppositely oriented mixed pairs. It is the only implemented mod so far.

## Development

Run Gradle from this repository root using the checked-in wrapper. Boots sources,
resources and test fixtures live under `mods/MismatchedBoots/`; its existing
logical Gradle paths (`:Common`, `:Fabric`, `:NeoForge`, `:RuntimeTests:Fabric`
and `:RuntimeTests:NeoForge`) remain unchanged during the first layout stage.

See [the runtime test guide](mods/MismatchedBoots/RuntimeTests/README.md) for
maintained validation commands. Production artifacts remain separate Fabric
and NeoForge builds of Mismatched Boots; no shared family runtime mod is added.
