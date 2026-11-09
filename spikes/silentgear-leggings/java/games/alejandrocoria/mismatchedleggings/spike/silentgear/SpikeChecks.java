package games.alejandrocoria.mismatchedleggings.spike.silentgear;

import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.silentchaos512.gear.api.item.GearItem;
import net.silentchaos512.gear.api.traits.TraitActionContext;
import net.silentchaos512.gear.api.util.DataResource;
import net.silentchaos512.gear.gear.material.MaterialInstance;
import net.silentchaos512.gear.gear.part.PartInstance;
import net.silentchaos512.gear.gear.trait.effect.WielderEffectTraitEffect;
import net.silentchaos512.gear.setup.SgDataComponents;
import net.silentchaos512.gear.setup.gear.GearTypes;
import net.silentchaos512.gear.util.GearData;
import net.silentchaos512.gear.util.GearHelper;
import net.silentchaos512.gear.util.TraitHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SpikeChecks {
    private static final StringBuilder REPORT = new StringBuilder();
    private static boolean finished;

    private SpikeChecks() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> run(event.getServer()));
    }

    private static void run(MinecraftServer server) {
        if (finished) { return; }
        finished = true;
        try {
            var player = player(server);
            numeric(player);
            dynamic(player);
            oxidation(player);
            sets(server);
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "silentgear.txt"), REPORT);
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            try { Files.writeString(Path.of(System.getProperty("leggings.test.report"), "silentgear.txt"), REPORT); }
            catch (java.io.IOException reportFailure) { failure.addSuppressed(reportFailure); }
            SkeletonChecks.failed("server", failure);
        } finally {
            server.halt(false);
        }
    }

    private static void numeric(ServerPlayer player) throws Exception {
        boolean duplicateGap = false;
        for (String material : List.of("iron", "feather", "quartz", "bone", "copper")) {
            var source = armor("leggings", "legging_plates", material);
            var samples = new ArrayList<ItemStack>();
            samples.add(source);
            if (material.equals("iron")) { samples.add(lined("iron", "phantom_membrane")); }
            for (var input : samples) {
                input.setDamageValue(5);
                var before = input.copy();
                var other = new ItemStack(Items.DIAMOND_LEGGINGS);
                other.setDamageValue(11);
                var pair = pair(input, other);
                for (var root : pair) {
                    var sources = Candidate.sources(root);
                    var normalized = Candidate.merge(sources.getFirst(), sources.getLast()).primary();
                    int max = (input.getMaxDamage() + other.getMaxDamage()) / 2;
                    int remaining = (input.getMaxDamage() - 5 + other.getMaxDamage() - 11) / 2;
                    require(root.getMaxDamage() == max && root.getDamageValue() == max - remaining, "Composed LEGS durability differs");
                    close(amount(normalized, "minecraft:armor", EquipmentSlot.LEGS),
                            (amount(Candidate.attributes(input), "minecraft:armor", EquipmentSlot.LEGS) + 6) / 2, "Normalized armor");
                    if (TraitHelper.getTraitLevel(input, DataResource.trait("silentgear:light")) > 0) {
                        double nativeSpeed = amount(Candidate.attributes(input), "minecraft:movement_speed", EquipmentSlot.LEGS);
                        double oldSpeed = amount(root.getAttributeModifiers(), "minecraft:movement_speed", EquipmentSlot.LEGS);
                        double newSpeed = amount(normalized, "minecraft:movement_speed", EquipmentSlot.LEGS);
                        close(newSpeed, nativeSpeed / 2, "Candidate Light must average in both outputs");
                        duplicateGap |= Math.abs(oldSpeed - newSpeed) > 1E-8;
                        double nativeWorn = wornSpeed(player, input);
                        double oldWorn = wornSpeed(player, root);
                        var proposed = root.copy();
                        proposed.set(DataComponents.ATTRIBUTE_MODIFIERS, normalized);
                        double newWorn = wornSpeed(player, proposed);
                        close(newWorn, nativeWorn / 2, "Actual equipped candidate Light");
                        REPORT.append("WORN LIGHT native/old/candidate=").append(nativeWorn).append('/')
                                .append(oldWorn).append('/').append(newWorn).append('\n');
                    }
                    REPORT.append("NUMERIC ").append(material).append(" owner=").append(sources.getFirst().getItem())
                            .append(" native raw/distinct=").append(input.getAttributeModifiers().modifiers().size()).append('/')
                            .append(Candidate.attributes(input).modifiers().size()).append(" root max/damage=").append(max).append('/')
                            .append(root.getDamageValue()).append(" oldSpeed=").append(amount(root.getAttributeModifiers(), "minecraft:movement_speed", EquipmentSlot.LEGS))
                            .append(" candidateSpeed=").append(amount(normalized, "minecraft:movement_speed", EquipmentSlot.LEGS))
                            .append(" oldMagic=").append(amount(root.getAttributeModifiers(), "silentgear:magic_armor", EquipmentSlot.LEGS))
                            .append(" candidateMagic=").append(amount(normalized, "silentgear:magic_armor", EquipmentSlot.LEGS)).append('\n');
                    require(!GearHelper.isGear(root) && TraitHelper.getTraits(root).isEmpty(), "Mixed root became native gear");
                }
                require(ItemStack.matches(input, before), "Numeric measurement mutated input");
            }
        }
        require(duplicateGap, "Expected native effective duplicate/ambiguity was not reproduced; revise normalization hypothesis");
        var magic = BuiltInRegistries.ATTRIBUTE.get(Identifier.parse("silentgear:magic_armor")).orElseThrow();
        require(player.getAttribute(magic) == null, "Pinned native Magic Armor limitation changed");
        REPORT.append("Native player has no Magic Armor instance; candidate default changes composition only\n");
    }

    private static void dynamic(ServerPlayer player) throws Exception {
        for (String material : List.of("quartz", "bone")) {
            var source = armor("leggings", "legging_plates", material);
            var history = source.copy();
            for (var root : pair(source, new ItemStack(Items.DIAMOND_LEGGINGS))) {
                root.set(DataComponents.CUSTOM_NAME, Component.literal("projection sentinel"));
                double healthy = amount(root.getAttributeModifiers(), "minecraft:armor", EquipmentSlot.LEGS);
                root.setDamageValue(root.getMaxDamage() / 2);
                var control = source.copy();
                control.set(DataComponents.DAMAGE, (int) ((long) root.getDamageValue() * control.getMaxDamage() / root.getMaxDamage()));
                GearData.recalculateGearData(control, player);
                double expected = (amount(Candidate.attributes(control), "minecraft:armor", EquipmentSlot.LEGS) + 6) / 2;
                require(Math.abs(expected - healthy) > 1E-5, "No native wear-sensitive positive for " + material);
                root.inventoryTick(player.level(), player, EquipmentSlot.LEGS);
                close(amount(root.getAttributeModifiers(), "minecraft:armor", EquipmentSlot.LEGS), healthy, "Baseline must remain stale");
                var protectedRoot = root.copy();
                Candidate.refreshArmor(root, player);
                close(amount(root.getAttributeModifiers(), "minecraft:armor", EquipmentSlot.LEGS), expected, "Candidate dynamic armor");
                var changed = root.get(DataComponents.ATTRIBUTE_MODIFIERS);
                root.set(DataComponents.ATTRIBUTE_MODIFIERS, protectedRoot.get(DataComponents.ATTRIBUTE_MODIFIERS));
                require(ItemStack.matches(root, protectedRoot), "Projection escaped root attribute ownership");
                root.set(DataComponents.ATTRIBUTE_MODIFIERS, changed);
                require(Candidate.sources(root).stream().anyMatch(s -> ItemStack.matches(s, history)), "Dynamic source history changed");
                REPORT.append("DYNAMIC ").append(material).append(" healthy/stale=").append(healthy).append(" projected=").append(expected).append('\n');
            }
            require(ItemStack.matches(source, history), "Dynamic native writer reached input");
        }
    }

    private static void oxidation(ServerPlayer player) throws Exception {
        for (boolean coated : List.of(false, true)) {
            var source = armor("leggings", "legging_plates", "copper", coated ? new String[]{"coating", "netherite"} : new String[0]);
            source.set(SgDataComponents.OXIDATION_COUNTER.get(), 1199);
            var younger = source.copy();
            younger.set(SgDataComponents.OXIDATION_COUNTER.get(), 10);
            for (var root : pair(source, younger)) {
                var before = root.copy();
                player.tickCount = 20;
                root.inventoryTick(player.level(), player, null);
                require(ItemStack.matches(before, root), "Baseline root unexpectedly runs SG oxidation");
                Candidate.seed(root);
                var histories = Candidate.sources(root);
                var controls = new ArrayList<ItemStack>();
                for (var history : histories) {
                    var control = history.copy();
                    player.tickCount = 20;
                    control.inventoryTick(player.level(), player, null);
                    player.tickCount = 21;
                    control.inventoryTick(player.level(), player, null);
                    controls.add(control);
                }
                player.tickCount = 20;
                Candidate.advance(root, player);
                var data = root.get(DataComponents.CUSTOM_DATA).copyTag();
                for (int side = 0; side < 2; side++) {
                    var lane = data.getCompound("sg_spike_" + side).orElseThrow();
                    var control = controls.get(side);
                    require(lane.getIntOr("stage", -1) == control.getOrDefault(SgDataComponents.OXIDATION_STAGE.get(), (byte) 0)
                            && lane.getIntOr("counter", -1) == control.getOrDefault(SgDataComponents.OXIDATION_COUNTER.get(), 0), "Root oxidation differs from native clock/stage");
                    REPORT.append("OXIDATION coated=").append(coated).append(" side=").append(side).append(' ').append(lane).append('\n');
                }
                var ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
                var decoded = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, root).getOrThrow()).getOrThrow();
                require(ItemStack.matches(root, decoded), "Prototype root state/history NBT roundtrip; changed components="
                        + root.getComponents().stream().filter(c -> !java.util.Objects.equals(c.value(), decoded.get(c.type()))).map(c -> c.type().toString()).toList());
                var state = root.get(DataComponents.CUSTOM_DATA);
                root.set(DataComponents.CUSTOM_DATA, before.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY));
                if (!before.has(DataComponents.CUSTOM_DATA)) { root.remove(DataComponents.CUSTOM_DATA); }
                require(ItemStack.matches(root, before), "Oxidation escaped prototype state; root/native source was recalculated");
                root.set(DataComponents.CUSTOM_DATA, state);
            }
        }
    }

    private static void sets(MinecraftServer server) throws Exception {
        var source = armor("leggings", "legging_plates", "refined_iron");
        var trait = TraitHelper.getTraits(source).stream().filter(t -> t.getTraitId().getPath().equals("stellar")).findFirst().orElseThrow();
        var effect = trait.getTrait().getEffects().stream().filter(e -> e instanceof WielderEffectTraitEffect).findFirst().orElseThrow();
        var access = (SetAccess) effect;
        boolean ready = !List.of("baseline", "refuse").contains(System.getProperty("leggings.test.sgSpikeMode"));
        require(access.spike$ready() == ready, "Final LEGS local guard differs from expected candidate/refusal");
        require(SpikePlugin.actualOrder != null, "Actual sorted mixin order was not observed");
        REPORT.append("SET actual order ").append(SpikePlugin.actualOrder).append(" ready=").append(ready).append('\n');
        boolean boots = Boolean.getBoolean("leggings.test.boots");
        if (boots) {
            require((boolean) effect.getClass().getMethod("mismatchedboots$sgSetReady").invoke(effect), "Current Boots final set proof refused compatible LEGS hook");
        }
        var roots = new ArrayList<>(pair(source, new ItemStack(Items.DIAMOND_LEGGINGS)));
        roots.addAll(pair(source, source));
        for (var root : roots) {
            for (int pieces = 1; pieces <= 4; pieces++) {
                var subject = player(server);
                var control = player(server);
                subject.tickCount = 20;
                control.tickCount = 20;
                var before = root.copy();
                var slots = List.of(EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HEAD, EquipmentSlot.CHEST);
                var nativePieces = List.of(source, armor("boots", "boot_plates", "refined_iron"),
                        armor("helmet", "helmet_plates", "refined_iron"), armor("chestplate", "chestplate_plates", "refined_iron"));
                for (int i = 0; i < pieces; i++) {
                    var nativePiece = nativePieces.get(i).copy();
                    subject.setItemSlot(slots.get(i), i == 0 ? root : i == 1 && boots ? mixedBoots(nativePiece) : nativePiece);
                    if (i != 0 || ready) { control.setItemSlot(slots.get(i), nativePiece.copy()); }
                }
                var context = new TraitActionContext(subject, trait, Candidate.carrier(root));
                int expectedCount = pieces - (ready ? 0 : 1);
                require(access.spike$count(context, GearTypes.ARMOR.get(), subject) == expectedCount, "Native count must recognize each mixed slot once");
                Candidate.calls = 0;
                for (int i = 0; i < pieces; i++) {
                    var stack = subject.getItemBySlot(slots.get(i));
                    if (i == 0) { Candidate.wearer(root, subject); }
                    else if (i == 1 && boots) { stack.inventoryTick(subject.level(), subject, EquipmentSlot.FEET); }
                    else { effect.inventoryTick(new TraitActionContext(subject, trait, stack), subject.level(), subject, true); }
                    var original = control.getItemBySlot(slots.get(i));
                    if (!original.isEmpty()) { effect.inventoryTick(new TraitActionContext(control, trait, original), control.level(), control, true); }
                }
                require(Candidate.calls == (ready ? 1 : 0), "Duplicate-source Wielder dispatch was not once per trait");
                require(effects(subject).equals(effects(control)), "Native/mixed Wielder effect parameters differ: " + effects(subject) + " vs " + effects(control));
                require(ItemStack.matches(root, before) && subject.getItemBySlot(EquipmentSlot.LEGS) == root, "Wielder replaced real equipment or changed root/history");
                REPORT.append("SET pieces=").append(pieces).append(" count=").append(expectedCount).append(" effects=").append(effects(subject)).append('\n');
            }
            var player = player(server);
            require(!Candidate.recognizes(new TraitActionContext(player, trait, source), EquipmentSlot.MAINHAND, root), "Held root counted as LEGS");
            require(!Candidate.recognizes(new TraitActionContext(player, trait, source), EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS)), "Foreign root recognized");
        }
    }

    private static List<String> effects(ServerPlayer player) {
        return player.getActiveEffects().stream().map(e -> e.toString()).sorted().toList();
    }

    private static double wornSpeed(ServerPlayer player, ItemStack stack) throws Exception {
        var update = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates");
        update.setAccessible(true);
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        update.invoke(player);
        double base = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        player.setItemSlot(EquipmentSlot.LEGS, stack);
        update.invoke(player);
        double bonus = player.getAttributeValue(Attributes.MOVEMENT_SPEED) / base - 1;
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        update.invoke(player);
        return bonus;
    }

    private static ItemStack mixedBoots(ItemStack source) throws Exception {
        var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
        var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, source, new ItemStack(Items.DIAMOND_BOOTS));
        return (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
    }

    private static List<ItemStack> pair(ItemStack first, ItemStack second) {
        var pair = MixedLeggingsFactory.createPair(first, second);
        return List.of(pair.primary(), pair.inverse());
    }

    private static ItemStack armor(String name, String plates, String material, String... extra) throws Exception {
        var parts = new ArrayList<PartInstance>();
        parts.add(PartInstance.from(part(plates, material)));
        for (int i = 0; i < extra.length; i += 2) { parts.add(PartInstance.from(part(extra[i], extra[i + 1]))); }
        return ((GearItem) BuiltInRegistries.ITEM.getValue(Identifier.parse("silentgear:" + name))).construct(parts);
    }

    private static ItemStack lined(String material, String lining) throws Exception {
        return armor("leggings", "legging_plates", material, "lining", lining);
    }

    private static ItemStack part(String name, String material) throws Exception {
        var item = BuiltInRegistries.ITEM.getValue(Identifier.parse("silentgear:" + name));
        return (ItemStack) item.getClass().getMethod("create", List.class).invoke(item, List.of(MaterialInstance.of(DataResource.material("silentgear:" + material))));
    }

    static double amount(ItemAttributeModifiers modifiers, String attribute, EquipmentSlot slot) {
        return modifiers.modifiers().stream().filter(e -> e.slot().test(slot) && e.attribute().unwrapKey().orElseThrow().identifier().toString().equals(attribute))
                .filter(e -> e.modifier().operation() == AttributeModifier.Operation.ADD_VALUE || e.modifier().operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
                .mapToDouble(e -> e.modifier().amount()).sum();
    }

    private static ServerPlayer player(MinecraftServer server) throws Exception {
        var profile = new GameProfile(UUID.randomUUID(), "SgLegsSpike");
        var player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
        var loaded = ServerGamePacketListenerImpl.class.getDeclaredMethod("markClientLoaded");
        loaded.setAccessible(true);
        loaded.invoke(player.connection);
        player.setGameMode(GameType.SURVIVAL);
        server.overworld().getChunkAt(new BlockPos(0, server.overworld().getMinY() + 3, 0));
        return player;
    }

    static void close(double actual, double expected, String message) { require(Math.abs(actual - expected) < 1E-6, message + ": " + actual + " != " + expected); }
    public static void require(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
