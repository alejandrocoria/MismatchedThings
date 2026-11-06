package games.alejandrocoria.mismatchedleggings.test;

import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.compatibility.forbiddenarcanus.ForbiddenModifierAdapter;
import games.alejandrocoria.mismatchedleggings.component.ComponentRuleSupport;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsRecomposer;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.ForbiddenModifiers;
import games.alejandrocoria.mismatchedleggings.test.mixin.ArmorWearInvoker;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Maintained state/effect checks; modifier smithing admission and UI belong to later blocks. */
final class ForbiddenModifierChecks {
    private static final ResourceKey<Registry<Object>> REGISTRY = ResourceKey.createRegistryKey(id("item_modifier"));

    private ForbiddenModifierChecks() {}

    static void server(MinecraftServer server) throws Exception {
        require(!ComponentRuleSupport.isManaged(LeggingsContent.forbiddenModifiersType()), "Root authority entered configurable projection");
        matrix(server);
        rootAuthority(server);
        deaths(server);
        Files.writeString(ForbiddenArcanusChecks.report("modifiers-server"),
                "Nine native input combinations, both orientations and actual workbench payment passed;\n"
                + "immutable history, exact union/projections, actual wear, root-only state and recomposition passed;\n"
                + "persistent/network codecs and real native death/PlayerList respawn passed;\n"
                + "no modifier smithing or presentation acceptance claimed\n");
    }

    private static void matrix(MinecraftServer server) throws Exception {
        for (String first : List.of("none", "eternal", "soulbound")) {
            for (String second : List.of("none", "eternal", "soulbound")) {
                var left = source(server, first, true);
                var right = source(server, second, false);
                var beforeLeft = left.copy();
                var beforeRight = right.copy();
                var expected = state(first).union(state(second));
                var pair = MixedLeggingsFactory.createPair(left, right, CompositionRules.defaults(), server.registryAccess());
                var player = actor(server);
                try {
                    var menu = new WorkbenchMenu(301, player.getInventory());
                    menu.getSlot(0).set(left.copy());
                    menu.getSlot(1).set(right.copy());
                    require(ItemStack.matches(pair.primary(), menu.getSlot(2).getItem())
                                    && ItemStack.matches(pair.inverse(), menu.getSlot(3).getItem()),
                            "Actual workbench did not supply current registry context");
                    var taken = menu.getSlot(3).safeTake(1, 1, player);
                    require(ItemStack.matches(pair.inverse(), taken) && menu.isCommitted()
                                    && !menu.getSlot(0).hasItem() && !menu.getSlot(1).hasItem(),
                            "Workbench modifier result did not pay exactly once");
                    menu.removed(player);
                    for (var root : List.of(pair.primary(), pair.inverse())) {
                        verify(root, expected);
                        var data = MixedLeggingsStructure.validData(root).orElseThrow();
                        require(ItemStack.matches(data.left().source(), root == pair.primary() ? beforeLeft : beforeRight)
                                        && ItemStack.matches(data.right().source(), root == pair.primary() ? beforeRight : beforeLeft),
                                "Union rewrote source provenance");
                        codecs(server, root);
                        var recomposed = MixedLeggingsRecomposer.recompose(root, data.left().source(), data.right().source(), server.registryAccess());
                        verify(recomposed, expected);
                        // Factory shares one repair-material union between outputs; inverse recomposition
                        // may reverse that list. Membership is meaningful here, not its existing order.
                        var materials = root.get(DataComponents.REPAIRABLE).items().stream().collect(Collectors.toSet());
                        require(materials.equals(recomposed.get(DataComponents.REPAIRABLE).items().stream()
                                .collect(Collectors.toSet())), "Recomposition changed repair materials");
                        var normalized = root.copy();
                        normalized.set(DataComponents.REPAIRABLE, recomposed.get(DataComponents.REPAIRABLE));
                        require(ItemStack.matches(normalized, recomposed), "Unchanged recomposition lost root state/effects: "
                                + first + '/' + second + " changed=" + changedComponents(root, recomposed));
                        var worn = root.copy();
                        player.setItemSlot(EquipmentSlot.LEGS, worn);
                        int beforeDamage = worn.getDamageValue();
                        ((ArmorWearInvoker) player).leggings$hurtArmor(player.damageSources().generic(), 8);
                        require(worn.getDamageValue() == (expected.eternal() ? 0 : beforeDamage + 2),
                                "Native combat wear ignored Eternal or disabled finite wear");
                        if (expected.eternal()) {
                            worn.hurtAndBreak(10000, player, EquipmentSlot.LEGS);
                            require(!worn.isEmpty() && !worn.getItem().isBarVisible(worn), "Eternal root broke or regained a durability bar");
                        }
                    }
                    require(ItemStack.matches(left, beforeLeft) && ItemStack.matches(right, beforeRight), "Composition/wear mutated inputs");
                } finally {
                    close(player);
                }
            }
        }
    }

    private static void rootAuthority(MinecraftServer server) throws Exception {
        var source = source(server, "none", true);
        source.set(LeggingsContent.forbiddenModifiersType(), new ForbiddenModifiers(true, true));
        var forged = MixedLeggingsFactory.createPair(source, source(server, "none", false), CompositionRules.defaults(), server.registryAccess());
        verify(forged.primary(), ForbiddenModifiers.EMPTY);
        verify(forged.inverse(), ForbiddenModifiers.EMPTY);

        var originals = MixedLeggingsFactory.createPair(source(server, "none", true), source(server, "none", false),
                CompositionRules.defaults(), server.registryAccess());
        var plain = originals.primary();
        var siblingBefore = originals.inverse().copy();
        var history = MixedLeggingsStructure.validData(plain).orElseThrow();
        for (var flags : List.of(new ForbiddenModifiers(true, false), new ForbiddenModifiers(false, true), new ForbiddenModifiers(true, true))) {
            // Model a later root-only edit without implementing B's recipe entry point.
            var edited = plain.copy();
            edited.set(LeggingsContent.forbiddenModifiersType(), flags);
            edited.set(DataComponents.CUSTOM_NAME, Component.literal("Live modifier target"));
            edited.set(DataComponents.REPAIR_COST, 7);
            var live = ForbiddenModifierAdapter.INSTANCE.finish(edited, server.registryAccess());
            verify(live, flags);
            require(edited.get(DataComponents.CUSTOM_NAME).equals(live.get(DataComponents.CUSTOM_NAME))
                            && live.get(DataComponents.REPAIR_COST) == 7, "Native application changed live edits");
            require(MixedLeggingsStructure.validData(live).orElseThrow().equals(history), "Root-only effect changed sources");
            var changed = history.right().source();
            changed.set(DataComponents.CUSTOM_NAME, Component.literal("Transformed historical appearance"));
            var recomposed = MixedLeggingsRecomposer.recompose(live, history.left().source(), changed, server.registryAccess());
            verify(recomposed, flags);
            require(live.get(DataComponents.CUSTOM_NAME).equals(recomposed.get(DataComponents.CUSTOM_NAME))
                            && recomposed.get(DataComponents.REPAIR_COST) == 7, "Recomposition lost live edits");
            require(ItemStack.matches(siblingBefore, originals.inverse()), "Later root edit changed its original sibling");
            codecs(server, recomposed);
        }
    }

    private static void deaths(MinecraftServer server) throws Exception {
        server.overworld().setChunkForced(0, 0, true);
        var cases = samples(server).stream().map(ForbiddenArcanusChecks.Sample::stack).collect(Collectors.toCollection(ArrayList::new));
        cases.add(source(server, "soulbound", true));
        var rootOnly = MixedLeggingsFactory.createPair(source(server, "none", true), source(server, "none", false),
                CompositionRules.defaults(), server.registryAccess()).primary();
        rootOnly.set(LeggingsContent.forbiddenModifiersType(), new ForbiddenModifiers(true, true));
        cases.add(ForbiddenModifierAdapter.INSTANCE.finish(rootOnly, server.registryAccess()));
        for (var root : cases) {
            var player = actor(server);
            player.setPos(.5, 65, .5);
            player.setItemSlot(EquipmentSlot.LEGS, root.copy());
            player.getInventory().setItem(0, new ItemStack(Items.APPLE));
            boolean retained = MixedLeggingsStructure.isMixedItem(root)
                    ? root.getOrDefault(LeggingsContent.forbiddenModifiersType(), ForbiddenModifiers.EMPTY).soulbound()
                    : nativeKind(root).equals("soulbound");
            require(player.hurtServer(server.overworld(), player.damageSources().fellOutOfWorld(), 10000)
                            && player.isDeadOrDying(), "Soulbound control did not actually die");
            var respawned = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
            require(respawned != player && !respawned.isDeadOrDying(), "Native respawn did not replace the player");
            require(retained ? ItemStack.matches(root, respawned.getItemBySlot(EquipmentSlot.LEGS))
                            : respawned.getItemBySlot(EquipmentSlot.LEGS).isEmpty(), "Native Soulbound retained the wrong root/state");
            require(!respawned.getInventory().contains(new ItemStack(Items.APPLE)), "Native death returned an unmarked control");
            server.getPlayerList().remove(respawned);
            close(respawned);
        }
    }

    static List<ForbiddenArcanusChecks.Sample> samples(MinecraftServer server) {
        var samples = new ArrayList<ForbiddenArcanusChecks.Sample>();
        for (var kinds : List.of(List.of("none", "none"), List.of("eternal", "none"),
                List.of("none", "soulbound"), List.of("eternal", "soulbound"))) {
            var pair = MixedLeggingsFactory.createPair(source(server, kinds.getFirst(), true), source(server, kinds.getLast(), false),
                    CompositionRules.defaults(), server.registryAccess());
            for (var root : List.of(pair.primary(), pair.inverse())) {
                samples.add(new ForbiddenArcanusChecks.Sample("modifiers " + kinds + (root == pair.primary() ? " primary" : " inverse"), root, 0));
            }
        }
        return samples;
    }

    private static void verify(ItemStack root, ForbiddenModifiers state) {
        require(MixedLeggingsStructure.validData(root).isPresent(), "Modifier output lost structural validity/name");
        require(root.getOrDefault(LeggingsContent.forbiddenModifiersType(), ForbiddenModifiers.EMPTY).equals(state), "Wrong authoritative union");
        require(!state.isEmpty() || !root.has(LeggingsContent.forbiddenModifiersType()), "Empty authority should be omitted");
        String projection = state.soulbound() ? "soulbound" : state.eternal() ? "eternal" : "none";
        require(nativeKind(root).equals(projection), "Native projection lost either effective flag");
        require(state.eternal() ? !root.has(DataComponents.MAX_DAMAGE) && !root.has(DataComponents.DAMAGE)
                        : root.isDamageableItem() && root.getMaxDamage() > 0, "Wrong live durability for root flags");
    }

    private static void codecs(MinecraftServer server, ItemStack root) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var loaded = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, root).getOrThrow()).getOrThrow();
        require(ItemStack.matches(root, loaded), "Persistent codec changed modifiers/provenance");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buffer, root);
            require(ItemStack.matches(root, ItemStack.STREAM_CODEC.decode(buffer)), "Network codec changed modifiers/provenance");
        } finally {
            buffer.release();
        }
    }

    private static String changedComponents(ItemStack before, ItemStack after) {
        return Stream.concat(before.getComponents().keySet().stream(), after.getComponents().keySet().stream())
                .distinct().filter(type -> !Objects.equals(before.get(type), after.get(type)))
                .map(type -> BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type) + ": " + before.get(type) + " -> " + after.get(type))
                .toList().toString();
    }

    private static ItemStack source(MinecraftServer server, String kind, boolean diamond) {
        var result = new ItemStack(diamond ? Items.DIAMOND_LEGGINGS : Items.IRON_LEGGINGS);
        result.setDamageValue(diamond ? 20 : 10);
        if (!kind.equals("none")) {
            var holder = server.registryAccess().lookupOrThrow(REGISTRY).getOrThrow(ResourceKey.create(REGISTRY, id(kind)));
            try {
                Class.forName("com.stal111.forbidden_arcanus.common.item.modifier.ModifierHelper")
                        .getMethod("setModifier", ItemStack.class, Holder.class).invoke(null, result, holder);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Could not apply native modifier control", failure);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static String nativeKind(ItemStack root) {
        var type = (DataComponentType<Holder<Object>>) BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id("modifier"));
        var holder = root.get(type);
        return holder == null ? "none" : holder.unwrapKey().orElseThrow().identifier().getPath();
    }

    private static ForbiddenModifiers state(String kind) {
        return new ForbiddenModifiers(kind.equals("eternal"), kind.equals("soulbound"));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("forbidden_arcanus", path);
    }

    private static ServerPlayer actor(MinecraftServer server) {
        var player = RepairFixtures.player(server);
        new EmbeddedChannel(player.connection.getConnection());
        player.connection.markClientLoaded();
        return player;
    }

    private static void close(ServerPlayer player) {
        ((EmbeddedChannel) player.connection.getConnection().channel()).finishAndReleaseAll();
    }
}
