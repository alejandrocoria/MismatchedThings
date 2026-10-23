package games.alejandrocoria.mismatchedboots.runtimetest.mystical;

import com.mojang.authlib.GameProfile;
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

/** In-process native event probes, not a connected client or a production integration. */
public final class MysticalAugmentsProbe {
    public interface Platform {
        String loader();
        void update(ServerPlayer player);
        void logout(ServerPlayer player);
        void death(ServerPlayer player);
    }
    private static boolean finished;
    private static int assertions;
    private static final StringBuilder observations = new StringBuilder();
    private static int playerSequence;
    public static final List<ItemEntity> witnessedDrops = new ArrayList<>();

    private MysticalAugmentsProbe() {}

    public static void run(MinecraftServer server, Platform platform) {
        if (finished) { return; }
        finished = true;
        try {
            observations.append("Loader: ").append(platform.loader()).append("; disconnected server-side event subjects.\n");
            if (System.getProperty("mismatchedboots.test.mysticalAugments").equals("absent")) {
                var player = player(server);
                var pair = MixedBootsFactory.createPair(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS));
                for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
                    player.setItemSlot(EquipmentSlot.FEET, root); player.aiStep();
                    require(MysticalAugmentsBridge.resolve(root).isEmpty(), "Absent mod produced augments");
                }
                require(MysticalAugmentsProbe.class.getClassLoader().getResource(
                        "com/blakebr0/mysticalagriculture/api/util/AugmentUtils.class") == null, "Absence fixture includes MA");
                observations.append("Optional absence: ordinary mixed AI ticks, no MA class loaded.\n");
            } else if (System.getProperty("mismatchedboots.test.mysticalAugments").equals("refund")) {
                refunds(server);
            } else { live(server, platform); }
            NetworkAcceptanceFiles.write("mystical-augments-report.txt", observations + "Assertions: " + assertions + "\n");
            NetworkAcceptanceFiles.write("mystical-augments-success.txt", "research-only\n");
        } catch (Throwable failure) { NetworkAcceptanceFiles.failure("mystical-augments", failure); }
        finally { server.halt(false); }
    }

    private static void live(MinecraftServer server, Platform platform) throws Exception {
        var jump = item("awakened_supremium_boots", "JUMP_BOOST_III");
        var pair = MixedBootsFactory.createPair(jump, new ItemStack(Items.DIAMOND_BOOTS));
        var player = player(server);
        player.setItemSlot(EquipmentSlot.FEET, pair.primary());
        MysticalAugmentsBridge.active = false;
        player.aiStep(); platform.update(player);
        require(!player.hasEffect(MobEffects.JUMP_BOOST), "Unmodified mixed unexpectedly runs Jump");
        require(MysticalAugmentsBridge.nativeAugments(pair.primary()).isEmpty(), "Native lookup recognizes mixed");
        MysticalAugmentsBridge.active = true;
        for (ItemStack stack : List.of(jump, pair.primary(), pair.inverse())) {
            player.removeAllEffects(); player.setItemSlot(EquipmentSlot.FEET, stack);
            var history = ModContent.getMixedBootsData(stack);
            player.aiStep();
            require(player.getEffect(MobEffects.JUMP_BOOST) != null
                    && player.getEffect(MobEffects.JUMP_BOOST).getAmplifier() == 2, "Jump III missing: " + stack);
            require(history.equals(ModContent.getMixedBootsData(stack)), "Tick changed source provenance");
        }
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); player.removeAllEffects();
        player.setItemSlot(EquipmentSlot.OFFHAND, pair.inverse()); player.aiStep(); platform.update(player);
        require(!player.hasEffect(MobEffects.JUMP_BOOST), "Held mixed activated armor callback");
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        var levels = MixedBootsFactory.createPair(item("awakened_supremium_boots", "JUMP_BOOST_I"), jump);
        var duplicates = MixedBootsFactory.createPair(jump, jump);
        for (ItemStack stack : List.of(levels.primary(), levels.inverse(), duplicates.primary(), duplicates.inverse())) {
            require(MysticalAugmentsBridge.resolve(stack).size() == 1, "Family not deduplicated");
            player.removeAllEffects(); player.setItemSlot(EquipmentSlot.FEET, stack); player.aiStep();
            require(player.getEffect(MobEffects.JUMP_BOOST).getAmplifier() == 2, "Family strongest tier not selected");
        }
        observations.append("Baseline missing effect reproduced; native and both mixed orientations run Jump III; held gate, family dedup/strongest and immutable sources pass.\n");

        for (boolean mixed : List.of(false, true)) {
            var subject = player(server);
            var nativeBoots = item("awakened_supremium_boots", "HEALTH_BOOST_III", "STEP_ASSIST");
            var roots = MixedBootsFactory.createPair(nativeBoots, new ItemStack(Items.DIAMOND_BOOTS));
            for (ItemStack stack : mixed ? List.of(roots.primary(), roots.inverse()) : List.of(nativeBoots)) {
                double base = subject.getAttributeValue(Attributes.STEP_HEIGHT);
                subject.setItemSlot(EquipmentSlot.FEET, stack); platform.update(subject);
                require(subject.getMaxHealth() == 32, "Health III missing");
                require(subject.getAttributeValue(Attributes.STEP_HEIGHT) == base + 1, "Step missing");
                subject.setShiftKeyDown(true); platform.update(subject);
                require(subject.getAttributeValue(Attributes.STEP_HEIGHT) == base, "Crouch did not remove Step");
                subject.setShiftKeyDown(false); platform.update(subject);
                require(subject.getAttributeValue(Attributes.STEP_HEIGHT) == base + 1, "Uncrouch did not restore Step");
                subject.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); platform.update(subject);
                require(subject.getMaxHealth() == 20 && subject.getAttributeValue(Attributes.STEP_HEIGHT) == base,
                        "Native cache cleanup after unequip failed");
            }
            var high = item("awakened_supremium_boots", "HEALTH_BOOST_V");
            var low = item("inferium_boots", "HEALTH_BOOST_I");
            subject.setItemSlot(EquipmentSlot.FEET, mixed ? MixedBootsFactory.createPair(high, low).inverse() : high);
            platform.update(subject); require(subject.getMaxHealth() == 40, "High family missing");
            subject.setItemSlot(EquipmentSlot.FEET, mixed ? MixedBootsFactory.createPair(low, new ItemStack(Items.DIAMOND_BOOTS)).primary() : low);
            platform.update(subject); platform.update(subject);
            require(subject.getMaxHealth() == 24, "High-to-low switch failed after native cleanup");
            subject.setItemSlot(EquipmentSlot.HEAD, item("awakened_supremium_helmet", "HEALTH_BOOST_V"));
            platform.update(subject); require(subject.getMaxHealth() == 40, "Native helmet arbitration failed");
            subject.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); platform.update(subject);
            require(subject.getMaxHealth() == 40, "Mixed removal cleared native helmet effect");
            subject.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); platform.update(subject);
            require(subject.getMaxHealth() == 20, "Last native cache entry remained");
            lifecycle(server, platform, mixed, false);
            lifecycle(server, platform, mixed, true);
        }
        observations.append("Native cache: Health/Step, crouching, unequip, high-to-low swaps and native helmet coexistence pass.\n");

        var noFall = item("awakened_supremium_boots", "NO_FALL_DAMAGE");
        var falls = MixedBootsFactory.createPair(noFall, new ItemStack(Items.DIAMOND_BOOTS));
        for (ItemStack stack : List.of(new ItemStack(Items.DIAMOND_BOOTS), noFall, falls.primary(), falls.inverse())) {
            var subject = player(server); subject.setItemSlot(EquipmentSlot.FEET, stack);
            float before = subject.getHealth(); subject.causeFallDamage(9, 1, subject.damageSources().fall());
            require((subject.getHealth() == before) == !stack.is(Items.DIAMOND_BOOTS), "Native fall route differs: " + stack);
        }
        observations.append("Actual causeFallDamage: native/mixed protection and vanilla damage control pass.\n");
        absorption(server, platform.loader().equals("Fabric"));
        resources(server);
    }

    private static void lifecycle(MinecraftServer server, Platform platform, boolean mixed, boolean death) throws Exception {
        var subject = player(server);
        double baseStep = subject.getAttributeValue(Attributes.STEP_HEIGHT);
        ItemStack nativeBoots = item("awakened_supremium_boots", "HEALTH_BOOST_III", "STEP_ASSIST");
        subject.setItemSlot(EquipmentSlot.FEET, mixed ? MixedBootsFactory.createPair(nativeBoots, new ItemStack(Items.DIAMOND_BOOTS)).inverse() : nativeBoots);
        platform.update(subject);
        if (death) { platform.death(subject); } else { platform.logout(subject); }
        var cacheField = Class.forName("com.blakebr0.mysticalagriculture.handler.AugmentHandler").getDeclaredField("ABILITY_CACHE");
        cacheField.setAccessible(true);
        require(((java.util.Set<?>) MysticalAugmentsBridge.invoke(cacheField.get(null), "getCachedAbilities",
                new Class<?>[]{Player.class}, subject)).isEmpty(), "Lifecycle left native cache entries");
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
            replacement.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); platform.update(replacement);
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
                    player.aiStep(); require(player.getEffect(MobEffects.ABSORPTION).getAmplifier() == 2, "Absorption missing");
                    player.removeEffect(MobEffects.ABSORPTION); player.aiStep();
                    require(!player.hasEffect(MobEffects.ABSORPTION), "Absorption refilled before native interval");
                    var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
                    player.saveWithoutId(saved);
                    var replacement = player(server, player.getGameProfile());
                    replacement.load(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), saved.buildResult()));
                    replacement.aiStep();
                    require(replacement.hasEffect(MobEffects.ABSORPTION) == fabric,
                            "Absorption timestamp persistence differs from this distribution's native behavior");
                    server.getWorldData().overworldData().setGameTime(29_600); player.aiStep();
                    require(!player.hasEffect(MobEffects.ABSORPTION), "Absorption refilled at exclusive boundary");
                    server.getWorldData().overworldData().setGameTime(29_601); player.aiStep();
                    require(player.hasEffect(MobEffects.ABSORPTION), "Absorption did not refill after interval");
                }
            }
        } finally { server.getWorldData().overworldData().setGameTime(before); }
        observations.append("Absorption: native and both orientations honor shared 9600-tick interval/exclusive boundary.\n");
        observations.append("Absorption player-NBT reload: ").append(fabric ? "timestamp resets (native WeakHashMap)" : "timestamp persists (native player data)").append(".\n");
    }

    private static void resources(MinecraftServer server) throws Exception {
        var player = player(server);
        var nativeBoots = item("inferium_boots", "HEALTH_BOOST_I");
        var roots = MixedBootsFactory.createPair(nativeBoots, new ItemStack(Items.DIAMOND_BOOTS));
        var menu = table(player);
        require(menu.getSlot(0).mayPlace(nativeBoots), "Native table positive control rejected");
        for (ItemStack root : List.of(roots.primary(), roots.inverse())) {
            require(!menu.getSlot(0).mayPlace(root), "Spike enabled mixed table admission");
            require(MysticalAugmentsBridge.nativeAugments(root).isEmpty(), "General native lookup was broadened");
        }
        for (ItemStack stack : List.of(nativeBoots.copy(), roots.primary(), roots.inverse())) {
            boolean nativeSubject = stack.getItem() == nativeBoots.getItem();
            player.getInventory().clearContent(); player.setItemSlot(EquipmentSlot.FEET, stack);
            stack.remove(DataComponents.UNBREAKABLE); stack.setDamageValue(stack.getMaxDamage() - 1);
            stack.hurtAndBreak(2, player, EquipmentSlot.FEET);
            require(stack.isEmpty(), "Break control did not actually break");
            int refunds = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                var item = player.getInventory().getItem(i);
                if (BuiltInRegistries.ITEM.getKey(item.getItem()).equals(Identifier.parse("mysticalagriculture:health_boost_i_augment"))) {
                    refunds += item.getCount();
                }
            }
            require(refunds == (nativeSubject ? 1 : 0), "Unexpected physical refund: " + refunds);
        }
        observations.append("Native table accepts native/rejects both mixed; actual break refunds remain native-only.\n");
    }

    private static AbstractContainerMenu table(ServerPlayer player) throws Exception {
        Object inventory = MysticalAugmentsBridge.call("com.blakebr0.mysticalagriculture.tileentity.TinkeringTableTileEntity",
                "createInventoryHandler", new Class<?>[]{});
        var type = Class.forName("com.blakebr0.mysticalagriculture.container.TinkeringTableContainer");
        return (AbstractContainerMenu) type.getConstructor(int.class, Inventory.class, inventory.getClass(), BlockPos.class)
                .newInstance(1, player.getInventory(), inventory, BlockPos.ZERO);
    }

    private static void refunds(MinecraftServer server) throws Exception {
        resources(server); // Native positive control and mixed negative control with the new bridge disabled.
        MysticalAugmentsBridge.refundsActive = true;
        try {
            var nativePlayer = player(server);
            for (int slot = 0; slot < 36; slot++) { nativePlayer.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64)); }
            var nativeBoots = item("inferium_boots", "HEALTH_BOOST_I");
            var expectedNative = physicalAugments(nativeBoots);
            nativePlayer.setItemSlot(EquipmentSlot.FEET, nativeBoots);
            nativeBoots.setDamageValue(nativeBoots.getMaxDamage() - 1); nativeBoots.hurtAndBreak(2, nativePlayer, EquipmentSlot.FEET);
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
        } finally { MysticalAugmentsBridge.refundsActive = false; }
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
            if (full) { for (int slot = 0; slot < 36; slot++) { player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64)); } }
            player.setItemSlot(EquipmentSlot.FEET, root);
            var menu = table(player);
            require(!menu.getSlot(0).mayPlace(root) && MysticalAugmentsBridge.nativeAugments(root).isEmpty(),
                    "Refund candidate opened table/general lookup: " + label);
            // Force a damageable fixture even when both native Supremium inputs carry UNBREAKABLE.
            root.remove(DataComponents.UNBREAKABLE); root.hurtAndBreak(1, player, EquipmentSlot.FEET);
            require(!root.isEmpty() && root.getDamageValue() == 1 && delivered(player).isEmpty(), "Nonbreaking wear refunded: " + label);
            root.setDamageValue(0);
            require(delivered(player).isEmpty(), "Repair refunded: " + label);
            root.setDamageValue(root.getMaxDamage() - 1); root.hurtAndBreak(2, player, EquipmentSlot.FEET);
            require(root.isEmpty(), "Refund fixture did not actually break: " + label);
            var received = delivered(player);
            require(received.equals(expectedOwners.get(i)), "Wrong owner refund: " + label + " orientation " + i
                    + " expected=" + expectedOwners.get(i) + " received=" + received);
            received.forEach((id, count) -> actualTotal.merge(id, count, Integer::sum));
            root.hurtAndBreak(2, player, EquipmentSlot.FEET);
            require(delivered(player).equals(received), "Consumed root refunded twice: " + label);
            if (full) { require(!drops(player).isEmpty(), "Full inventory control did not exercise native dropping"); }
            drops(player).forEach(ItemEntity::discard);
        }
        require(actualTotal.equals(expectedTotal), "Pair refund duplicated/lost physical inputs: " + label);
        require(ItemStack.matches(left, leftBefore) && ItemStack.matches(right, rightBefore), "Refund changed original inputs: " + label);
        observations.append("Refund ").append(label).append(": per-left-source allocation, pair conservation, NBT ownership, no early/repeated refund; ")
                .append(actualTotal).append('\n');
    }

    private static Map<Identifier, Integer> physicalAugments(ItemStack source) {
        var result = new LinkedHashMap<Identifier, Integer>();
        for (Object augment : MysticalAugmentsBridge.nativeAugments(source)) {
            var item = (net.minecraft.world.item.Item) MysticalAugmentsBridge.invoke(augment, "getItem", new Class<?>[]{});
            result.merge(BuiltInRegistries.ITEM.getKey(item), 1, Integer::sum);
        }
        return result;
    }

    private static Map<Identifier, Integer> delivered(ServerPlayer player) {
        var result = new LinkedHashMap<Identifier, Integer>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            addAugmentItem(result, player.getInventory().getItem(slot));
        }
        for (ItemEntity drop : drops(player)) { addAugmentItem(result, drop.getItem()); }
        return result;
    }

    private static void addAugmentItem(Map<Identifier, Integer> result, ItemStack stack) {
        if (stack.isEmpty()) { return; }
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
        return player(server, new GameProfile(UUID.randomUUID(), "ma-spike-" + ++playerSequence));
    }

    private static ServerPlayer player(MinecraftServer server, GameProfile profile) {
        var player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(profile, false));
        // NeoForge widens this method; Fabric keeps vanilla's private access.
        try {
            var loaded = ServerGamePacketListenerImpl.class.getDeclaredMethod("markClientLoaded");
            loaded.setAccessible(true); loaded.invoke(player.connection);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
        player.setGameMode(GameType.SURVIVAL); player.setPos(0, 80, 0); player.setNoGravity(true);
        return player;
    }

    private static ItemStack item(String name, String... augments) throws Exception {
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("mysticalagriculture", name)).orElseThrow());
        var augmentType = Class.forName("com.blakebr0.mysticalagriculture.api.tinkering.Augment");
        for (int slot = 0; slot < augments.length; slot++) {
            Object augment = Class.forName("com.blakebr0.mysticalagriculture.lib.ModAugments").getField(augments[slot]).get(null);
            MysticalAugmentsBridge.call("com.blakebr0.mysticalagriculture.api.util.AugmentUtils", "addAugment",
                    new Class<?>[]{ItemStack.class, augmentType, int.class}, stack, augment, slot);
        }
        require(MysticalAugmentsBridge.nativeAugments(stack).size() == augments.length, "Native augment setup failed");
        return stack;
    }

    private static void require(boolean condition, String message) {
        assertions++; if (!condition) { throw new AssertionError(message); }
    }
}
