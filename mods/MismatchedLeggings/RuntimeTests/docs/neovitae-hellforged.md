# Neo Vitae Hellforged gameplay and presentation

Maintained acceptance for both products. The gameplay selection is dedicated-server-only;
the presentation selection adds a separate-JVM reopen and a hidden/muted client. Use the current
packaged JARs and the shared catalog's `neovitae` dependency closure, never indev
mods, generated replacement classes or research init scripts. NeoForge `.114` is
the native baseline; the pinned Modonomicon requires `.109` or newer.

## Gameplay commands

From the repository root, with Java 25 and prepared artifact caches:

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyNeoVitaeHellforged --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyNeoVitaeHellforged -PleggingsBoots=true --offline --console=plain
.\gradlew.bat :MismatchedBoots:RuntimeTests:NeoForge:verifyNeoVitaeComposition --offline --console=plain
```

Run sequentially. The first two use the existing Leggings profile runner, selecting
standalone Leggings or Leggings/current Boots. The last retains Boots' existing
standalone infusion/lifecycle/restart gate and adds a small Hellforged registration/
inheritance control; it is not another full ritual matrix.

Focused default/config and native marker-read tests:

```powershell
.\gradlew.bat :MismatchedBoots:Common:test --tests '*HellforgedDefaultsTest' :MismatchedLeggings:Common:test --tests '*HellforgedDefaultsTest' :MismatchedThings:NeoForge:test --tests '*HellforgedMixinPluginTest' --offline --console=plain
```

The native guard test obtains its hash-checked artifact through the shared artifact
bindings. Optional absence can reuse Boots' `verifyNeoVitaeCompositionAbsent` and
Leggings' ordinary `runSkeletonServer`; these runs must not stage Neo Vitae or its
dependencies. No native-present `.99` result is implied.

## Evidence and boundaries

Leggings' profiles write below
`NeoForge/build/reports/26.1.2.114/neoVitaeHellforged[-boots]/` and
`NeoForge/build/verification/26.1.2.114/neoVitaeHellforged[-boots]/`.
Fresh `server.ok`, `neovitae-hellforged-server.txt`, `neovitae-sentient-server.txt` and the runner's `matrix.ok`
are required, without `server.failure.txt` or runtime `ERROR` diagnostics.

The maintained cases exercise:

- Native positive controls; actual paid workbench extraction in both orders and
  companion return; neither/one/both upgraded inputs; raw/effective averaged bonus
  identities, creation fire intersection and direct root's full native bonus.
- Actual ritual layout/master, activation and separate operation EV payment,
  exact gem/ingot consumption and refusal of another operation. Activation is not
  refunded when the ritual has no eligible target.
- Native direct root forging preserves Spiritus charge, Blood Mending, damage and
  source history; ordinary recomposition does not inflate bonuses or reset state.
- A representative inverse created under `OWNER`, later queried under `SHARE`,
  is recognized before/after recomposition without writing its absent marker.
  Explicit false, `OWNER`/`OMIT`, malformed and bare items do not gain fallback
  recognition. Suppressed inherited upgrades cannot buy a useless repeat operation.
- Logical client-thread checks exercise unsynchronized, synchronized `OWNER` and
  synchronized `SHARE` views without replacing server authority. These are rule/
  native-helper checks, not a graphical client or TCP claim.
- A refused optional binding declines only new mixed ritual eligibility and the
  legacy reader; native items, existing set tags and separate Spiritus binding work.
- `neovitae-sentient-server.txt` compares actual level-one Aquatic Agility, Gills,
  Lava Strider, Unbounded Flight and Deus Sanguinis against native armor, each mixed
  slot and both together in both orientations (nine equipment combinations with
  Boots installed). Missing-set negatives, genuine submersion, Fire Resistance/
  crouching, 5-EV flight ticks/empty-EV refusal and one chestplate XP operation are
  retained. Native Deus evolution validates its layout/activation and operation
  debit, raising capacity from 500 to 600 and adding Blood Mending to both worn
  roots without changing their sources or unrelated live state.

The ritual fixture waits for an actual entity-query witness in its forced chunk,
with a bounded timeout, rather than guessing a fixed startup delay. The witness is
discarded before operation checks. No owner worlds or manual fixtures are created.

Boots' existing report directory additionally contains
`neo-vitae-hellforged-smoke.txt`. Its other composition/lifecycle markers retain
their existing meanings.

This gameplay gate does not certify direct-root icon tint, tooltips, every native
tome/progression interaction, ritual animation/cadence or a new saved-container reopen.
No screenshots or automated client are launched by the gameplay selection.

## Presentation and persistence

```powershell
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyNeoVitaePresentation --offline --console=plain
.\gradlew.bat :MismatchedLeggings:RuntimeTests:NeoForge:verifyNeoVitaePresentation -PleggingsBoots=true --offline --console=plain
```

Use the same prepared artifacts; run sequentially. This extends the existing
packaged profile runner, not the research init script or another release graph.
Evidence is under `NeoForge/build/reports/26.1.2.114/neoVitaePresentation[-boots]/`
and the corresponding `build/verification/` directory. The runner requires fresh
`server.ok`, `client.ok`, `matrix.ok`, plus:

- `neovitae-hellforged-lifecycle.txt` and `neovitae-hellforged-saved.json`:
  12 full samples per slot in a real barrel: native control, both orientations of
  one inherited/direct source, both inherited/direct sources, legacy inverse,
  explicit false, and ordinary full-model fallback. Direct/inherited roots retain
  charge, Blood Mending, damage and glint through paid trim smithing and anvil rename;
  repeated recomposition retains bonus identities/amounts.
- `neovitae-hellforged-restart.txt`: another game JVM loads that same barrel and
  compares complete saved roots/history before querying native recognition and
  effective attributes. No recipe recreation or silent replacement is used.
- `neovitae-hellforged-client.txt`: a client opens a disposable copy of the saved
  world, waits for actual synchronized product rules, and reads its barrel. Native
  model resolution compares GUI/both-hand tint submissions and split/fallback GUI
  states. Direct-root views add only eligible temporary native markers; inherited
  asymmetry, explicit false and synchronized `OWNER`/missing-rule cases are retained.
  Native equipped layer textures/colors/trims/glint and normal/Shift/F3+H root
  tooltips are checked without changing complete root/source state. All checks run
  again after actual resource reload.

The client reuses the test-only pre-GLFW hidden-window hook, muted disposable options
and disabled NeoForge early window. No screenshot gallery or owner world is created.
The client/reopen evidence is not a TCP/pixel certification or the owner's consolidated
acceptance. The gameplay selection above owns native set-upgrade/Deus regression;
the ordinary-world and same-loader packaged server checks below remain owner acceptance.
The entire executable `neovitae-update` spike is retired; no init script,
candidate hook, observer or alternative test entry point is used.

## Ordinary-world owner checklist

**Acceptance:** the owner confirmed the complete checklist below on 2026-11-17,
including the packaged same-loader client/server reconnect. Together with the
automatic gates and full spike retirement, this closes the bounded joint extension.

Use the joint NeoForge environment, with a disposable world or your existing indev
test world. There is no prepared manual-world download or copy step. Commands below
are native Neo Vitae commands and require cheats/operator permission, not mod-owned
test commands.

1. Combine plain Sentient and diamond pieces for each slot; repeat with one/two
   Hellforged inputs. Inspect both outputs: averaged inherited bonus values, native
   Hellforged tooltip and unchanged inherited color split. One fireproof source
   alone does not grant root fire protection. Give otherwise identical inputs a
   distinguishing name/trim when necessary.
2. Use the native ritual diviner/book for the **Hellforging** layout and activate
   its master stone with an appropriate activation crystal. Drop one eligible mixed
   item, one `neovitae:prismatic_spiritus_gem` and one `neovitae:ingot_hellforged`
   inside its range. Check payment, native transformation/tint, root fire protection
   and refusal of a second operation; an ordinary non-Sentient control is ineligible.
   Retain some Spiritus charge, Blood Mending/damage or another root edit in one case.
   The native diviner `neovitae:ritual_diviner_deus` covers the advanced layouts.
   For disposable test-network preparation, `/neovitae anima-network @s set 2000000`
   supplies EV; `query` shows the balance and `set 0` lets you check empty-EV flight.
   Activation and operation costs are separate; activating without a valid target
   need not refund the activation cost.
3. Equip original Sentient helmet/chestplate with mixed feet/legs; compare each
   new upgrade with the original feet/legs and remove a required piece as a negative.
   Upgrades are on the **equipped chestplate**, never installed on the mixed pieces.
   To avoid XP grinding in a test world, use native commands:

   ```mcfunction
   /neovitae upgrade @s points set-cap 700
   /neovitae upgrade @s upgrade set neovitae:aquatic_agility 200
   ```

   The second command sets that upgrade's XP. Use these level-one values, preferably
   with a separate native chestplate for each effect (or set the previous upgrade's
   XP to `0` before trying another):

   | Upgrade ID | Level-one XP | Observe |
   | --- | --- | --- |
   | `neovitae:aquatic_agility` | 200 | Swimming is faster with a complete recognized set. |
   | `neovitae:gills` | 600 | Air replenishes while genuinely submerged. |
   | `neovitae:lava_strider` | 600 | Walk on lava with Fire Resistance; crouching or losing the potion releases support. |
   | `neovitae:unbounded_flight` | 1 | Survival flight consumes EV and stops when it runs out; native capacity gate is 600. |
   | `neovitae:deus_sanguinis` | 1 | Blood Siphon/Shield bonuses appear with the set; native capacity gate is normally 700. |

   For Siphon/Shield, vanilla `/attribute @s neovitae:blood_siphon get` and
   `/attribute @s neovitae:blood_shield get` make the comparison explicit.
   `set-cap` only bypasses progression for this test; it does not perform Hellforging
   or install its armor bonuses. Use `/effect give @s minecraft:fire_resistance 600`
   for Lava Strider; item fireproofing is not the potion. Keep the chestplate healthy.
   For actual **Deus Sentient Armour Evolution**, use an unevolved Sentient chestplate
   with capacity 500 (`/neovitae upgrade @s points set-cap 500`), mixed boots/leggings
   lacking Blood Mending, the native Deus layout and adequate EV. Stand by the master
   when it runs. Check 500 → 600 capacity and Blood Mending on both roots, not sources.
4. Check inventory/hand/equipped views, movement, trims/glint, normal/Shift/F3+H
   tooltips and F3+T. Trim and rename a directly forged pair, save/reopen and verify
   its charge/repair/upgrade state. Finally repeat recognition/presentation with
   packaged JARs on a same-loader client/server after reconnect. Automated local
   render submissions and separate-JVM reopen do not replace that final acceptance.
