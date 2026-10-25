package games.alejandrocoria.mismatchedboots.runtimetest.mystical;

import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedboots.compatibility.mysticalagriculture.MysticalAugments;
import games.alejandrocoria.mismatchedboots.compatibility.mysticalagriculture.MysticalAugmentsCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Packaged production scenarios and native controls; connected acceptance is a separate shared fixture. */
public final class MysticalAugmentsProbe {
    public interface Platform {
        String loader();

        void update(ServerPlayer player);

        void logout(ServerPlayer player);

        void death(ServerPlayer player);

        default void coexist(MinecraftServer server) throws Exception {
            throw new AssertionError("Unexpected coexist loader");
        }
    }

    private static boolean finished;
    private static int assertions;
    private static final StringBuilder observations = new StringBuilder();
    private static int playerSequence;
    public static final List<ItemEntity> witnessedDrops = new ArrayList<>();

    private MysticalAugmentsProbe() {
    }

    public static void run(MinecraftServer server, Platform platform) {
        if (finished) {
            return;
        }
        finished = true;
        try {
            observations.append("Loader: ").append(platform.loader()).append("; disconnected server-side event subjects.\n");
            if (System.getProperty("mismatchedboots.test.mysticalAugments").equals("absent")) {
                var player = player(server);
                var pair = MixedBootsFactory.createPair(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS));
                for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
                    player.setItemSlot(EquipmentSlot.FEET, root);
                    player.aiStep();
                    require(MysticalAugmentsCompatibility.selectedIds(root).isEmpty(), "Absent mod produced augments");
                    root.hurtAndBreak(root.getMaxDamage() + 1, player, EquipmentSlot.FEET);
                    require(root.isEmpty(), "Optional absence changed ordinary mixed breakage");
                }
                require(MysticalAugmentsProbe.class.getClassLoader().getResource(
                        "com/blakebr0/mysticalagriculture/api/util/AugmentUtils.class") == null, "Absence fixture includes MA");
                observations.append("Optional absence: ordinary mixed AI ticks/breaks, no MA class loaded.\n");
            } else if (System.getProperty("mismatchedboots.test.mysticalAugments").startsWith("refund")) {
                refunds(server, platform);
            } else if (System.getProperty("mismatchedboots.test.mysticalAugments").startsWith("no")) {
                refusals(server, platform);
            } else {
                live(server, platform);
            }
            NetworkAcceptanceFiles.write("mystical-augments-report.txt", observations + "Assertions: " + assertions + "\n");
            NetworkAcceptanceFiles.write("mystical-augments-success.txt", "server-cases-passed\n");
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure("mystical-augments", failure);
        } finally {
            server.halt(false);
        }
    }

    private static void live(MinecraftServer server, Platform platform) throws Exception {
        var jump = item("awakened_supremium_boots", "JUMP_BOOST_III");
        var pair = MixedBootsFactory.createPair(jump, new ItemStack(Items.DIAMOND_BOOTS));
        var player = player(server);
        player.setItemSlot(EquipmentSlot.FEET, pair.primary());
        require(MysticalNativeTestApi.nativeAugments(pair.primary()).isEmpty(), "Native lookup recognizes mixed");
        for (ItemStack stack : List.of(jump, pair.primary(), pair.inverse())) {
            player.removeAllEffects();
            player.setItemSlot(EquipmentSlot.FEET, stack);
            var history = ModContent.getMixedBootsData(stack);
            player.aiStep();
            require(player.getEffect(MobEffects.JUMP_BOOST) != null
                            && player.getEffect(MobEffects.JUMP_BOOST).getAmplifier() == 2, "Jump III missing: " + stack);
            require(history.equals(ModContent.getMixedBootsData(stack)), "Tick changed source provenance");
        }
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        player.removeAllEffects();
        player.setItemSlot(EquipmentSlot.OFFHAND, pair.inverse());
        player.aiStep();
        platform.update(player);
        require(!player.hasEffect(MobEffects.JUMP_BOOST), "Held mixed activated armor callback");
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        var levels = MixedBootsFactory.createPair(item("awakened_supremium_boots", "JUMP_BOOST_I"), jump);
        var duplicates = MixedBootsFactory.createPair(jump, jump);
        for (ItemStack stack : List.of(levels.primary(), levels.inverse(), duplicates.primary(), duplicates.inverse())) {
            require(MysticalAugmentsCompatibility.selectedIds(stack).size() == 1, "Family not deduplicated");
            player.removeAllEffects();
            player.setItemSlot(EquipmentSlot.FEET, stack);
            player.aiStep();
            require(player.getEffect(MobEffects.JUMP_BOOST).getAmplifier() == 2, "Family strongest tier not selected");
        }
        observations.append("Production native/mixed Jump III; held gate, family dedup/strongest and immutable sources pass.\n");

        for (boolean mixed : List.of(false, true)) {
            var subject = player(server);
            var nativeBoots = item("awakened_supremium_boots", "HEALTH_BOOST_III", "STEP_ASSIST");
            var roots = MixedBootsFactory.createPair(nativeBoots, new ItemStack(Items.DIAMOND_BOOTS));
            for (ItemStack stack : mixed ? List.of(roots.primary(), roots.inverse()) : List.of(nativeBoots)) {
                double base = subject.getAttributeValue(Attributes.STEP_HEIGHT);
                subject.setItemSlot(EquipmentSlot.FEET, stack);
                platform.update(subject);
                require(subject.getMaxHealth() == 32, "Health III missing");
                require(subject.getAttributeValue(Attributes.STEP_HEIGHT) == base + 1, "Step missing");
                subject.setShiftKeyDown(true);
                platform.update(subject);
                require(subject.getAttributeValue(Attributes.STEP_HEIGHT) == base, "Crouch did not remove Step");
                subject.setShiftKeyDown(false);
                platform.update(subject);
                require(subject.getAttributeValue(Attributes.STEP_HEIGHT) == base + 1, "Uncrouch did not restore Step");
                subject.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                platform.update(subject);
                require(subject.getMaxHealth() == 20 && subject.getAttributeValue(Attributes.STEP_HEIGHT) == base,
                        "Native cache cleanup after unequip failed");
            }
            var high = item("awakened_supremium_boots", "HEALTH_BOOST_V");
            var low = item("inferium_boots", "HEALTH_BOOST_I");
            subject.setItemSlot(EquipmentSlot.FEET, mixed ? MixedBootsFactory.createPair(high, low).inverse() : high);
            platform.update(subject);
            require(subject.getMaxHealth() == 40, "High family missing");
            subject.setItemSlot(EquipmentSlot.FEET, mixed ? MixedBootsFactory.createPair(low, new ItemStack(Items.DIAMOND_BOOTS)).primary() : low);
            platform.update(subject);
            platform.update(subject);
            require(subject.getMaxHealth() == 24, "High-to-low switch failed after native cleanup");
            subject.setItemSlot(EquipmentSlot.HEAD, item("awakened_supremium_helmet", "HEALTH_BOOST_V"));
            platform.update(subject);
            require(subject.getMaxHealth() == 40, "Native helmet arbitration failed");
            subject.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            platform.update(subject);
            require(subject.getMaxHealth() == 40, "Mixed removal cleared native helmet effect");
            subject.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            platform.update(subject);
            require(subject.getMaxHealth() == 20, "Last native cache entry remained");
            lifecycle(server, platform, mixed, false);
            lifecycle(server, platform, mixed, true);
        }
        observations.append("Native cache: Health/Step, crouching, unequip, high-to-low swaps and native helmet coexistence pass.\n");

        var noFall = item("awakened_supremium_boots", "NO_FALL_DAMAGE");
        var falls = MixedBootsFactory.createPair(noFall, new ItemStack(Items.DIAMOND_BOOTS));
        for (ItemStack stack : List.of(new ItemStack(Items.DIAMOND_BOOTS), noFall, falls.primary(), falls.inverse())) {
            var subject = player(server);
            subject.setItemSlot(EquipmentSlot.FEET, stack);
            float before = subject.getHealth();
            subject.causeFallDamage(9, 1, subject.damageSources().fall());
            require((subject.getHealth() == before) == !stack.is(Items.DIAMOND_BOOTS), "Native fall route differs: " + stack);
        }
        observations.append("Actual causeFallDamage: native/mixed protection and vanilla damage control pass.\n");
        absorption(server, platform.loader().equals("Fabric"));
        selectionAndFamilies(server);
        absorptionOrder(server);
        resources(server);
        if (System.getProperty("mismatchedboots.test.mysticalAugments").equals("compatible")) {
            require(MysticalGuardControl.equipmentCalls > 0 && MysticalGuardControl.armorCalls > 0, "Compatible wrappers did not execute");
        }
    }

    private static void refusals(MinecraftServer server, Platform platform) throws Exception {
        boolean query = !System.getProperty("mismatchedboots.test.mysticalAugments").equals("noequipment");
        var nativeBoots = item("awakened_supremium_boots", "JUMP_BOOST_III", "NO_FALL_DAMAGE");
        var nativePlayer = player(server);
        nativePlayer.setItemSlot(EquipmentSlot.FEET, nativeBoots);
        nativePlayer.setItemSlot(EquipmentSlot.HEAD, item("awakened_supremium_helmet", "HEALTH_BOOST_III"));
        nativePlayer.aiStep();
        platform.update(nativePlayer);
        require(nativePlayer.hasEffect(MobEffects.JUMP_BOOST) && nativePlayer.getMaxHealth() == 32, "Native control lost its own effects");
        var pair = MixedBootsFactory.createPair(item("awakened_supremium_boots", "JUMP_BOOST_III", "HEALTH_BOOST_III"),
                                                item("awakened_supremium_boots", "STEP_ASSIST", "NO_FALL_DAMAGE"));
        for (var root : List.of(pair.primary(), pair.inverse())) {
            var subject = player(server);
            subject.setItemSlot(EquipmentSlot.FEET, root);
            double step = subject.getAttributeValue(Attributes.STEP_HEIGHT);
            subject.aiStep();
            platform.update(subject);
            require(subject.hasEffect(MobEffects.JUMP_BOOST) == !query, "Independent armor route failed");
            require(subject.getMaxHealth() == (query ? 32 : 20), "Independent native Health route failed");
            require(subject.getAttributeValue(Attributes.STEP_HEIGHT) == step + (query ? 1 : 0), "Independent Step route failed");
            require(root.getDamageValue() == 0, "Declined Jump mutated live mixed durability");
            float before = subject.getHealth();
            subject.causeFallDamage(9, 1, subject.damageSources().fall());
            require((subject.getHealth() == before) == query, "Independent fall route failed");
        }
        observations.append("Independent local refusal; native controls intact, both inverse roots retain unaffected channels.\n");
    }

    private static void selectionAndFamilies(MinecraftServer server) throws Exception {
        for (var spec : MysticalAugments.CATALOG.values()) {
            var nativeBoots = item("awakened_supremium_boots", spec.id().getPath().toUpperCase(java.util.Locale.ROOT));
            var pair = MixedBootsFactory.createPair(nativeBoots, new ItemStack(Items.DIAMOND_BOOTS));
            for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
                require(MysticalAugmentsCompatibility.selectedIds(root).equals(List.of(spec.id())), "Native catalog selection differs: " + spec.id());
            }
        }
        for (var family : List.of("FIRE_RESISTANCE",
                                  "LUCK_III",
                                  "MINING_FATIGUE_RESISTANCE",
                                  "POISON_RESISTANCE",
                                  "SLOW_FALLING",
                                  "WITHER_RESISTANCE")) {
            var nativeBoots = item("awakened_supremium_boots", family);
            var pair = MixedBootsFactory.createPair(nativeBoots, new ItemStack(Items.DIAMOND_BOOTS));
            for (ItemStack stack : List.of(nativeBoots, pair.primary(), pair.inverse())) {
                var subject = player(server);
                subject.setItemSlot(EquipmentSlot.FEET, stack);
                subject.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.POISON, 200));
                subject.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.WITHER, 200));
                subject.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.MINING_FATIGUE, 200));
                subject.setRemainingFireTicks(100);
                subject.aiStep();
                boolean works = switch (family) {
                    case "FIRE_RESISTANCE" -> subject.hasEffect(MobEffects.FIRE_RESISTANCE) && !subject.isOnFire();
                    case "LUCK_III" -> subject.getEffect(MobEffects.LUCK) != null && subject.getEffect(MobEffects.LUCK).getAmplifier() == 2;
                    case "MINING_FATIGUE_RESISTANCE" -> !subject.hasEffect(MobEffects.MINING_FATIGUE);
                    case "POISON_RESISTANCE" -> !subject.hasEffect(MobEffects.POISON);
                    case "WITHER_RESISTANCE" -> !subject.hasEffect(MobEffects.WITHER);
                    default -> subject.hasEffect(MobEffects.SLOW_FALLING);
                };
                require(works, "Native/mixed family failed: " + family + "/" + stack);
                if (family.equals("SLOW_FALLING")) {
                    subject.removeEffect(MobEffects.SLOW_FALLING);
                    subject.setShiftKeyDown(true);
                    subject.aiStep();
                    require(!subject.hasEffect(MobEffects.SLOW_FALLING), "Sneaking retained Slow Falling");
                }
            }
        }
        var good = item("awakened_supremium_boots", "JUMP_BOOST_III");
        @SuppressWarnings("unchecked") var component = (net.minecraft.core.component.DataComponentType<List<Object>>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .get(Identifier.parse("mysticalagriculture:equipped_augments")).orElseThrow().value();
        var constructor = Class.forName("com.blakebr0.mysticalagriculture.api.components.AugmentComponent")
                               .getConstructor(Identifier.class, int.class);
        Identifier jump = Identifier.parse("mysticalagriculture:jump_boost_iii");
        for (List<Object> invalid : List.of(List.of(constructor.newInstance(jump, -1)), List.of(constructor.newInstance(jump, 2)),
                                            List.of(constructor.newInstance(jump, 0), constructor.newInstance(jump, 0)),
                                            List.of(constructor.newInstance(jump, 0),
                                                    constructor.newInstance(jump, 1),
                                                    constructor.newInstance(jump, 1)))) {
            var bad = good.copy();
            bad.set(component, invalid);
            var pair = MixedBootsFactory.createPair(bad, new ItemStack(Items.DIAMOND_BOOTS));
            require(MysticalAugmentsCompatibility.selectedIds(pair.primary()).isEmpty()
                            && MysticalAugmentsCompatibility.selectedIds(pair.inverse()).isEmpty(), "Malformed native slots granted an effect");
        }
        var weak = item("inferium_boots");
        weak.set(component, List.of(constructor.newInstance(jump, 0)));
        require(MysticalAugmentsCompatibility.selectedIds(MixedBootsFactory.createPair(weak, new ItemStack(Items.DIAMOND_BOOTS)).primary()).isEmpty(),
                "Source tier restriction bypassed");
        var valid = MixedBootsFactory.createPair(good, new ItemStack(Items.DIAMOND_BOOTS)).primary();
        var badCount = valid.copy();
        badCount.setCount(2);
        var missing = valid.copy();
        missing.remove(ModContent.mixedBootsDataType().orElseThrow());
        var spoof = new ItemStack(Items.STICK);
        spoof.applyComponents(valid.getComponents());
        for (var bad : List.of(badCount, missing, spoof)) {
            require(MysticalAugmentsCompatibility.selectedIds(bad).isEmpty(), "Invalid root granted augments");
        }
        observations.append("All 23 native IDs selected in both orientations; all 11 families exercised; malformed slots/tier/roots rejected.\n");
    }

    private static void absorptionOrder(MinecraftServer server) throws Exception {
        long time = server.overworld().getGameTime();
        try {
            server.getWorldData().overworldData().setGameTime(30_000);
            var nativeBoots = item("awakened_supremium_boots", "ABSORPTION_V");
            var pair = MixedBootsFactory.createPair(nativeBoots, new ItemStack(Items.DIAMOND_BOOTS));
            List<Identifier> expected = null;
            int amplifier = -1;
            for (var boots : List.of(nativeBoots, pair.primary(), pair.inverse())) {
                var subject = player(server);
                subject.setItemSlot(EquipmentSlot.FEET, boots);
                subject.setItemSlot(EquipmentSlot.HEAD, item("awakened_supremium_helmet", "ABSORPTION_I"));
                @SuppressWarnings("unchecked") var augments = (List<Object>) MysticalNativeTestApi.call(
                        "com.blakebr0.mysticalagriculture.api.util.AugmentUtils", "getArmorAugments", new Class<?>[]{Player.class}, subject);
                var ids = augments.stream().map(a -> (Identifier) MysticalNativeTestApi.invoke(a, "getId", new Class<?>[]{})).toList();
                subject.aiStep();
                int actual = subject.getEffect(MobEffects.ABSORPTION).getAmplifier();
                if (expected == null) {
                    expected = ids;
                    amplifier = actual;
                }
                require(expected.equals(ids) && actual == amplifier, "Mixed FEET changed native cross-piece Absorption ordering");
            }
        } finally {
            server.getWorldData().overworldData().setGameTime(time);
        }
        observations.append("Native equipment iteration and shared-timestamp Absorption winner preserved with native helmet.\n");
    }

    private static void lifecycle(MinecraftServer server, Platform platform, boolean mixed, boolean death) throws Exception {
        var subject = player(server);
        double baseStep = subject.getAttributeValue(Attributes.STEP_HEIGHT);
        ItemStack nativeBoots = item("awakened_supremium_boots", "HEALTH_BOOST_III", "STEP_ASSIST");
        subject.setItemSlot(EquipmentSlot.FEET,
                            mixed ? MixedBootsFactory.createPair(nativeBoots, new ItemStack(Items.DIAMOND_BOOTS)).inverse() : nativeBoots);
        platform.update(subject);
        if (death) {
            platform.death(subject);
        } else {
            platform.logout(subject);
        }
        var cacheField = Class.forName("com.blakebr0.mysticalagriculture.handler.AugmentHandler").getDeclaredField("ABILITY_CACHE");
        cacheField.setAccessible(true);
        require(((java.util.Set<?>) MysticalNativeTestApi.invoke(cacheField.get(null), "getCachedAbilities",
                                                                 new Class<?>[]{Player.class}, subject)).isEmpty(),
                "Lifecycle left native cache entries");
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
        subject.saveWithoutId(output);
        var replacement = player(server, subject.getGameProfile());
        if (!death) {
            replacement.load(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), output.buildResult()));
        }
        platform.update(replacement);
        if (!death) {
            require(replacement.getMaxHealth() == 32, "Saved-player reconnect duplicated/lost health");
            require(replacement.getAttributeValue(Attributes.STEP_HEIGHT) == baseStep + 1, "Saved-player reload lost/duplicated Step");
            replacement.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            platform.update(replacement);
            require(replacement.getMaxHealth() == 20 && replacement.getAttributeValue(Attributes.STEP_HEIGHT) == baseStep,
                    "Reloaded player's unequip did not clear health/Step");
        } else {
            require(replacement.getMaxHealth() == 20 && replacement.getAttributeValue(Attributes.STEP_HEIGHT) == baseStep,
                    "Fresh player inherited health/Step cache");
        }
        observations.append(mixed ? "mixed " : "native ").append(death ? "death/fresh-player" : "logout/player-NBT reload")
                    .append(": cache cleared; replacement cleanup passes.\n");
    }

    private static void absorption(MinecraftServer server, boolean fabric) throws Exception {
        long before = server.overworld().getGameTime();
        try {
            for (boolean mixed : List.of(false, true)) {
                for (boolean inverse : mixed ? List.of(false, true) : List.of(false)) {
                    var player = player(server);
                    var nativeBoots = item("awakened_supremium_boots", "ABSORPTION_III");
                    var roots = MixedBootsFactory.createPair(nativeBoots, new ItemStack(Items.DIAMOND_BOOTS));
                    player.setItemSlot(EquipmentSlot.FEET, mixed ? inverse ? roots.inverse() : roots.primary() : nativeBoots);
                    server.getWorldData().overworldData().setGameTime(20_000);
                    player.aiStep();
                    require(player.getEffect(MobEffects.ABSORPTION).getAmplifier() == 2, "Absorption missing");
                    player.removeEffect(MobEffects.ABSORPTION);
                    player.aiStep();
                    require(!player.hasEffect(MobEffects.ABSORPTION), "Absorption refilled before native interval");
                    var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
                    player.saveWithoutId(saved);
                    var replacement = player(server, player.getGameProfile());
                    replacement.load(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), saved.buildResult()));
                    replacement.aiStep();
                    require(replacement.hasEffect(MobEffects.ABSORPTION) == fabric,
                            "Absorption timestamp persistence differs from this distribution's native behavior");
                    server.getWorldData().overworldData().setGameTime(29_600);
                    player.aiStep();
                    require(!player.hasEffect(MobEffects.ABSORPTION), "Absorption refilled at exclusive boundary");
                    server.getWorldData().overworldData().setGameTime(29_601);
                    player.aiStep();
                    require(player.hasEffect(MobEffects.ABSORPTION), "Absorption did not refill after interval");
                }
            }
        } finally {
            server.getWorldData().overworldData().setGameTime(before);
        }
        observations.append("Absorption: native and both orientations honor shared 9600-tick interval/exclusive boundary.\n");
        observations.append("Absorption player-NBT reload: ")
                    .append(fabric ? "timestamp resets (native WeakHashMap)" : "timestamp persists (native player data)")
                    .append(".\n");
    }

    private static void resources(MinecraftServer server) throws Exception {
        MysticalRefundControl.reset(null, null);
        var player = player(server);
        var nativeBoots = item("inferium_boots", "HEALTH_BOOST_I");
        var roots = MixedBootsFactory.createPair(nativeBoots, new ItemStack(Items.DIAMOND_BOOTS));
        var menu = table(player);
        require(menu.getSlot(0).mayPlace(nativeBoots), "Native table positive control rejected");
        for (ItemStack root : List.of(roots.primary(), roots.inverse())) {
            require(!menu.getSlot(0).mayPlace(root), "Mixed table admission enabled unexpectedly");
            require(MysticalNativeTestApi.nativeAugments(root).isEmpty(), "General native lookup was broadened");
        }
        for (ItemStack stack : List.of(nativeBoots.copy(), roots.primary(), roots.inverse())) {
            boolean nativeSubject = stack.getItem() == nativeBoots.getItem();
            player.getInventory().clearContent();
            player.setItemSlot(EquipmentSlot.FEET, stack);
            stack.remove(DataComponents.UNBREAKABLE);
            stack.setDamageValue(stack.getMaxDamage() - 1);
            stack.hurtAndBreak(2, player, EquipmentSlot.FEET);
            require(stack.isEmpty(), "Break control did not actually break");
            int refunds = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                var item = player.getInventory().getItem(i);
                if (BuiltInRegistries.ITEM.getKey(item.getItem()).equals(Identifier.parse("mysticalagriculture:health_boost_i_augment"))) {
                    refunds += item.getCount();
                }
            }
            boolean refused = System.getProperty("mismatchedboots.test.mysticalAugments").matches("refund(missing|extra)");
            require(refunds == (nativeSubject || stack == roots.primary() && !refused ? 1 : 0), "Unexpected physical refund: " + refunds);
        }
        observations.append("Native table/general lookup unchanged; actual breaks honor left-source refund ownership/readiness.\n");
    }

    private static AbstractContainerMenu table(ServerPlayer player) throws Exception {
        Object inventory = MysticalNativeTestApi.call("com.blakebr0.mysticalagriculture.tileentity.TinkeringTableTileEntity",
                                                      "createInventoryHandler", new Class<?>[]{});
        var type = Class.forName("com.blakebr0.mysticalagriculture.container.TinkeringTableContainer");
        return (AbstractContainerMenu) type.getConstructor(int.class, Inventory.class, inventory.getClass(), BlockPos.class)
                                           .newInstance(1, player.getInventory(), inventory, BlockPos.ZERO);
    }

    private static void refunds(MinecraftServer server, Platform platform) throws Exception {
        resources(server);
        String variant = System.getProperty("mismatchedboots.test.mysticalAugments");
        boolean refused = variant.matches("refund(missing|extra)");
        if (refused) {
            var player = player(server);
            var pair = MixedBootsFactory.createPair(item("awakened_supremium_boots", "JUMP_BOOST_I"), new ItemStack(Items.DIAMOND_BOOTS));
            for (var root : List.of(pair.primary(), pair.inverse())) {
                player.setItemSlot(EquipmentSlot.FEET, root);
                player.removeAllEffects();
                player.aiStep();
                require(player.hasEffect(MobEffects.JUMP_BOOST), "Refund refusal disabled independent armor effect");
                breakRoot(root, player);
                require(root.isEmpty() && delivered(player).isEmpty(), "Refused boundary refunded or suppressed ordinary consumption");
            }
            observations.append("Missing/ambiguous final consumption proof: only MA refunds refused, native controls/Jump intact.\n");
            return;
        }
        var nativePlayer = player(server);
        for (int slot = 0; slot < 36; slot++) {
            nativePlayer.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        var nativeBoots = item("inferium_boots", "HEALTH_BOOST_I");
        var expectedNative = physicalAugments(nativeBoots);
        nativePlayer.setItemSlot(EquipmentSlot.FEET, nativeBoots);
        nativeBoots.setDamageValue(nativeBoots.getMaxDamage() - 1);
        nativeBoots.hurtAndBreak(2, nativePlayer, EquipmentSlot.FEET);
        require(nativeBoots.isEmpty() && delivered(nativePlayer).equals(expectedNative) && !drops(nativePlayer).isEmpty(),
                "Native full-inventory spawn control failed");
        drops(nativePlayer).forEach(ItemEntity::discard);
        observations.append("Native full inventory: actual break delivers one augment through an accepted ItemEntity spawn.\n");
        var diamond = new ItemStack(Items.DIAMOND_BOOTS);
        refundPair(server, item("inferium_boots", "HEALTH_BOOST_I"), diamond, "one MA + vanilla", false);
        refundPair(server, item("inferium_boots", "HEALTH_BOOST_I"), item("prudentium_boots", "LUCK_I"), "different augments", false);
        refundPair(server, item("inferium_boots", "HEALTH_BOOST_I"), item("inferium_boots", "HEALTH_BOOST_I"), "identical inputs", false);
        refundPair(server, item("awakened_supremium_boots", "HEALTH_BOOST_I", "STEP_ASSIST"),
                   item("awakened_supremium_boots", "HEALTH_BOOST_V", "JUMP_BOOST_III"), "four physical augments including weaker tier", false);
        refundPair(server, item("awakened_supremium_boots", "HEALTH_BOOST_I", "HEALTH_BOOST_I"), diamond,
                   "two occupied slots with identical augment", false);
        refundPair(server, item("inferium_boots"), diamond, "empty augment list", false);
        refundPair(server, new ItemStack(Items.IRON_BOOTS), diamond, "ordinary mixed", false);
        refundPair(server, item("inferium_boots", "HEALTH_BOOST_I"), item("prudentium_boots", "LUCK_I"),
                   "full inventory native drop fallback", true);
        refundFailures(server, variant);
        if (variant.equals("refundcoexist")) {
            platform.coexist(server);
            observations.append("JDT and MA native shrink observers remain ready and independently return their own purchases.\n");
        }
        MysticalRefundControl.reset(null, null);
    }

    private static void refundPair(MinecraftServer server, ItemStack left, ItemStack right, String label, boolean full) throws Exception {
        ItemStack leftBefore = left.copy(), rightBefore = right.copy();
        var expectedOwners = List.of(physicalAugments(left), physicalAugments(right));
        var expectedTotal = new LinkedHashMap<Identifier, Integer>(expectedOwners.getFirst());
        expectedOwners.getLast().forEach((id, count) -> expectedTotal.merge(id, count, Integer::sum));
        var actualTotal = new LinkedHashMap<Identifier, Integer>();
        var pair = MixedBootsFactory.createPair(left, right);
        var outputs = List.of(pair.primary(), pair.inverse());
        for (int i = 0; i < outputs.size(); i++) {
            var ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
            ItemStack root = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, outputs.get(i)).getOrThrow()).getOrThrow();
            var player = player(server);
            if (full) {
                for (int slot = 0; slot < 36; slot++) {
                    player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
                }
            }
            player.setItemSlot(EquipmentSlot.FEET, root);
            MysticalRefundControl.reset(root, player);
            var menu = table(player);
            require(!menu.getSlot(0).mayPlace(root) && MysticalNativeTestApi.nativeAugments(root).isEmpty(),
                    "Refund adapter broadened table/general lookup: " + label);
            // Force a damageable fixture even when both native Supremium inputs carry UNBREAKABLE.
            root.remove(DataComponents.UNBREAKABLE);
            root.hurtAndBreak(1, player, EquipmentSlot.FEET);
            require(!root.isEmpty() && root.getDamageValue() == 1 && delivered(player).isEmpty(), "Nonbreaking wear refunded: " + label);
            root.setDamageValue(0);
            require(delivered(player).isEmpty(), "Repair refunded: " + label);
            root.setDamageValue(root.getMaxDamage() - 1);
            root.hurtAndBreak(2, player, EquipmentSlot.FEET);
            require(root.isEmpty(), "Refund fixture did not actually break: " + label);
            require(MysticalRefundControl.deliveries == expectedOwners.get(i).values().stream().mapToInt(Integer::intValue).sum(),
                    "Delivery count/timing wrong: " + label);
            var received = delivered(player);
            require(received.equals(expectedOwners.get(i)), "Wrong owner refund: " + label + " orientation " + i
                    + " expected=" + expectedOwners.get(i) + " received=" + received);
            received.forEach((id, count) -> actualTotal.merge(id, count, Integer::sum));
            root.hurtAndBreak(2, player, EquipmentSlot.FEET);
            require(delivered(player).equals(received), "Consumed root refunded twice: " + label);
            if (full) {
                require(!drops(player).isEmpty(), "Full inventory control did not exercise native dropping");
            }
            drops(player).forEach(ItemEntity::discard);
        }
        require(actualTotal.equals(expectedTotal), "Pair refund duplicated/lost physical inputs: " + label);
        require(ItemStack.matches(left, leftBefore) && ItemStack.matches(right, rightBefore), "Refund changed original inputs: " + label);
        observations.append("Refund ")
                    .append(label)
                    .append(": per-left-source allocation, pair conservation, NBT ownership, no early/repeated refund; ")
                    .append(actualTotal)
                    .append('\n');
        MysticalRefundControl.reset(null, null);
    }

    private static void breakRoot(ItemStack root, ServerPlayer player) {
        root.remove(DataComponents.UNBREAKABLE);
        root.setDamageValue(root.getMaxDamage() - 1);
        // Keep the original inventory identity observable through nested tests, without equipment cleanup.
        root.hurtAndBreak(2, player.level(), player, item -> {});
    }

    private static void breakOther(ItemStack another, ServerPlayer player) {
        var previous = MysticalRefundControl.root;
        MysticalRefundControl.root = another;
        try {
            breakRoot(another, player);
        } finally {
            MysticalRefundControl.root = previous;
        }
    }

    private static void refundFailures(MinecraftServer server, String variant) throws Exception {
        boolean peer = variant.equals("refundinner") || variant.equals("refundouter");
        for (String fault : List.of("ordinary",
                                    "prevent",
                                    "cancel",
                                    "throw",
                                    "deliverySame",
                                    "deliveryOther",
                                    "command",
                                    "detached",
                                    "count",
                                    "data",
                                    "malformed",
                                    "unbreakable",
                                    "creative",
                                    "spectator",
                                    "inventory",
                                    "offhand")) {
            var player = player(server);
            var root = MixedBootsFactory.createPair(item("inferium_boots", "HEALTH_BOOST_I"), new ItemStack(Items.DIAMOND_BOOTS)).primary();
            player.setItemSlot(EquipmentSlot.FEET, root);
            MysticalRefundControl.reset(root, player);
            var expected = physicalAugments(ModContent.getMixedBootsData(root).orElseThrow().left().source());
            boolean expect = List.of("ordinary", "deliverySame", "deliveryOther", "inventory", "offhand").contains(fault);
            switch (fault) {
                case "prevent" -> MysticalRefundControl.prevent = true;
                case "cancel" -> MysticalRefundControl.cancelDelivery = true;
                case "throw" -> MysticalRefundControl.throwDelivery = true;
                case "deliverySame" -> MysticalRefundControl.duringDelivery = () -> root.hurtAndBreak(2, player.level(), player, item -> {});
                case "deliveryOther" -> {
                    var another = MixedBootsFactory.createPair(item("prudentium_boots", "LUCK_I"), new ItemStack(Items.DIAMOND_BOOTS)).primary();
                    player.getInventory().setItem(12, another);
                    expected.putAll(physicalAugments(ModContent.getMixedBootsData(another).orElseThrow().left().source()));
                    MysticalRefundControl.duringDelivery = () -> breakOther(another, player);
                }
                case "detached" -> player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                case "count" -> root.setCount(2);
                case "data" -> root.remove(ModContent.mixedBootsDataType().orElseThrow());
                case "malformed" -> {
                    var source = item("inferium_boots", "HEALTH_BOOST_I");
                    var type = (net.minecraft.core.component.DataComponentType<List<Object>>) BuiltInRegistries.DATA_COMPONENT_TYPE
                            .get(Identifier.parse("mysticalagriculture:equipped_augments")).orElseThrow().value();
                    Object entry = Class.forName("com.blakebr0.mysticalagriculture.api.components.AugmentComponent")
                                        .getConstructor(Identifier.class, int.class)
                                        .newInstance(Identifier.parse("mysticalagriculture:health_boost_i"), 2);
                    source.set(type, List.of(entry));
                    root.set(ModContent.mixedBootsDataType().orElseThrow(), ModContent.getMixedBootsData(
                            MixedBootsFactory.createPair(source, new ItemStack(Items.DIAMOND_BOOTS)).primary()).orElseThrow());
                }
                case "creative" -> player.setGameMode(GameType.CREATIVE);
                case "spectator" -> player.setGameMode(GameType.SPECTATOR);
                case "inventory", "offhand" -> {
                    player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                    player.getInventory().setItem(fault.equals("inventory") ? 14 : Inventory.SLOT_OFFHAND, root);
                }
            }
            boolean threw = false;
            try {
                if (fault.equals("command")) {
                    root.shrink(1);
                } else if (fault.equals("unbreakable")) {
                    root.set(DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
                    root.hurtAndBreak(root.getMaxDamage() + 1, player.level(), player, item -> {});
                } else {
                    breakRoot(root, player);
                }
            } catch (IllegalStateException failure) {
                if (!fault.equals("throw") || !failure.getMessage().equals("MA controlled delivery failure")) {
                    throw failure;
                }
                threw = true;
            }
            require(threw == fault.equals("throw"), "Throwing-delivery control was not exercised");
            require(delivered(player).equals(expect ? expected : Map.of()), "Wrong refund under " + fault + ": " + delivered(player));
            if (fault.equals("prevent")) {
                require(!root.isEmpty(), "No-consumption peer actually consumed root");
            }
            if (fault.equals("cancel") || fault.equals("throw")) {
                require(root.isEmpty() && MysticalRefundControl.deliveries == 1, "Cancelled/throwing dispatch timing/count wrong");
                MysticalRefundControl.throwDelivery = false;
                root.hurtAndBreak(2, player.level(), player, item -> {});
                require(MysticalRefundControl.deliveries == 1, "Cancelled/throwing delivery retried");
            }
            MysticalRefundControl.reset(null, null);
        }
        if (peer) {
            for (String fault : variant.equals("refundinner")
                                ? List.of("reenter", "other", "replace", "move", "entitlement", "lore", "throwShrink")
                                : List.of("reenter", "other", "lore")) {
                var player = player(server);
                var root = MixedBootsFactory.createPair(item("inferium_boots", "HEALTH_BOOST_I"), new ItemStack(Items.DIAMOND_BOOTS)).primary();
                player.setItemSlot(EquipmentSlot.FEET, root);
                MysticalRefundControl.reset(root, player);
                var expected = physicalAugments(ModContent.getMixedBootsData(root).orElseThrow().left().source());
                switch (fault) {
                    case "reenter" -> MysticalRefundControl.before = () -> root.hurtAndBreak(1, player.level(), player, item -> {});
                    case "other" -> {
                        var another = MixedBootsFactory.createPair(item("prudentium_boots", "LUCK_I"), new ItemStack(Items.DIAMOND_BOOTS)).primary();
                        player.getInventory().setItem(12, another);
                        expected.putAll(physicalAugments(ModContent.getMixedBootsData(another).orElseThrow().left().source()));
                        MysticalRefundControl.before = () -> breakOther(another, player);
                    }
                    case "replace" -> MysticalRefundControl.after = () -> player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                    case "move" -> MysticalRefundControl.after = () -> {
                        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                        player.getInventory().setItem(13, root);
                    };
                    case "entitlement" -> {
                        var replacement = MixedBootsFactory.createPair(item("awakened_supremium_boots", "JUMP_BOOST_I"),
                                                                       new ItemStack(Items.DIAMOND_BOOTS)).primary();
                        MysticalRefundControl.after = () -> root.set(ModContent.mixedBootsDataType().orElseThrow(),
                                                                     ModContent.getMixedBootsData(replacement).orElseThrow());
                    }
                    case "lore" -> MysticalRefundControl.after = () -> root.set(DataComponents.CUSTOM_NAME,
                                                                                net.minecraft.network.chat.Component.literal("unrelated name"));
                    case "throwShrink" -> MysticalRefundControl.after = () -> {throw new IllegalStateException("MA controlled shrink failure");};
                }
                try {
                    breakRoot(root, player);
                } catch (IllegalStateException failure) {
                    if (!fault.equals("throwShrink") || !failure.getMessage().equals("MA controlled shrink failure")) {
                        throw failure;
                    }
                }
                boolean declined = List.of("replace", "move", "entitlement", "throwShrink").contains(fault);
                require(delivered(player).equals(declined ? Map.of() : expected),
                        "Foreign shrink callback failed " + variant + "/" + fault + ": " + delivered(player));
                require(MysticalRefundControl.peerCalls > 0, "Compatible shrink peer never ran");
                MysticalRefundControl.reset(null, null);
            }
        }
        observations.append(
                            "Production consumption/delivery timing, prevented/creative/direct deletion, outside-FEET identity, cancellation/failure and nested independent roots pass; peer=")
                    .append(peer)
                    .append('.')
                    .append('\n');
    }

    static Map<Identifier, Integer> physicalAugments(ItemStack source) {
        var result = new LinkedHashMap<Identifier, Integer>();
        for (Object augment : MysticalNativeTestApi.nativeAugments(source)) {
            var item = (net.minecraft.world.item.Item) MysticalNativeTestApi.invoke(augment, "getItem", new Class<?>[]{});
            result.merge(BuiltInRegistries.ITEM.getKey(item), 1, Integer::sum);
        }
        return result;
    }

    static Map<Identifier, Integer> delivered(ServerPlayer player) {
        var result = new LinkedHashMap<Identifier, Integer>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            addAugmentItem(result, player.getInventory().getItem(slot));
        }
        for (ItemEntity drop : drops(player)) {
            addAugmentItem(result, drop.getItem());
        }
        return result;
    }

    private static void addAugmentItem(Map<Identifier, Integer> result, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id.getNamespace().equals("mysticalagriculture") && id.getPath().endsWith("_augment")) {
            result.merge(id, stack.getCount(), Integer::sum);
        }
    }

    private static List<ItemEntity> drops(ServerPlayer player) {
        return witnessedDrops.stream().filter(item -> !item.isRemoved() && item.level() == player.level()
                && player.getBoundingBox().inflate(4).intersects(item.getBoundingBox())).toList();
    }

    public static ServerPlayer player(MinecraftServer server) {
        return player(server, new GameProfile(UUID.randomUUID(), "ma-test-" + ++playerSequence));
    }

    private static ServerPlayer player(MinecraftServer server, GameProfile profile) {
        var player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                                         CommonListenerCookie.createInitial(profile, false));
        // NeoForge widens this method; Fabric keeps vanilla's private access.
        try {
            var loaded = ServerGamePacketListenerImpl.class.getDeclaredMethod("markClientLoaded");
            loaded.setAccessible(true);
            loaded.invoke(player.connection);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(0, 80, 0);
        player.setNoGravity(true);
        return player;
    }

    static ItemStack item(String name, String... augments) throws Exception {
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("mysticalagriculture", name)).orElseThrow());
        var augmentType = Class.forName("com.blakebr0.mysticalagriculture.api.tinkering.Augment");
        for (int slot = 0; slot < augments.length; slot++) {
            Object augment = Class.forName("com.blakebr0.mysticalagriculture.lib.ModAugments").getField(augments[slot]).get(null);
            MysticalNativeTestApi.call("com.blakebr0.mysticalagriculture.api.util.AugmentUtils", "addAugment",
                                       new Class<?>[]{ItemStack.class, augmentType, int.class}, stack, augment, slot);
        }
        require(MysticalNativeTestApi.nativeAugments(stack).size() == augments.length, "Native augment setup failed");
        return stack;
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
