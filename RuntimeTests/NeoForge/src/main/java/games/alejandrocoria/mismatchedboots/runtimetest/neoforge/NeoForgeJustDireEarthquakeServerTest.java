package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveNetwork;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireEarthquakeCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireGroundStompCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireSettingsCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireUpgradesCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireEarthquakeTestSupport.binding;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireEarthquakeTestSupport.charge;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireEarthquakeTestSupport.clock;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireEarthquakeTestSupport.mixed;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireEarthquakeTestSupport.original;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireEarthquakeTestSupport.timer;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireEarthquakeTestSupport.timers;

/** Real production root action/permits/clock; native controls and fault probes are not an alternate implementation. */
final class NeoForgeJustDireEarthquakeServerTest {
    private static final String PREFIX = "just-dire-earthquake-server";
    private static int warmup;
    private static int sequence;
    private static int groups;
    private static int elapsed;
    private static int paused;
    private static boolean started;
    private static boolean finished;
    private static ServerPlayer clockPlayer;
    private static ServerPlayer originalPlayer;
    private static ItemStack clockRoot;
    private static ItemStack nativeRoot;
    private static Mob late;
    private static final StringBuilder report = new StringBuilder();

    private NeoForgeJustDireEarthquakeServerTest() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> run(event.getServer()));
    }

    private static void run(MinecraftServer server) {
        if (finished) {
            return;
        }

        try {
            if (!started) {
                if (++warmup == 1) {
                    server.overworld().setChunkForced(0, 0, true);
                }
                if (warmup < 200 && !server.overworld().areEntitiesActuallyLoadedAndTicking(new net.minecraft.world.level.ChunkPos(0, 0))) {
                    return;
                }
                require(warmup < 200, "Entity chunk never became ticking");
                String variant = System.getProperty("mismatchedboots.runtimetest.justDireEarthquakeStateVariant", "live");
                if (!variant.equals("live")) {
                    unavailable(server, variant);
                    finish(server);
                    return;
                }
                JustDireEarthquakeTestSupport.bind();
                effects(server);
                shared(server);
                permits(server);
                settings(server);
                faults(server);
                startClock(server);
                started = true;
                return;
            }
            naturalClock();
            if (elapsed == 1_000) {
                record("full natural 200 active/800 cooldown production/native timeline, no late-target repeat/payment, disabled/depleted/uninstalled continuation, unequipped pause and independent Ground Stomp expiry");
                finish(server);
            }
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure(PREFIX, failure);
            finished = true;
            server.overworld().setChunkForced(0, 0, false);
            server.halt(false);
        }
    }

    private static void finish(MinecraftServer server) {
        NetworkAcceptanceFiles.write(PREFIX + "-report.txt", groups + " production Earthquake server groups\n" + report);
        NetworkAcceptanceFiles.write(PREFIX + "-success.txt", "neoforge\n");
        finished = true;
        server.overworld().setChunkForced(0, 0, false);
        server.halt(false);
    }

    private static void effects(MinecraftServer server) throws Exception {
        var player = JustDireGroundStompTestSupport.player(server, GameType.SURVIVAL);
        for (int amount : new int[]{0, 49_999, 50_000, 99_999, 100_000, 150_000}) {
            Result control = use(player, original(amount), false, 3, false);
            for (boolean inverse : new boolean[]{false, true}) {
                var root = mixed(amount, inverse, false);
                var before = root.copy();
                require(control.equals(use(player, root, true, 3, false)), "Native threshold/exhaustion/feedback mismatch at " + amount);
                history(before, root);
            }
        }
        record("both orientations: native thresholds 0/49999/50000/99999/100000/150000, exact Slowness IV/200 and FE exhaustion; one heavy sound and native particle calls; unchanged provenance");
        require(use(player, original(50_000), false, 0, false).equals(use(player, mixed(50_000, true, false), true, 0, false)),
                "Empty activation differs");
        var silent = mixed(50_000, false, false);
        set(silent, "earthquake_custom_setting", 1);
        Result result = use(player, silent, true, 1, false);
        require(result.particles == 0 && result.sounds == 1 && result.affected == 1, "Particle suppression changed sound/effect");
        record("empty area starts lockout/sound without FE bill; root custom particles suppress emissions without losing effect/sound");
        for (int unbreaking : new int[]{3, 10}) {
            var control = original(100_000);
            var root = mixed(100_000, false, false);
            var enchantment = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
            control.enchant(enchantment, unbreaking);
            root.enchant(enchantment, unbreaking);
            require(use(player, control, false, 4, false).equals(use(player, root, true, 4, false)), "Discount/base admission mismatch");
        }
        for (GameType mode : List.of(GameType.CREATIVE, GameType.SPECTATOR)) {
            player.setGameMode(mode);
            for (int amount : new int[]{49_999, 50_000}) {
                require(use(player, original(amount), false, 3, false).equals(use(player, mixed(amount, false, false), true, 3, false)),
                        "Exemption/base admission mismatch");
            }
        }
        player.setGameMode(GameType.SURVIVAL);
        require(use(player, original(150_000), false, 3, false).equals(use(player, mixed(150_000, false, true), true, 3, false)),
                "Two Eclipse halves duplicated action");
        record("native Unbreaking III/X and creative/spectator payments keep full base threshold; two Eclipse sources still execute once");
        var filtered = use(player, original(250_000), false, 0, true);
        require(filtered.equals(use(player,
                                    mixed(250_000, false, false),
                                    true,
                                    0,
                                    true)) && filtered.affected == 2 && filtered.energy == 100_000 && filtered.particles == 6,
                "Ground/AABB/tag/multipart/immune processing differs: " + filtered);
        record("actual world filters: grounded/AABB edge versus airborne/outside/warden tag/multipart; immune target is billed and gets native particles despite no accepted effect");
        for (String field : List.of("tool_enabled", "earthquake_toggle", "earthquake_upgrade_installed")) {
            var root = mixed(50_000, false, false);
            set(root, field, false);
            var before = root.copy();
            require(use(player, root, true, 1, false).affected == 0 && ItemStack.matches(root, before), "Disabled permission mutated root");
        }

        var config = config();
        boolean enabled = config.get();
        try {
            config.set(false);
            var root = mixed(50_000, false, false);
            var before = root.copy();
            require(use(player, root, true, 1, false).affected == 0 && ItemStack.matches(root, before), "Native config veto lost");
        } finally {
            config.set(enabled);
        }
        record("root installation/global/toggle and native config deny before action/payment/lockout");
    }

    private static Result use(ServerPlayer player, ItemStack root, boolean isMixed, int count, boolean filtering) throws Exception {
        player.setItemSlot(EquipmentSlot.FEET, root);
        var targets = new ArrayList<Mob>();
        try {
            for (int i = 0; i < count; i++) {
                targets.add(target(player, i + 1, 0));
            }
            if (filtering) {
                targets.add(target(player, 2, 0));
                targets.add(target(player, 5.1, 0));
                targets.add(target(player, 2, 1));
                targets.add(target(player, 6, 0));
                targets.add(target(player, 2, 2));
                targets.add(target(player, 2, 3));
                targets.add(target(player, 2, 4));
            }
            JustDireEarthquakeServerGuardPlugin.heavySounds = 0;
            JustDireEarthquakeServerGuardPlugin.particles = 0;
            if (isMixed) {
                attempt(player, 72);
            } else {
                JustDireEarthquakeTestSupport.nativeUse(player, root);
            }

            int affected = 0;
            for (Mob mob : targets) {
                var effect = mob.getEffect(MobEffects.SLOWNESS);
                if (effect != null) {
                    require(effect.getAmplifier() == 3 && effect.getDuration() == 200, "Wrong Slowness effect");
                    affected++;
                }
            }

            return new Result(charge(root),
                              affected,
                              clock(root, "earthquake"),
                              JustDireEarthquakeServerGuardPlugin.heavySounds,
                              JustDireEarthquakeServerGuardPlugin.particles);
        } finally {
            targets.forEach(Mob::discard);
        }
    }

    private static void shared(MinecraftServer server) throws Exception {
        var player = JustDireGroundStompTestSupport.player(server, GameType.SURVIVAL);
        var tool = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
        var nativeUse = tool.getMethod("useAbility",
                                       net.minecraft.world.level.Level.class,
                                       net.minecraft.world.entity.player.Player.class,
                                       ItemStack.class,
                                       int.class,
                                       boolean.class);
        for (int amount : new int[]{50_000, 55_000, 105_000}) {
            for (boolean empty : new boolean[]{false, true}) {
                var control = original(amount);
                var root = mixed(amount, true, false);
                for (var stack : List.of(control, root)) {
                    set(stack, "earthquake_bindingmode", 2);
                    set(stack, "groundstomp_upgrade_installed", true);
                    set(stack, "groundstomp_bindingmode", 2);
                    set(stack, "stepheight_upgrade_installed", true);
                    set(stack, "stepheight_bindingmode", 2);
                    set(stack, "ability_bindings", List.of(binding("earthquake", 72), binding("groundstomp", 72), binding("stepheight", 72)));
                }

                var active = (List<?>) tool.getMethod("getActiveAbilities", ItemStack.class).invoke(control.getItem(), control);
                require(active.stream()
                              .map(value -> ((Enum<?>) value).name())
                              .filter(value -> value.equals("GROUNDSTOMP") || value.equals("EARTHQUAKE"))
                              .toList()
                              .equals(List.of("GROUNDSTOMP", "EARTHQUAKE")), "Native active order changed");
                var mob = empty ? null : target(player, 1, 0);
                try {
                    player.setItemSlot(EquipmentSlot.FEET, control);
                    nativeUse.invoke(control.getItem(), player.level(), player, control, 72, false);
                    boolean slowed = mob != null && mob.hasEffect(MobEffects.SLOWNESS);
                    if (mob != null) {
                        mob.removeAllEffects();
                        mob.setOnGround(true);
                    }
                    player.setItemSlot(EquipmentSlot.FEET, root);
                    require(attempt(player, 72), "Shared permit rejected");
                    require(charge(root) == charge(control) && Objects.equals(clock(root, "groundstomp"), clock(control, "groundstomp"))
                                    && Objects.equals(clock(root, "earthquake"), clock(control, "earthquake"))
                                    && (mob != null && mob.hasEffect(MobEffects.SLOWNESS)) == slowed
                                    && !root.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("stepheight_toggle"), true),
                            "Shared native order/budget/passive mismatch");
                } finally {
                    if (mob != null) {
                        mob.discard();
                    }
                }
            }
        }
        record("native shared active order proven independent of reversed binding-list order: GS then Earthquake, fresh post-GS budget at 50000/55000/105000, empty targets and passive switch last");
    }

    private static void permits(MinecraftServer server) throws Exception {
        var player = JustDireGroundStompTestSupport.player(server, GameType.SURVIVAL);
        for (boolean active : new boolean[]{false, true}) {
            var root = mixed(150_000, false, false);
            set(root, "groundstomp_upgrade_installed", true);
            set(root, "groundstomp_bindingmode", 2);
            set(root, "ability_bindings", List.of(binding("groundstomp", 71), binding("earthquake", 72)));
            root.set(timers(), List.of(timer("earthquake", 1, active)));
            player.setItemSlot(EquipmentSlot.FEET, root);
            var permit = prepare(player, 71);
            require(permit.accepted(), "GS quote unavailable");
            JustDireEarthquakeTestSupport.tick(player);
            require(activate(player, permit, 71) && charge(root) == 145_000, "Unrelated phase/expiry rejected paid GS");
            require(!activate(player, permit, 71), "Consumed nonce replayed");
        }

        var root = mixed(150_000, false, false);
        root.set(timers(), List.of(timer("groundstomp", 1, false)));
        player.setItemSlot(EquipmentSlot.FEET, root);
        var permit = prepare(player, 72);
        var tick = JustDireGroundStompCompatibility.class.getDeclaredMethod("tickEquipped", ServerPlayer.class);
        tick.setAccessible(true);
        tick.invoke(null, player);
        require(activate(player, permit, 72) && clock(root, "earthquake") != null && clock(root, "groundstomp") == null,
                "Peer GS expiry rejected Earthquake");
        record("real production prepare/activate: unrelated EQ active→cooldown/expiry and GS expiry tolerated once; full-stack action preflight refreshed only after validated progression");

        List<Consumer<ItemStack>> mutations = List.of(stack -> set(stack, "forge_energy", 149_999),
                                                      stack -> stack.setDamageValue(1),
                                                      stack -> set(stack, "tool_enabled", false),
                                                      stack -> stack.remove(DataComponents.ATTRIBUTE_MODIFIERS),
                                                      stack -> {
                                                          try {
                                                              stack.set(timers(),
                                                                        List.of(timer("airburst", 77, false), timer("earthquake", 77, false)));
                                                          } catch (Exception e) {
                                                              throw new IllegalStateException(e);
                                                          }
                                                      },
                                                      stack -> {
                                                          try {
                                                              stack.set(timers(), List.of(timer("airburst", 76, false)));
                                                          } catch (Exception e) {
                                                              throw new IllegalStateException(e);
                                                          }
                                                      });
        for (Consumer<ItemStack> mutation : mutations) {
            root = mixed(150_000, false, false);
            set(root, "groundstomp_upgrade_installed", true);
            set(root, "groundstomp_bindingmode", 2);
            set(root, "ability_bindings", List.of(binding("groundstomp", 71)));
            root.set(timers(), List.of(timer("airburst", 77, false)));
            player.setItemSlot(EquipmentSlot.FEET, root);
            permit = prepare(player, 71);
            require(permit.accepted(), "Mutation test failed to prepare");
            mutation.accept(root);
            var changed = root.copy();
            require(!activate(player, permit, 71) && ItemStack.matches(root, changed), "Unrelated state change granted payment");
        }
        root = mixed(150_000, false, false);
        player.setItemSlot(EquipmentSlot.FEET, root);
        permit = prepare(player, 72);
        player.setItemSlot(EquipmentSlot.FEET, root.copy());
        require(!activate(player, permit, 72), "Equal-value replacement reused nonce");
        root = mixed(150_000, false, false);
        set(root, "groundstomp_upgrade_installed", true);
        set(root, "groundstomp_bindingmode", 2);
        set(root, "ability_bindings", List.of(binding("groundstomp", 72), binding("earthquake", 72)));
        root.set(timers(), List.of(timer("earthquake", 1, false)));
        player.setItemSlot(EquipmentSlot.FEET, root);
        permit = prepare(player, 72);
        JustDireEarthquakeTestSupport.tick(player);
        require(activate(player, permit, 72) && clock(root, "earthquake") == null && charge(root) == 145_000,
                "Unready EQ was promoted into existing GS permit");
        record("strict resource/wear/attributes/settings/unknown timer or insertion/replacement changes reject read-only; an EQ that expires after prepare cannot join a paid GS permit");
        for (int kind = 0; kind < 5; kind++) {
            root = mixed(150_000, false, false);
            set(root, "groundstomp_upgrade_installed", true);
            set(root, "groundstomp_bindingmode", 2);
            set(root, "ability_bindings", List.of(binding("groundstomp", 71)));
            Object old = timer("earthquake", kind == 3 ? -1 : 77, false);
            root.set(timers(), List.of(old, timer("airburst", 20, false)));
            player.setItemSlot(EquipmentSlot.FEET, root);
            permit = prepare(player, 71);
            require(permit.accepted(), "Clock rejection control did not prepare");
            switch (kind) {
                case 0 -> root.set(timers(), List.of(timer("earthquake", 78, false), timer("airburst", 20, false)));
                case 1 -> root.set(timers(), List.of(timer("earthquake", 75, false), timer("airburst", 20, false)));
                case 2 -> root.set(timers(), List.of(timer("airburst", 20, false)));
                case 3 -> root.set(timers(), List.of(timer("earthquake", -2, false), timer("airburst", 20, false)));
                case 4 -> root.set(timers(), List.of(timer("airburst", 20, false), timer("earthquake", 76, false)));
            }

            var changed = root.copy();
            require(!activate(player, permit, 71) && ItemStack.matches(root, changed),
                    "Clock reset/excess/unsupported/removal/reorder admitted: " + kind);
        }

        var invalid = mixed(0, false, false);
        player.setItemSlot(EquipmentSlot.FEET, invalid);
        for (var records : List.of(List.of(timer("earthquake", 0, true)), List.of(timer("earthquake", -1, false)),
                                   List.of(timer("earthquake", 2, true), timer("earthquake", 3, false)))) {
            invalid.set(timers(), records);
            var before = invalid.copy();
            JustDireEarthquakeTestSupport.tick(player);
            require(ItemStack.matches(before, invalid), "Owned ticker repaired unsupported/duplicate evidence");
        }

        var dormant = MixedBootsFactory.createPair(new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.IRON_BOOTS)).primary();
        dormant.set(timers(), List.of(timer("earthquake", 1, true)));
        player.setItemSlot(EquipmentSlot.FEET, dormant);
        JustDireEarthquakeTestSupport.tick(player);
        require(clock(dormant, "earthquake").equals(new JustDireEarthquakeTestSupport.Clock(800, false))
                        && JustDireEarthquakeCompatibility.quote(dormant) == null, "Dormant lockout was frozen or granted an ineligible action");
        record("clock comparator rejects reset, excessive progress, premature removal, unsupported mutation and record reorder; owned ticker retains zero/negative/duplicate evidence with one warning and advances dormant lockout without granting readiness");
    }

    private static void settings(MinecraftServer server) throws Exception {
        var player = JustDireGroundStompTestSupport.player(server, GameType.SURVIVAL);
        var root = mixed(0, false, false);
        player.getInventory().setItem(36, root);
        AbstractContainerMenu menu = (AbstractContainerMenu) Class.forName("com.direwolf20.justdirethings.common.containers.ToolSettingContainer")
                                                                  .getConstructor(int.class,
                                                                                  net.minecraft.world.entity.player.Inventory.class,
                                                                                  net.minecraft.world.entity.player.Player.class)
                                                                  .newInstance(17, player.getInventory(), player);
        player.containerMenu = menu;
        var selected = JustDireActiveNetwork.edit(player,
                                                  new JustDireActiveNetwork.SettingsRequest(17,
                                                                                            36,
                                                                                            1,
                                                                                            1,
                                                                                            JustDireActiveNetwork.NONE,
                                                                                            0,
                                                                                            "",
                                                                                            0,
                                                                                            0,
                                                                                            false,
                                                                                            true));
        require(selected.accepted(), "Native menu selection rejected");
        var token = selected.token();
        for (int[] edit : new int[][]{{1, 0}, {3, 1}, {4, 2}}) {
            require(JustDireActiveNetwork.edit(player,
                                               new JustDireActiveNetwork.SettingsRequest(17,
                                                                                         36,
                                                                                         1,
                                                                                         2 + edit[0],
                                                                                         token,
                                                                                         edit[0],
                                                                                         "earthquake",
                                                                                         edit[1],
                                                                                         72,
                                                                                         false,
                                                                                         true)).accepted(), "Valid EQ server edit rejected");
        }
        require(!JustDireActiveNetwork.edit(player,
                                            new JustDireActiveNetwork.SettingsRequest(17, 36, 1, 9, token, 2, "earthquake", 1, 72, false, true))
                                      .accepted(), "Dead EQ slider admitted");
        require(JustDireSettingsCompatibility.metadata(root, "earthquake") != null
                        && !JustDireSettingsCompatibility.metadata(root, "earthquake").hasSlider()
                        && !JustDireSettingsCompatibility.boundPassives(root, 72, false).contains("earthquake"),
                "EQ metadata exposed a dead slider or passive switch");
        player.containerMenu = player.inventoryMenu;
        record("real native-menu token validates EQ toggle/particles/equipped binding at zero FE, rejects dead numeric slider; C metadata is fixed/toggle-only and EQ never joins passive switches");
    }

    private static void faults(MinecraftServer server) throws Exception {
        var player = JustDireGroundStompTestSupport.player(server, GameType.SURVIVAL);
        for (int fault : new int[]{1, 2, 3, 4}) {
            var root = mixed(150_000, false, false);
            player.setItemSlot(EquipmentSlot.FEET, root);
            var before = root.copy();
            var mobs = List.of(target(player, 1, 0), target(player, 2, 0), target(player, 3, 0));
            JustDireEarthquakeServerGuardPlugin.player = player;
            JustDireEarthquakeServerGuardPlugin.fault = fault;
            JustDireEarthquakeServerGuardPlugin.failAt = fault == 1 ? 2 : 1;
            JustDireEarthquakeServerGuardPlugin.debits = 0;
            boolean threw = false;
            try {
                attempt(player, 72);
            } catch (JustDireEarthquakeServerGuardPlugin.PostCommitFailure expected) {
                threw = true;
            } finally {
                JustDireEarthquakeServerGuardPlugin.fault = 0;
            }

            long affected = mobs.stream().filter(mob -> mob.hasEffect(MobEffects.SLOWNESS)).count();
            require(affected == (fault == 1 || fault == 4 ? 1 : 0) && charge(root) == (fault == 1 || fault == 4 ? 100_000 : 150_000)
                            && clock(root, "earthquake").ticks() == 200 && threw == (fault == 4), "Fault lost rollback/commit boundary: " + fault);
            history(before, root);
            mobs.forEach(Mob::discard);
        }

        var root = mixed(150_000, false, false);
        set(root, "groundstomp_upgrade_installed", true);
        set(root, "groundstomp_bindingmode", 2);
        set(root, "ability_bindings", List.of(binding("earthquake", 72), binding("groundstomp", 71)));
        player.setItemSlot(EquipmentSlot.FEET, root);
        var callback = new CallbackMob(player);
        callback.setPos(9, 100, 8);
        callback.setNoAi(true);
        callback.setNoGravity(true);
        require(player.level().addFreshEntity(callback), "Callback target missing");
        callback.setOnGround(true);
        try {
            require(attempt(player, 72) && !callback.reentered && charge(root) == 100_000 && clock(root, "groundstomp") == null,
                    "Cross-ability callback reentered dispatcher");
            require(attempt(player, 71) && charge(root) == 95_000, "Finally failed to release shared guard");
        } finally {
            callback.discard();
        }
        root = mixed(150_000, false, false);
        set(root, "stepheight_upgrade_installed", true);
        set(root, "stepheight_bindingmode", 2);
        set(root, "ability_bindings", List.of(binding("earthquake", 72), binding("stepheight", 72)));
        player.setItemSlot(EquipmentSlot.FEET, root);
        var changing = new ArrayList<Mob>();
        for (int i = 0; i < 3; i++) {
            var mob = new ChangingMob(player);
            mob.setPos(9 + i, 100, 8);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            require(player.level().addFreshEntity(mob), "Changing callback target missing");
            mob.setOnGround(true);
            changing.add(mob);
        }

        try {
            require(attempt(player, 72) && charge(root) == 100_000 && changing.stream().filter(mob -> mob.hasEffect(MobEffects.SLOWNESS)).count() == 1
                            && root.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("stepheight_toggle"), true),
                    "Effect callback control change paid later targets or allowed passive dispatch");
        } finally {
            changing.forEach(Mob::discard);
        }
        record("real staged second-target short receipt keeps first effect/debit and lockout; equipment/control changes abort before effect; post-commit exception propagates without refund; GS callback reentry rejects and guard releases");
    }

    private static void startClock(MinecraftServer server) throws Exception {
        clockPlayer = JustDireGroundStompTestSupport.player(server, GameType.SURVIVAL);
        originalPlayer = JustDireGroundStompTestSupport.player(server, GameType.SURVIVAL);
        clockRoot = mixed(50_000, true, false);
        nativeRoot = original(50_000);
        clockPlayer.setItemSlot(EquipmentSlot.FEET, clockRoot);
        originalPlayer.setItemSlot(EquipmentSlot.FEET, nativeRoot);
        require(attempt(clockPlayer, 72), "Clock activation denied");
        JustDireEarthquakeTestSupport.nativeUse(originalPlayer, nativeRoot);
        clockRoot.set(timers(), List.of(timer("earthquake", 200, true), timer("groundstomp", 30, false)));
    }

    private static void naturalClock() throws Exception {
        if (elapsed == 150 && paused < 10) {
            clockPlayer.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            originalPlayer.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            var before = clockRoot.copy();
            JustDireEarthquakeTestSupport.tick(clockPlayer);
            nativeRoot.getItem().inventoryTick(nativeRoot, originalPlayer.level(), originalPlayer, EquipmentSlot.MAINHAND);
            require(ItemStack.matches(before, clockRoot), "Unequipped EQ advanced");
            paused++;
            return;
        }
        clockPlayer.setItemSlot(EquipmentSlot.FEET, clockRoot);
        originalPlayer.setItemSlot(EquipmentSlot.FEET, nativeRoot);
        if (late != null) {
            late.setOnGround(true);
        }

        var nativeConfig = config();
        boolean enabled = nativeConfig.get();
        try {
            if (elapsed >= 40) {
                nativeConfig.set(false);
            }
            JustDireEarthquakeTestSupport.tick(clockPlayer);
            var stompTick = JustDireGroundStompCompatibility.class.getDeclaredMethod("tickEquipped", ServerPlayer.class);
            stompTick.setAccessible(true);
            stompTick.invoke(null, clockPlayer);
            nativeRoot.getItem().inventoryTick(nativeRoot, originalPlayer.level(), originalPlayer, EquipmentSlot.FEET);
            elapsed++;
        } finally {
            nativeConfig.set(enabled);
        }
        require(Objects.equals(clock(clockRoot, "earthquake"), clock(nativeRoot, "earthquake")),
                "Production/native natural clocks differ at " + elapsed);
        require(charge(clockRoot) == (elapsed <= 40 ? 50_000 : 0) && charge(clockRoot) == charge(nativeRoot), "Clock spent/restored FE");
        if (late != null) {
            require(!late.hasEffect(MobEffects.SLOWNESS), "Active period repeated effect");
        }
        if (elapsed == 20) {
            late = target(clockPlayer, 1, 0);
        }
        if (elapsed == 30) {
            require(clock(clockRoot, "groundstomp") == null, "Independent GS did not expire");
        }
        if (elapsed == 40) {
            late.discard();
            late = null;
            for (var root : List.of(clockRoot, nativeRoot)) {
                set(root, "forge_energy", 0);
                set(root, "tool_enabled", false);
                set(root, "earthquake_toggle", false);
                set(root, "earthquake_upgrade_installed", false);
            }
        }
        if (elapsed == 200) {
            require(clock(clockRoot, "earthquake").equals(new JustDireEarthquakeTestSupport.Clock(800, false)), "Phase transition missed");
        }
        if (elapsed == 1_000) {
            require(clock(clockRoot, "earthquake") == null, "Lockout did not expire");
        }
    }

    private static void unavailable(MinecraftServer server, String variant) throws Exception {
        var player = JustDireGroundStompTestSupport.player(server, GameType.SURVIVAL);
        if (variant.equals("absent")) {
            require(!JustDireEarthquakeCompatibility.available() && !prepare(player, 72).accepted(), "Absent API granted action");
        } else {
            JustDireEarthquakeTestSupport.bind();
            var root = mixed(50_000, false, false);
            player.setItemSlot(EquipmentSlot.FEET, root);
            var before = root.copy();
            for (int repeat = 0; repeat < 3; repeat++) {
                require(!attempt(player, 72), "Unavailable EQ action granted");
            }
            require(ItemStack.matches(before, root) && JustDireGroundStompCompatibility.available() && JustDireUpgradesCompatibility.available(),
                    "Unavailable EQ poisoned existing route or state");
        }
        record(variant + ": unavailable action does not charge/start clock; quiet absence or one bounded optional diagnostic; older routes intact");
    }

    private static Mob target(ServerPlayer player, double distance, int type) {
        Mob mob = type == 3 ? new MultipartMob(player.level()) : type == 4 ? new ImmuneMob(player.level())
                                                                           : (type == 2
                                                                              ? EntityType.WARDEN
                                                                              : EntityType.ZOMBIE).create(player.level(), EntitySpawnReason.COMMAND);
        require(mob != null, "No target");
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setPos(player.getX() + distance, player.getY(), player.getZ());
        require(player.level().addFreshEntity(mob), "Target not inserted");
        mob.setOnGround(type != 1);
        require(player.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox()).contains(mob), "Target invisible in actual query");
        return mob;
    }

    private static JustDireActiveNetwork.ActivationReply prepare(ServerPlayer player, int key) {
        return JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(++sequence, key, false));
    }

    private static boolean activate(ServerPlayer player, JustDireActiveNetwork.ActivationReply reply, int key) {
        return JustDireActiveNetwork.activate(player,
                                              new JustDireActiveNetwork.Activate(reply.sequence(), reply.nonce(), key, false, java.util.List.of(36)));
    }

    private static boolean attempt(ServerPlayer player, int key) {
        var reply = prepare(player, key);
        return reply.accepted() && activate(player, reply, key);
    }

    private static void history(ItemStack before, ItemStack after) {
        require(ModContent.getMixedBootsData(before).equals(ModContent.getMixedBootsData(after)) && before.getDamageValue() == after.getDamageValue(),
                "Root FE action changed history/wear");
    }

    private static <T> void set(ItemStack root, String name, T value) {
        JustDireAttributeTestSupport.set(root, name, value);
    }

    private static ModConfigSpec.BooleanValue config() throws Exception {
        var map = (Map<?, ?>) Class.forName("com.direwolf20.justdirethings.setup.Config").getField("AVAILABLE_ABILITY_MAP").get(null);
        return (ModConfigSpec.BooleanValue) map.entrySet()
                                               .stream()
                                               .filter(entry -> ((Enum<?>) entry.getKey()).name().equals("EARTHQUAKE"))
                                               .findFirst()
                                               .orElseThrow()
                                               .getValue();
    }

    private static void record(String value) {
        groups++;
        report.append(value).append('\n');
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private record Result(int energy, int affected, JustDireEarthquakeTestSupport.Clock clock, int sounds, int particles) {
    }

    private static final class MultipartMob extends Zombie {
        MultipartMob(ServerLevel level) {
            super(EntityType.ZOMBIE, level);
        }

        @Override
        public boolean isMultipartEntity() {
            return true;
        }

        @Override
        public PartEntity<?>[] getParts() {
            return new PartEntity<?>[0];
        }
    }

    private static final class ImmuneMob extends Zombie {
        ImmuneMob(ServerLevel level) {
            super(EntityType.ZOMBIE, level);
        }

        @Override
        public boolean canBeAffected(MobEffectInstance effect) {
            return !effect.getEffect().equals(MobEffects.SLOWNESS) && super.canBeAffected(effect);
        }
    }

    private static final class CallbackMob extends Zombie {
        final ServerPlayer player;
        boolean reentered;

        CallbackMob(ServerPlayer player) {
            super(EntityType.ZOMBIE, player.level());
            this.player = player;
        }

        @Override
        public boolean addEffect(MobEffectInstance effect, Entity source) {
            boolean accepted = super.addEffect(effect, source);
            if (effect.getEffect().equals(MobEffects.SLOWNESS)) {
                reentered = attempt(player, 71);
            }
            return accepted;
        }
    }

    private static final class ChangingMob extends Zombie {
        final ServerPlayer player;

        ChangingMob(ServerPlayer player) {
            super(EntityType.ZOMBIE, player.level());
            this.player = player;
        }

        @Override
        public boolean addEffect(MobEffectInstance effect, Entity source) {
            boolean accepted = super.addEffect(effect, source);
            if (effect.getEffect().equals(MobEffects.SLOWNESS)) {
                set(player.getItemBySlot(EquipmentSlot.FEET), "tool_enabled", false);
            }
            return accepted;
        }
    }
}
