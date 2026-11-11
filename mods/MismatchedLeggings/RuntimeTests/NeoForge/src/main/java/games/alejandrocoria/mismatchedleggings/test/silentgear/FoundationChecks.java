package games.alejandrocoria.mismatchedleggings.test.silentgear;

import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedleggings.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearCompatibility;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearState;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsRecomposer;
import games.alejandrocoria.mismatchedleggings.composition.NeoForgeCompositionAttributes;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import games.alejandrocoria.mismatchedleggings.test.WorkbenchChecks;
import games.alejandrocoria.mismatchedthings.api.composition.AttributePolicy;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.neoforged.fml.ModList;
import net.silentchaos512.gear.api.item.GearItem;
import net.silentchaos512.gear.api.property.TraitListPropertyValue;
import net.silentchaos512.gear.api.traits.TraitInstance;
import net.silentchaos512.gear.api.util.DataResource;
import net.silentchaos512.gear.core.component.GearPropertiesData;
import net.silentchaos512.gear.gear.material.MaterialInstance;
import net.silentchaos512.gear.gear.part.PartInstance;
import net.silentchaos512.gear.gear.trait.condition.GearTypeTraitCondition;
import net.silentchaos512.gear.setup.SgDataComponents;
import net.silentchaos512.gear.setup.gear.GearProperties;
import net.silentchaos512.gear.setup.gear.GearTypes;
import net.silentchaos512.gear.util.GearData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Native controls and real paid extraction exercise the packaged production adapter, never the spike. */
public final class FoundationChecks {
    private static boolean finished;
    private static boolean magnetChunkPrepared;
    private static final StringBuilder REPORT = new StringBuilder();

    private FoundationChecks() {}

    public static void tick(MinecraftServer server) {
        if (!finished && !magnetChunkPrepared && !System.getProperty("leggings.test.sgMagnetVariant", "").isEmpty()
                && !Boolean.getBoolean("leggings.test.sgFoundationAbsent")) {
            // The fixture player is not connected: keep its origin chunk tracked before querying real item entities.
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    server.overworld().setChunkForced(x, z, true);
                    server.overworld().getChunk(x, z);
                }
            }
            magnetChunkPrepared = true;
            return;
        }
        if (finished || server.getTickCount() < 20) { return; }
        finished = true;
        try {
            SkeletonChecks.verify();
            require(ModList.get().isLoaded("mismatchedboots") == Boolean.getBoolean("leggings.test.boots"), "Wrong sibling graph");
            boolean absent = Boolean.getBoolean("leggings.test.sgFoundationAbsent");
            require(ModList.get().isLoaded("silentgear") != absent, "Wrong native graph");
            var player = player(server);
            ordinary(server, player);
            BrilliantChecks.ordinary(player, REPORT);
            if (!absent && System.getProperty("leggings.test.sgLiveVariant", "").equals("boundary")) {
                LiveStateChecks.refusal(player, REPORT);
            } else if (!absent) {
                numeric(server, player);
                clocks(server, player);
                rejection(player);
                EffectsChecks.run(player, REPORT);
                if (ModList.get().isLoaded("mismatchedboots")) { sibling(); }
                LiveStateChecks.run(player, REPORT);
                BrilliantChecks.run(player, REPORT);
                WielderChecks.run(player, REPORT);
            }
            MagnetChecks.run(player, REPORT, absent);
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "foundation.txt"), REPORT);
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            SkeletonChecks.failed("server", failure);
        } finally {
            if (magnetChunkPrepared) {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) { server.overworld().setChunkForced(x, z, false); }
                }
            }
            server.halt(false);
        }
    }

    private static void ordinary(MinecraftServer server, ServerPlayer player) {
        var foreign = new SilentGearState(Optional.of(new SilentGearState.Oxidation("polished", 1, 0, 50)),
                Optional.empty(), AttributePolicy.SEPARATE, List.of(), false, Optional.empty());
        var source = new ItemStack(Items.IRON_LEGGINGS);
        source.set(SilentGearCompatibility.state(), foreign);
        var base = source.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        var duplicates = new ArrayList<>(base.modifiers());
        duplicates.addAll(base.modifiers());
        source.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(duplicates));
        var query = new NeoForgeCompositionAttributes().query(source);
        require(query.modifiers().size() == duplicates.size(), "Non-SG duplicate entries were normalized");
        for (var root : pair(source, new ItemStack(Items.DIAMOND_LEGGINGS))) {
            require(!root.has(SilentGearCompatibility.state()), "Foreign copied state manufactured SG authority");
            root.setDamageValue(10);
            var before = root.copy();
            root.inventoryTick(player.level(), player, EquipmentSlot.LEGS);
            require(ItemStack.matches(before, root), "Ordinary inventory tick activated SG state/repair");
            root.hurtAndBreak(3, player, EquipmentSlot.LEGS);
            before.setDamageValue(13);
            require(ItemStack.matches(before, root), "Ordinary wear changed beyond its single root debit");
        }
        // The same registered scalar schema and network codec work in a JVM without any SG classes.
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        try {
            SilentGearCompatibility.state().streamCodec().encode(buffer, foreign);
            require(foreign.equals(SilentGearCompatibility.state().streamCodec().decode(buffer)) && !buffer.isReadable(), "State network roundtrip");
        } finally { buffer.release(); }
        var encoded = SilentGearState.CODEC.encodeStart(NbtOps.INSTANCE, foreign).getOrThrow();
        require(foreign.equals(SilentGearState.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow()), "State persistent roundtrip");
        var malformed = ((net.minecraft.nbt.CompoundTag) encoded).copy();
        malformed.getCompound("left").orElseThrow().putInt("counter", -1);
        var badNetwork = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        try {
            net.minecraft.network.codec.ByteBufCodecs.TAG.encode(badNetwork, malformed);
            boolean rejected = false;
            try { SilentGearCompatibility.state().streamCodec().decode(badNetwork); }
            catch (io.netty.handler.codec.DecoderException expected) { rejected = true; }
            require(rejected, "Malformed network state decoded");
        } finally { badNetwork.release(); }
        paid(player, new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS));
        REPORT.append("Ordinary paid extraction; non-SG duplicate/copy isolation; native-independent state codecs passed\n");
    }

    private static void numeric(MinecraftServer server, ServerPlayer player) throws Exception {
        for (String material : List.of("iron", "feather", "quartz", "bone", "copper")) {
            var input = armor(material, material.equals("iron") ? new String[]{"lining", "phantom_membrane"} : new String[0]);
            input.setDamageValue(5);
            var donor = new ItemStack(Items.DIAMOND_LEGGINGS);
            donor.setDamageValue(11);
            var history = input.copy();
            var query = new NeoForgeCompositionAttributes().query(input);
            require(query.modifiers().equals(input.getAttributeModifiers().modifiers().stream().distinct().toList()), "Canonical SG normalization differs");
            double nativeSpeed = worn(player, input, Attributes.MOVEMENT_SPEED);
            double nativeFlutter = worn(player, input, Attributes.SAFE_FALL_DISTANCE);
            var roots = paid(player, input, donor);
            for (var root : roots) {
                close(worn(player, root, Attributes.MOVEMENT_SPEED), nativeSpeed / 2, "Effective equipped Light average");
                close(worn(player, root, Attributes.SAFE_FALL_DISTANCE), nativeFlutter / 2, "Effective equipped Flutter average");
                require(root.has(SilentGearCompatibility.state()), "SG root lacks foundation state");
                require(root.getMaxDamage() == (input.getMaxDamage() + donor.getMaxDamage()) / 2 && root.getDamageValue() == 8,
                        "Standard durability changed");
                require(MixedLeggingsStructure.validData(root).orElseThrow().left().source().getDamageValue()
                        + MixedLeggingsStructure.validData(root).orElseThrow().right().source().getDamageValue() == 16, "Source history changed");
                roundtrip(server, root);
            }
            require(ItemStack.matches(input, history), "Preview/payment mutated native input");
            if (material.equals("iron")) { close(nativeSpeed, .002, "Native Light +2% of .1 player base"); }
            if (material.equals("feather")) { require(nativeFlutter > 0, "Missing independent Flutter LEGS positive"); }
            double magic = amount(query, Identifier.parse("silentgear:magic_armor"));
            for (var root : roots) { close(amount(root.get(DataComponents.ATTRIBUTE_MODIFIERS), Identifier.parse("silentgear:magic_armor")), magic / 2, "Magic Armor default"); }
            REPORT.append(material).append(": native/mixed equipped Light=").append(nativeSpeed).append('/').append(nativeSpeed / 2)
                    .append(" Flutter=").append(nativeFlutter).append('/').append(nativeFlutter / 2).append('\n');
        }
        var iron = armor("iron");
        var rules = CompositionRules.defaults();
        var magic = Identifier.parse("silentgear:magic_armor");
        var explicit = new CompositionRules(new AttributeRuleSet(AttributePolicy.SEPARATE, Map.of(magic, AttributePolicy.MAXIMUM)), rules.components());
        var configured = MixedLeggingsFactory.createPair(iron, new ItemStack(Items.DIAMOND_LEGGINGS), explicit);
        double nativeMagic = amount(new NeoForgeCompositionAttributes().query(iron), magic);
        close(amount(configured.primary().get(DataComponents.ATTRIBUTE_MODIFIERS), magic), nativeMagic, "Explicit Magic Armor policy");
        close(amount(configured.inverse().get(DataComponents.ATTRIBUTE_MODIFIERS), magic), nativeMagic, "Inverse explicit policy");
        require(player.getAttribute(BuiltInRegistries.ATTRIBUTE.get(magic).orElseThrow()) == null, "Native player Magic Armor limitation changed");
        REPORT.append("Explicit Magic Armor policy wins; native player still lacks attribute instance\n");
    }

    private static void clocks(MinecraftServer server, ServerPlayer player) throws Exception {
        var older = armor("copper");
        older.set(SgDataComponents.OXIDATION_COUNTER.get(), 1199);
        var younger = older.copy();
        younger.set(SgDataComponents.OXIDATION_COUNTER.get(), 10);
        // Root-owned state copied onto an input must not override either native lane.
        older.set(SilentGearCompatibility.state(), new SilentGearState(Optional.empty(), Optional.empty(),
                AttributePolicy.REJECT, List.of(), true, Optional.empty()));
        for (var inputs : List.of(List.of(older, younger), List.of(older, new ItemStack(Items.DIAMOND_LEGGINGS)))) {
            for (var root : paid(player, inputs.get(0), inputs.get(1))) {
                var data = MixedLeggingsStructure.validData(root).orElseThrow();
                var state = root.get(SilentGearCompatibility.state());
                for (int side = 0; side < 2; side++) {
                    var source = side == 0 ? data.left().source() : data.right().source();
                    var lane = side == 0 ? state.left() : state.right();
                    require(lane.isPresent() == SilentGearCompatibility.leggings(source), "Lane orientation/eligibility");
                    lane.ifPresent(value -> require(value.counter() == source.getOrDefault(SgDataComponents.OXIDATION_COUNTER.get(), 0)
                            && value.phase().equals("polished") && value.level() == 1, "Unequal source clock/default lost"));
                }
                // Simulate persisted live clocks differing from both historical inputs; trim must preserve them.
                state = new SilentGearState(state.left().map(value -> new SilentGearState.Oxidation(value.phase(), value.level(), value.stage(), 77)),
                        state.right().map(value -> new SilentGearState.Oxidation(value.phase(), value.level(), value.stage(), 88)),
                        state.armorPolicy(), state.armorProjection(), state.armorOverridden(), state.sample());
                root.set(SilentGearCompatibility.state(), state);
                var trimLeft = data.left().source();
                trimLeft.set(DataComponents.TRIM, new ArmorTrim(server.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL).get(Identifier.withDefaultNamespace("quartz")).orElseThrow(),
                        server.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).get(Identifier.withDefaultNamespace("coast")).orElseThrow()));
                var recomposed = MixedLeggingsRecomposer.recompose(root, trimLeft, data.right().source());
                require(state.equals(recomposed.get(SilentGearCompatibility.state())), "Trim reset clocks/ownership");
                var before = root.copy();
                root.inventoryTick(player.level(), player, EquipmentSlot.LEGS);
                require(ItemStack.matches(root, before), "Selected tick changed an unchanged projection/clock");
                roundtrip(server, root);
                root.remove(SilentGearCompatibility.state());
                var missing = MixedLeggingsRecomposer.recompose(root, trimLeft, data.right().source());
                require(!missing.has(SilentGearCompatibility.state()), "Missing state lazily reseeded");
            }
        }
        var root = pair(older, younger).getFirst();
        var data = MixedLeggingsStructure.validData(root).orElseThrow();
        var attrs = root.get(DataComponents.ATTRIBUTE_MODIFIERS);
        root.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(attrs.modifiers().stream().filter(e -> !SilentGearState.managed(e)).toList()));
        var overridden = MixedLeggingsRecomposer.recompose(root, data.left().source(), data.right().source());
        require(overridden.get(SilentGearCompatibility.state()).armorOverridden()
                && SilentGearState.Projection.capture(overridden.get(DataComponents.ATTRIBUTE_MODIFIERS)).isEmpty(), "External managed removal was reclaimed");
        overridden.set(DataComponents.ATTRIBUTE_MODIFIERS, attrs);
        var coincident = MixedLeggingsRecomposer.recompose(overridden, data.left().source(), data.right().source());
        require(coincident.get(SilentGearCompatibility.state()).armorOverridden(), "Numerical coincidence reclaimed ownership");
        var conditional = armor("copper");
        var properties = new java.util.HashMap<>(GearData.getProperties(conditional).properties());
        properties.put(GearProperties.TRAITS.get(), new TraitListPropertyValue(List.of(TraitInstance.of(
                DataResource.trait("silentgear:polished"), 1, new GearTypeTraitCondition(GearTypes.BOOTS)))));
        conditional.set(SgDataComponents.GEAR_PROPERTIES.get(), new GearPropertiesData(properties));
        for (var result : pair(conditional, new ItemStack(Items.DIAMOND_LEGGINGS))) {
            var captured = result.get(SilentGearCompatibility.state());
            require(captured.left().isEmpty() && captured.right().isEmpty(), "Unmatched native trait condition seeded a lane");
        }
        var removed = MixedLeggingsRecomposer.recompose(root, conditional, data.right().source());
        require(removed.get(SilentGearCompatibility.state()).left().isEmpty(), "Disappeared eligibility retained lane");
        var oxidized = armor("copper");
        var nativeProperties = new java.util.HashMap<>(GearData.getProperties(oxidized).properties());
        nativeProperties.put(GearProperties.TRAITS.get(), new TraitListPropertyValue(List.of(TraitInstance.of(DataResource.trait("silentgear:oxidized"), 2))));
        oxidized.set(SgDataComponents.GEAR_PROPERTIES.get(), new GearPropertiesData(nativeProperties));
        oxidized.set(SgDataComponents.OXIDATION_STAGE.get(), (byte) 1);
        oxidized.set(SgDataComponents.OXIDATION_COUNTER.get(), 500);
        var captured = pair(oxidized, new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst().get(SilentGearCompatibility.state());
        require(captured.left().orElseThrow().equals(new SilentGearState.Oxidation("oxidized", 2, 1, 500)), "Explicit native oxidized phase/level lost");
        REPORT.append("One/two-sided independent clocks, inverse orientation, trim, missing/copy isolation and sticky overrides passed\n");
    }

    private static void rejection(ServerPlayer player) throws Exception {
        for (int invalid : List.of(-1, 1201, Integer.MAX_VALUE)) {
            var source = armor("copper");
            source.set(SgDataComponents.OXIDATION_COUNTER.get(), invalid);
            var donor = new ItemStack(Items.DIAMOND_LEGGINGS);
            var menu = new WorkbenchMenu(401, player.getInventory());
            menu.getSlot(0).set(source.copy());
            menu.getSlot(1).set(donor.copy());
            require(!menu.getSlot(2).hasItem() && !menu.getSlot(3).hasItem(), "Malformed SG input exposed preview");
            menu.clicked(2, 0, ContainerInput.PICKUP, player);
            require(menu.getCarried().isEmpty() && ItemStack.matches(menu.getSlot(0).getItem(), source)
                    && ItemStack.matches(menu.getSlot(1).getItem(), donor), "Malformed state charged Workbench payment");
        }
        var boots = ((GearItem) BuiltInRegistries.ITEM.getValue(Identifier.parse("silentgear:boots"))).construct(
                List.of(PartInstance.from(part("boot_plates", "iron"))));
        boots.set(DataComponents.EQUIPPABLE, new ItemStack(Items.IRON_LEGGINGS).get(DataComponents.EQUIPPABLE));
        require(!SilentGearCompatibility.leggings(boots), "Equipment spoof turned native boots into SG leggings");
        REPORT.append("Malformed native counters refuse both previews/payment; native type identity rejects slot spoof\n");
    }

    private static void sibling() throws Exception {
        var source = ((GearItem) BuiltInRegistries.ITEM.getValue(Identifier.parse("silentgear:boots"))).construct(
                List.of(PartInstance.from(part("boot_plates", "copper"))));
        source.set(SgDataComponents.OXIDATION_COUNTER.get(), 42);
        var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
        var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, source, new ItemStack(Items.DIAMOND_BOOTS));
        var root = (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
        require(!root.has(SilentGearCompatibility.state()), "Sibling inherited Leggings-owned state");
        var binding = Class.forName("games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearCompatibility");
        @SuppressWarnings("unchecked") var type = (net.minecraft.core.component.DataComponentType<Object>) binding.getMethod("state").invoke(null);
        require(root.has(type), "Current Boots foundation no longer initializes");
        REPORT.append("Current packaged Boots initializes its separate SG component\n");
    }

    static List<ItemStack> paid(ServerPlayer player, ItemStack first, ItemStack second) {
        var expected = pair(first, second);
        for (int slot : List.of(2, 3)) {
            player.getInventory().clearContent();
            var menu = new WorkbenchMenu(400, player.getInventory());
            menu.getSlot(0).set(first.copy());
            menu.getSlot(1).set(second.copy());
            for (int preview = 0; preview < 3; preview++) {
                menu.slotsChanged(menu.getSlot(0).container);
                require(ItemStack.matches(menu.getSlot(2).getItem(), expected.get(0)) && ItemStack.matches(menu.getSlot(3).getItem(), expected.get(1)), "Repeat preview wrote state");
            }
            menu.clicked(slot, 0, ContainerInput.PICKUP, player);
            require(ItemStack.matches(menu.getCarried(), expected.get(slot - 2)) && menu.getSlot(0).getItem().isEmpty()
                    && menu.getSlot(1).getItem().isEmpty(), "Paid extraction failed/conservation");
            menu.setCarried(ItemStack.EMPTY);
            menu.removed(player);
            require(WorkbenchChecks.inventory(player).size() == 1 && WorkbenchChecks.contains(player, expected.get(slot == 2 ? 1 : 0)), "Close lost/duplicated paid companion");
        }
        player.getInventory().clearContent();
        return expected;
    }

    private static void roundtrip(MinecraftServer server, ItemStack root) {
        var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        require(ItemStack.matches(root, ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, root).getOrThrow()).getOrThrow()), "Typed root/history/state persistence roundtrip");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buffer, root);
            require(ItemStack.matches(root, ItemStack.STREAM_CODEC.decode(buffer)) && !buffer.isReadable(), "Real root network codec");
        } finally { buffer.release(); }
    }

    private static List<ItemStack> pair(ItemStack first, ItemStack second) {
        var pair = MixedLeggingsFactory.createPair(first, second);
        return List.of(pair.primary(), pair.inverse());
    }

    static ItemStack armor(String material, String... extras) throws Exception {
        var parts = new ArrayList<PartInstance>();
        parts.add(PartInstance.from(part("legging_plates", material)));
        for (int i = 0; i < extras.length; i += 2) { parts.add(PartInstance.from(part(extras[i], extras[i + 1]))); }
        return ((GearItem) BuiltInRegistries.ITEM.getValue(Identifier.parse("silentgear:leggings"))).construct(parts);
    }

    static ItemStack part(String name, String material) throws Exception {
        var item = BuiltInRegistries.ITEM.getValue(Identifier.parse("silentgear:" + name));
        return (ItemStack) item.getClass().getMethod("create", List.class).invoke(item,
                List.of(MaterialInstance.of(DataResource.material("silentgear:" + material))));
    }

    private static double worn(ServerPlayer player, ItemStack stack, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) throws Exception {
        var update = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates");
        update.setAccessible(true);
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        update.invoke(player);
        double base = player.getAttributeValue(attribute);
        player.setItemSlot(EquipmentSlot.LEGS, stack);
        update.invoke(player);
        double bonus = player.getAttributeValue(attribute) - base;
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        update.invoke(player);
        return bonus;
    }

    private static double amount(ItemAttributeModifiers attributes, Identifier id) {
        return attributes.modifiers().stream().filter(e -> e.attribute().unwrapKey().orElseThrow().identifier().equals(id))
                .mapToDouble(e -> e.modifier().amount()).sum();
    }

    static ServerPlayer player(MinecraftServer server) throws Exception {
        var profile = new GameProfile(UUID.randomUUID(), "SgFoundation");
        var player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
        if (Boolean.getBoolean("leggings.test.sgLiveInfusion")) {
            new io.netty.channel.embedded.EmbeddedChannel(player.connection.getConnection());
        }
        var loaded = ServerGamePacketListenerImpl.class.getDeclaredMethod("markClientLoaded");
        loaded.setAccessible(true);
        loaded.invoke(player.connection);
        return player;
    }

    private static void close(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 1E-6, message + ": " + actual + " != " + expected);
    }
}
