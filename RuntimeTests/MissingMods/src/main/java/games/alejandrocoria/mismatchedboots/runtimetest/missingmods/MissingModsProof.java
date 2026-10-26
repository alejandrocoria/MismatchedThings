package games.alejandrocoria.mismatchedboots.runtimetest.missingmods;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.SuspendedBootsData;
import games.alejandrocoria.mismatchedboots.data.SuspendedBootsItemCodec;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchInput;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsWorkbenchRecipe;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalNativeTestApi;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Separate disposable-world processes; production owns all capture and recovery. */
public final class MissingModsProof {
    public static final String MOD = "mismatchedboots_runtime_test";
    private static final int ROOTS = 11;
    private static final BlockPos STORAGE = new BlockPos(0, 80, 0);
    private static boolean finished;
    private static int ticks;
    static int convertingReads;
    static boolean legacyReadable = true;

    private MissingModsProof() {
    }

    public static boolean enabled() {
        return !System.getProperty("mismatchedboots.test.missingModsCase", "").isEmpty();
    }

    public static DataComponentType<Integer> throwingProperty() {
        return DataComponentType.<Integer>builder().persistent(com.mojang.serialization.Codec.INT.xmap(value -> {
            if (value == 13) {
                throw new IllegalArgumentException("Unreadable foreign property fixture");
            }
            return value;
        }, value -> value)).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build();
    }

    public static DataComponentType<Integer> convertingProperty() {
        var codec = com.mojang.serialization.Codec.either(com.mojang.serialization.Codec.INT,
                                                          com.mojang.serialization.Codec.INT.fieldOf("legacy").codec()).flatXmap(value -> {
            convertingReads++;
            if (value.right().isPresent() && !legacyReadable) {
                return com.mojang.serialization.DataResult.<Integer>error(() -> "Unsupported legacy fixture value");
            }
            return com.mojang.serialization.DataResult.success(value.map(number -> number, number -> number));
        }, value -> com.mojang.serialization.DataResult.success(com.mojang.datafixers.util.Either.left(value)));
        return DataComponentType.<Integer>builder().persistent(codec)
                                .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build();
    }

    public static String stage() {
        return System.getProperty("mismatchedboots.test.missingModsStage");
    }

    public static boolean connected() {
        return stage().startsWith("client");
    }

    public static boolean fixturesPresent() {
        return List.of("write", "restore", "reloaded", "clientwrite", "clientrestore").contains(stage());
    }

    public static boolean secondaryPresent() {
        return fixturesPresent() || stage().equals("sources");
    }

    public static Item temporaryBoots() {
        return new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, Identifier.parse(MOD + ":temporary_boots")))
                                             .durability(500).component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build()));
    }

    private static boolean candidate() {
        return System.getProperty("mismatchedboots.test.missingModsCase").equals("candidate");
    }

    private static boolean fabric() {
        return System.getProperty("mismatchedboots.runtimetest.loader").equals("fabric");
    }

    private static String energyId() {
        return fabric() ? MOD + ":temporary_energy" : "justdirethings:forge_energy";
    }

    private static DataComponentType<SuspendedBootsData> archiveType() {
        return component(SuspendedBootsItemCodec.ARCHIVE);
    }

    public static void tick(MinecraftServer server) {
        if (connected()) {
            return;
        }
        if (finished || ++ticks < 5) {
            return;
        }
        finished = true;
        try {
            switch (stage()) {
                case "write" -> seed(server);
                case "absent" -> absent(server);
                case "absentagain", "partial", "sources" -> repeated(server);
                case "restore", "reloaded" -> restore(server);
                default -> throw new AssertionError("Unknown missing-mod spike stage");
            }
            require(!candidate() || CaptureWitness.peerDecodes > 0, "Compatible peer did not participate in native decoding");
            NetworkAcceptanceFiles.write("missing-mods-success.txt", "passed " + stage() + "\n");
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure("missing-mods", failure);
        } finally {
            server.halt(false);
        }
    }

    private static void seed(MinecraftServer server) throws Exception {
        var ma = nativeBoot("mysticalagriculture:awakened_supremium_boots");
        var augmentClass = Class.forName("com.blakebr0.mysticalagriculture.api.tinkering.Augment");
        for (var entry : List.of("HEALTH_BOOST_III", "STEP_ASSIST")) {
            Object augment = Class.forName("com.blakebr0.mysticalagriculture.lib.ModAugments").getField(entry).get(null);
            MysticalNativeTestApi.call("com.blakebr0.mysticalagriculture.api.util.AugmentUtils", "addAugment",
                                       new Class<?>[]{ItemStack.class, augmentClass, int.class}, ma, augment,
                                       entry.equals("HEALTH_BOOST_III") ? 0 : 1);
        }
        require(MysticalNativeTestApi.nativeAugments(ma).size() == 2, "Native MA fixture lacks augments");
        CompoundTag opaque = new CompoundTag();
        opaque.putByteArray("bytes", new byte[]{-128, -1, 0, 1, 127});
        opaque.putLongArray("longs", new long[]{Long.MIN_VALUE, 9_007_199_254_740_993L, Long.MAX_VALUE});
        opaque.putIntArray("ints", new int[]{Integer.MIN_VALUE, 0, Integer.MAX_VALUE});
        ma.set(DataComponents.CUSTOM_DATA, CustomData.of(opaque));
        var jdt = fabric() ? ma.copy() : nativeBoot("justdirethings:eclipsealloy_boots");
        jdt.set(MissingModsProof.<Integer>component(energyId()), 200_000);
        if (!fabric()) {
            for (String upgrade : List.of("stepheight_upgrade_installed", "groundstomp_upgrade_installed")) {
                jdt.set(MissingModsProof.<Boolean>component("justdirethings:" + upgrade), true);
            }
        }
        var recipe = new MixedBootsWorkbenchRecipe();
        var maPair = recipe.assemblePair(new MismatchedWorkbenchInput(ma, new ItemStack(Items.DIAMOND_BOOTS)));
        var jdtPair = recipe.assemblePair(new MismatchedWorkbenchInput(jdt, new ItemStack(Items.DIAMOND_BOOTS)));
        var vanillaPair = recipe.assemblePair(new MismatchedWorkbenchInput(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS)));
        require(maPair.isComplete() && jdtPair.isComplete() && vanillaPair.isComplete(), "Native recipe fixture rejected");
        var roots = new ArrayList<>(List.of(maPair.primary(),
                                            maPair.inverse(),
                                            jdtPair.primary(),
                                            jdtPair.inverse(),
                                            vanillaPair.primary(),
                                            vanillaPair.inverse(),
                                            new ItemStack(BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID)),
                                            vanillaPair.primary().copy()));
        var dual = recipe.assemblePair(new MismatchedWorkbenchInput(ma,
                                                                    fabric() ? nativeBoot(MOD + ":temporary_boots") : jdt));
        require(dual.isComplete(), "Two-dependency fixture rejected");
        roots.add(dual.primary());
        roots.add(dual.inverse());
        for (int i = 8; i < 10; i++) {
            roots.get(i).set(MissingModsProof.<Integer>component(MOD + ":temporary_energy"), 1_987_654_321);
        }
        var enchantmentRoot = vanillaPair.primary().copy();
        enchantmentRoot.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                                      .get(Identifier.parse("mysticalagriculture:soul_siphoner")).orElseThrow(), 1);
        roots.add(enchantmentRoot);
        roots.get(7).set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).setEquipSound(
                BuiltInRegistries.SOUND_EVENT.get(Identifier.parse(MOD + ":temporary_equip")).orElseThrow()).build());
        for (int i = 2; i < 4; i++) {
            // Paid-root charge is deliberately different from the immutable source's charge.
            roots.get(i).set(MissingModsProof.<Integer>component(energyId()), 12_345 + i);
            if (!fabric()) {
                roots.get(i).set(MissingModsProof.<Boolean>component("justdirethings:tool_enabled"), false);
            }
        }
        if (!fabric()) {
            Object binding = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                                  .getConstructor(String.class, int.class, boolean.class, boolean.class).newInstance("groundstomp", 86, false, false);
            Object cooldown = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityCooldown")
                                   .getConstructor(String.class, int.class, boolean.class).newInstance("groundstomp", 37, false);
            for (int i = 8; i < 10; i++) {
                var root = roots.get(i);
                root.set(MissingModsProof.<List<Object>>component("justdirethings:ability_bindings"), List.of(binding));
                root.set(MissingModsProof.<List<Object>>component("justdirethings:ability_cooldowns"), List.of(cooldown));
                root.set(MissingModsProof.<Boolean>component("justdirethings:tool_enabled"), false);
            }
        }
        ItemStack attributeRoot = roots.get(4);
        var attributes = ItemAttributeModifiers.builder();
        for (var entry : attributeRoot.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            attributes.add(entry.attribute(), entry.modifier(), entry.slot());
        }
        attributes.add(BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(MOD + ":temporary_attribute")).orElseThrow(),
                       new AttributeModifier(Identifier.parse(MOD + ":projection"), 0.25,
                                             AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.FEET);
        attributeRoot.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes.build());
        server.overworld().setBlock(STORAGE, Blocks.BARREL.defaultBlockState(), 3);
        var barrel = storage(server);
        var baseline = new CompoundTag();
        for (int i = 0; i < roots.size(); i++) {
            var root = roots.get(i);
            if (i != 6) {
                root.setDamageValue(3);
            }
            root.set(DataComponents.CUSTOM_NAME, Component.literal("missing-mod seed " + i));
            root.set(DataComponents.CUSTOM_DATA, CustomData.of(opaque.copy()));
            baseline.put("root" + i, encode(server, root));
            barrel.setItem(i, root);
        }
        barrel.setChanged();
        NbtIo.writeCompressed(baseline, baselineFile());
        if (candidate()) {
            MissingModsModelChecks.run(server, roots.get(5));
            MissingModsInteractionChecks.run(server, roots.get(2));
            MissingModsInteractionChecks.breakDoesNotRefund(server, MissingModsInteractionChecks.suspend(server, roots.get(0)));
            if (!fabric()) {
                Class.forName("games.alejandrocoria.mismatchedboots.runtimetest.neoforge.missingmods.MissingModsSpike")
                     .getMethod("checkResources", MinecraftServer.class, ItemStack.class).invoke(null, server, roots.get(2));
            }
        }
        NetworkAcceptanceFiles.write("missing-mods-report.txt", "seeded eleven real barrel slots; "
                + (fabric() ? "native MA Fabric augments and temporary foreign counter" : "native MA NeoForge augments and paid-root JDT charge")
                + "; attribute/equip sound, typed arrays, vanilla/bare controls"
                + (candidate()
                   ? "; conflict/removal/native-normalization/conversion/filtering/source-presence/throwing-codec/native-container/invalid-equip checks; JSON active/suspended/restored booleans; real rename/workstation/wear/XP/refund checks passed"
                     + (fabric() ? "" : "; actual FE and retained-handler denial passed")
                   : "") + "\n");
    }

    private static void absent(MinecraftServer server) {
        require(BuiltInRegistries.ITEM.get(Identifier.parse("mysticalagriculture:awakened_supremium_boots")).isEmpty(), "MA still installed");
        require(BuiltInRegistries.DATA_COMPONENT_TYPE.get(Identifier.parse(energyId())).isEmpty(), "Foreign energy type still installed");
        var barrel = storage(server);
        StringBuilder report = new StringBuilder();
        for (int i = 0; i < ROOTS; i++) {
            ItemStack root = barrel.getItem(i);
            if (candidate()) {
                require(!root.isEmpty(), "Candidate lost root " + i);
                boolean protectedRoot = i < 5 || i >= 7;
                require(root.has(archiveType()) == protectedRoot, "Wrong archive presence at " + i);
                require(i == 6 || root.get(DataComponents.EQUIPPABLE).slot() == EquipmentSlot.FEET, "Existing equip slot lost");
                require(i != 6 || !root.has(DataComponents.EQUIPPABLE), "Bare /give acquired equip semantics");
                require(!protectedRoot || ModContent.getMixedBootsData(root).isEmpty(), "Partially restored gameplay became active");
                if (protectedRoot) {
                    require(root.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().isEmpty()
                                    && root.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty(),
                            "Suspended combat state leaked");
                    require(!root.get(DataComponents.EQUIPPABLE).damageOnHurt(), "Suspended shell takes combat wear");
                    report.append(i).append(" archiveBytes=").append(root.get(archiveType()).encodedSize())
                          .append(" parked=").append(root.get(archiveType()).original().keySet()).append('\n');
                    // Repeated persistent decoding while still absent must neither grow nor partially reactivate it.
                    ItemStack again = ItemStack.CODEC.parse(server.registryAccess().createSerializationContext(NbtOps.INSTANCE), encode(server, root))
                                                     .getOrThrow();
                    require(ItemStack.matches(root, again), "Repeated suspension changed protected state");
                }
                networkRoundTrip(server, root);
            } else {
                report.append(i).append(" empty=").append(root.isEmpty()).append(" sourceData=")
                      .append(ModContent.getMixedBootsData(root).isPresent()).append('\n');
                if (i < 4) {
                    require(root.isEmpty() || ModContent.getMixedBootsData(root).isEmpty(), "Native control unexpectedly kept unknown sources");
                }
            }
            if (!root.isEmpty()) {
                mutateWhileAbsent(root, i);
            }
        }
        barrel.setChanged();
        if (candidate()) {
            var baseline = readBaseline();
            var snapshot = new CompoundTag();
            for (int i = 0; i < ROOTS; i++) {
                snapshot.put("root" + i, encode(server, barrel.getItem(i)));
            }
            baseline.put("absent", snapshot);
            try {
                NbtIo.writeCompressed(baseline, baselineFile());
            } catch (java.io.IOException failure) {
                throw new IllegalStateException(failure);
            }
        }
        NetworkAcceptanceFiles.write("missing-mods-report.txt", report.toString());
    }

    private static void repeated(MinecraftServer server) {
        var snapshot = readBaseline().getCompoundOrEmpty("absent");
        var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        for (int i = 0; i < ROOTS; i++) {
            var root = storage(server).getItem(i);
            var raw = snapshot.getCompoundOrEmpty("root" + i);
            var expected = ItemStack.CODEC.parse(ops, raw).getOrThrow();
            require(encode(server, root).equals(encode(server, expected)), "Repeated/staged save changed root " + i);
            var retained = ModContent.suspendedBootsData(root);
            if (retained.isPresent()) {
                var previous = SuspendedBootsData.CODEC.parse(NbtOps.INSTANCE, raw.getCompoundOrEmpty("components")
                                                                                  .get(SuspendedBootsItemCodec.ARCHIVE)).getOrThrow();
                require(previous.equals(retained.get()) && previous.encodedSize() == retained.get().encodedSize(),
                        "Archive grew during staged return " + i);
            }
            if (i == 8 || i == 9) {
                require(retained.isPresent() && ModContent.getMixedBootsData(root).isEmpty(), "Dual-dependency root partially activated");
                if (stage().equals("sources")) {
                    require(ModContent.suspendedBootsData(root).orElseThrow().original().getIntOr(MOD + ":temporary_energy", 0) == 1_987_654_321,
                            "Root-only unavailable component was lost after both sources returned");
                    require(BuiltInRegistries.ITEM.containsKey(Identifier.parse("mysticalagriculture:awakened_supremium_boots"))
                                    && BuiltInRegistries.ITEM.containsKey(Identifier.parse(fabric()
                                                                                           ? MOD + ":temporary_boots"
                                                                                           : "justdirethings:eclipsealloy_boots")),
                            "Root-only failure stage lacks a source registration");
                }
            }
            networkRoundTrip(server, root);
        }
        NetworkAcceptanceFiles.write("missing-mods-report.txt", "eleven roots stable across " + stage()
                + "; no archive growth or partial dual-source/root-component activation\n");
    }

    static CompoundTag readBaseline() {
        try {
            return NbtIo.readCompressed(baselineFile(), NbtAccounter.unlimitedHeap());
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void restore(MinecraftServer server) throws Exception {
        var original = NbtIo.readCompressed(baselineFile(), NbtAccounter.unlimitedHeap());
        var barrel = storage(server);
        StringBuilder report = new StringBuilder();
        for (int i = 0; i < ROOTS; i++) {
            var root = barrel.getItem(i);
            ItemStack expected = ItemStack.CODEC.parse(server.registryAccess().createSerializationContext(NbtOps.INSTANCE),
                                                       original.getCompoundOrEmpty("root" + i)).getOrThrow();
            mutateWhileAbsent(expected, i);
            boolean same = !root.isEmpty() && encode(server, root).equals(encode(server, expected));
            report.append(i).append(" completeStateEqual=").append(same).append('\n');
            if (candidate()) {
                require(same, "Incomplete restored root " + i + "\nexpected=" + encode(server, expected) + "\nactual=" + encode(server, root));
                require(!root.has(archiveType()), "Restored root retained unresolved archive");
                require(i == 6 || ModContent.getMixedBootsData(root).isPresent(), "Source history not restored");
                networkRoundTrip(server, root);
            } else {
                require(same == (i == 5 || i == 6), "Native control changed its expected loss boundary at " + i);
            }
        }
        NetworkAcceptanceFiles.write("missing-mods-report.txt", report.toString());
    }

    static void mutateWhileAbsent(ItemStack root, int index) {
        if (index != 6 && root.has(DataComponents.MAX_DAMAGE)) {
            root.setDamageValue(root.getDamageValue() + 7);
        }
        root.set(DataComponents.CUSTOM_NAME, Component.literal("changed while absent " + index));
        root.remove(DataComponents.CUSTOM_DATA); // An explicit removal must not be undone by restoration.
    }

    private static void networkRoundTrip(MinecraftServer server, ItemStack root) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buffer, root);
            ItemStack decoded = ItemStack.STREAM_CODEC.decode(buffer);
            require(ItemStack.matches(root, decoded), "Network changed preserved data");
        } finally {
            buffer.release();
        }
    }

    static CompoundTag encode(MinecraftServer server, ItemStack root) {
        return (CompoundTag) ItemStack.CODEC.encodeStart(server.registryAccess().createSerializationContext(NbtOps.INSTANCE), root).getOrThrow();
    }

    private static Path baselineFile() {
        return Path.of(System.getProperty("mismatchedboots.test.missingModsBaseline"));
    }

    static BarrelBlockEntity storage(MinecraftServer server) {
        var barrel = (BarrelBlockEntity) server.overworld().getBlockEntity(STORAGE);
        require(barrel != null, "World lost its real barrel");
        return barrel;
    }

    private static ItemStack nativeBoot(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Identifier.parse(id)).orElseThrow());
    }

    @SuppressWarnings("unchecked")
    static <T> DataComponentType<T> component(String id) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(Identifier.parse(id)).orElseThrow().value();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
