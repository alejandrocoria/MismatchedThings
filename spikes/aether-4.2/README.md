# Aether II alpha.4.2 bounded spike

Opt-in NeoForge experiment, not production compatibility. Neither the normal
project graph nor the shared artifact catalog is changed. Both current packaged
products are loaded; the old baseline resolves through the existing artifact
catalog and alpha.4.2 uses its exact official URL and SHA-256.

From the repository root, run these phases **in order**, without concurrent runs:

```powershell
foreach ($phase in @('old', 'absent', 'control', 'candidate', 'restart', 'client')) {
    .\gradlew.bat --init-script spikes/aether-4.2/init.gradle :Aether42Spike:runSpike "-PaetherSpike=$phase"
    if ($LASTEXITCODE -ne 0) { throw "Aether spike failed: $phase" }
}
```

Generated reports and worlds live in this directory's ignored `build/` tree.
No owner indev JAR, configuration or world is used. The client starts hidden and
muted, with NeoForge's early window disabled; it produces no screenshot gallery.

| Phase | Purpose |
| --- | --- |
| `old` | Generate 12 real alpha.4.1 roots with both products/orientations, native charms/tier, damage, names, attributes and an explicit source set override. |
| `absent` | Load/save without Aether; verify two saves preserve suspended data and the converter does nothing. |
| `control` | Load old roots under alpha.4.2 with unchanged production and no prototype hooks. |
| `candidate` | Explicitly preprocess owned input before `ItemStack.CODEC`, compare complete serialized roots, restore missing-source archives, exercise the native set engine and server tooltip boundaries. |
| `restart` | Load candidate saves in another JVM, confirming active data and idempotence. |
| `client` | Exercise both relocated native tooltip producers, independent containment, normal/advanced flags and native tier reuse with a hidden client. |

`LocalConversion` is deliberately called by the experiment before decoding. It is
**not** installed into production or the world's item codec. Only the owned root
component and the two direct source patches are converted; unrelated custom NBT
is opaque. When restoring, only the archive's original owned patch is converted,
not its conflicts/projection. Native items and absent sources are not migrated.

`SetOperandMixin` proves that a temporary native `ArmorSet(requested)` operand
can extend recognition before the native null gate. It does not write equipment,
sources or attributes, and the native count/effect engine remains responsible.
Its fixed test hooks are not production fail-closed guards. The later adaptation
still needs final-transformed local provenance proofs and per-domain refusal
tests, including sibling chains.

The client experiment proves producer placement/containment and native tier
availability, not the finished mixed formatter/layout. Existing production
presenters still need their record readers and guarded targets updated.

## Known native server boundary

The relocated common attribute event is server-safe. Full `getTooltipLines`
still reaches bundled Nitrogen's `TooltipListeners.addAbilityTooltips`, which
references client-only `I18n`: both a native Neptune item and a mixed item fail.
The candidate phase records this exact native control separately; it does not
claim full server tooltip acceptance or repair Nitrogen. Other linkage errors
are not accepted. The two existing native startup `Toast` errors also remain.

See the private root `local-docs/AETHER-II-ALPHA-4.2-COMPATIBILITY-UPDATE.md` for
measured results, scope and the recommended production boundary. Once maintained
production tests replace these experiments, remove this spike rather than
keeping a parallel compatibility implementation.
