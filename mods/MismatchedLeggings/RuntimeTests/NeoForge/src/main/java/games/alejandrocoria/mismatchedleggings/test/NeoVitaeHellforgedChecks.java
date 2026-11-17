package games.alejandrocoria.mismatchedleggings.test;

import com.breakinblocks.neovitae.common.datacomponent.NVDataComponents;
import com.breakinblocks.neovitae.common.item.soul.HellforgedHelper;
import com.breakinblocks.neovitae.common.tag.NVTags;
import games.alejandrocoria.mismatchedleggings.component.ComponentRuleSet;
import games.alejandrocoria.mismatchedleggings.component.ComponentRules;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsRecomposer;
import games.alejandrocoria.mismatchedleggings.config.CompositionRuleState;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedthings.api.compatibility.neovitae.NeoVitaeHellforgedService;
import games.alejandrocoria.mismatchedthings.api.composition.ComponentPolicy;
import games.alejandrocoria.mismatchedthings.api.composition.ComponentRule;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Packaged production gameplay only. Presentation/reopen and the native chestplate suite are separate gates. */
final class NeoVitaeHellforgedChecks {
    private static final Identifier MARKER = Identifier.parse("neovitae:hellforged");
    private static int preparedAt = -1;
    private static net.minecraft.world.entity.item.ItemEntity witness;

    private NeoVitaeHellforgedChecks() {
    }

    static boolean ready(MinecraftServer server) {
        if (preparedAt < 0) {
            server.overworld().setChunkForced(3, 0, true);
            server.overworld().getChunk(3, 0);
            var player = FakePlayerFactory.getMinecraft(server.overworld());
            player.setPos(48.5, 81, .5);
            server.overworld().addNewPlayer(player);
            witness = new net.minecraft.world.entity.item.ItemEntity(server.overworld(), 48.5, 81.25, .5, new ItemStack(Items.STONE));
            require(server.overworld().addFreshEntity(witness), "Could not add entity-chunk readiness witness");
            preparedAt = server.getTickCount();
        }
        require(server.getTickCount() < preparedAt + 200, "Ritual entity chunk never became visible");
        if (!server.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(48, 80, 0, 51, 83, 2)).contains(witness)) {
            return false;
        }
        witness.discard();
        return true;
    }

    static void run(MinecraftServer server) throws Exception {
        var player = FakePlayerFactory.getMinecraft(server.overworld());
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(48.5, 81, .5);
        var report = new StringBuilder();
        var rituals = new NeoVitaeHellforgingRituals(server, player, report);
        verify(server, player, EquipmentSlot.LEGS, rituals, report);
        if (ModList.get().isLoaded("mismatchedboots")) {
            verify(server, player, EquipmentSlot.FEET, rituals, report);
        }
        bindingRefusal(server);
        report.append("Binding refusal: mixed tag/legacy fallback decline; native items and separate Spiritus binding intact\n");
        Files.writeString(Path.of(System.getProperty("leggings.test.report"), "neovitae-hellforged-server.txt"), report);
    }

    private static void verify(MinecraftServer server, ServerPlayer player, EquipmentSlot slot,
                               NeoVitaeHellforgingRituals rituals, StringBuilder report) throws Exception {
        var nativeItem = NeoVitaeChecks.item(slot == EquipmentSlot.FEET ? "sentient_boots" : "sentient_leggings");
        var vanilla = new ItemStack(slot == EquipmentSlot.FEET ? Items.DIAMOND_BOOTS : Items.DIAMOND_LEGGINGS);
        var forged = rituals.hellforge(nativeItem, true);
        close(total(forged, Attributes.ARMOR) - total(nativeItem, Attributes.ARMOR), 1, "Native armor positive");
        close(total(forged, Attributes.ARMOR_TOUGHNESS) - total(nativeItem, Attributes.ARMOR_TOUGHNESS), 3, "Native toughness positive");
        close(total(forged, Attributes.KNOCKBACK_RESISTANCE) - total(nativeItem, Attributes.KNOCKBACK_RESISTANCE), .1, "Native knockback positive");
        for (int count = 0; count <= 2; count++) {
            var first = count > 0 ? forged : nativeItem;
            var second = count == 2 ? forged.copy() : vanilla;
            if (count == 2) {
                second.set(DataComponents.CUSTOM_NAME, Component.literal("Distinct second native input"));
            }
            for (var root : paid(player, first, second)) {
                require(root.is(NVTags.Items.HELLFORGEABLE) && HellforgedHelper.isHellforged(root) == (count > 0), "Wrong shipped tag/marker policy");
                require(root.has(DataComponents.DAMAGE_RESISTANT) == (count == 2), "Creation fire intersection changed");
                for (var attribute : List.of(Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.KNOCKBACK_RESISTANCE)) {
                    close(total(root, attribute), (total(first, attribute) + total(second, attribute)) / 2, "Effective averaged bonus");
                }
                var raw = root.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers();
                require(raw.size() == root.getAttributeModifiers().modifiers().size(), "Native callback duplicated saved modifiers");
                require(raw.stream().map(entry -> entry.attribute().unwrapKey().orElseThrow().identifier() + "/" + entry.modifier().id())
                        .distinct().count() == raw.size(), "Duplicate native modifier identity");
            }
        }
        for (var root : paid(player, nativeItem, vanilla)) {
            var history = sources(root, slot);
            var api = new NeoVitaeChecks.NativeApi();
            root.set(api.capacity, 128D);
            root.set(api.blood, true);
            root.setDamageValue(19);
            require(api.fill(root, api.raw, 37.25, true) == 37.25, "Native Spiritus fill failed");
            var result = rituals.hellforge(root, true);
            close(total(result, Attributes.ARMOR) - total(root, Attributes.ARMOR), 1, "Full direct-root bonus");
            var refreshed = recompose(result, slot);
            require(HellforgedHelper.isHellforged(refreshed) && refreshed.has(DataComponents.DAMAGE_RESISTANT)
                    && refreshed.getDamageValue() == 19 && Boolean.TRUE.equals(refreshed.get(api.blood))
                    && Double.valueOf(37.25).equals(refreshed.get(api.amount)), "Recomposition lost direct live state");
            close(total(refreshed, Attributes.ARMOR), total(result, Attributes.ARMOR), "Recomposition bonus inflation");
            histories(history, sources(refreshed, slot));
        }
        ItemStack legacy;
        try (var ignored = policy(slot, "OWNER")) {
            legacy = pair(forged, vanilla, slot).get(1);
            require(!legacy.has(NVDataComponents.HELLFORGED.get()) && !HellforgedHelper.isHellforged(legacy)
                    && !legacy.is(NVTags.Items.HELLFORGEABLE), "OWNER policy was overridden");
            rituals.hellforge(legacy, false);
        }
        var before = legacy.copy();
        require(HellforgedHelper.isHellforged(legacy) && HellforgedHelper.readiness(legacy) == HellforgedHelper.Readiness.ALREADY_HELLFORGED,
                "Old inverse was not recognized before recomposition");
        rituals.hellforge(legacy, false);
        var refreshed = recompose(legacy, slot);
        require(!refreshed.has(NVDataComponents.HELLFORGED.get()) && HellforgedHelper.isHellforged(refreshed), "Legacy reader rewrote/lost marker");
        close(total(legacy, Attributes.ARMOR), total(refreshed, Attributes.ARMOR), "Legacy bonus changed");
        require(ItemStack.matches(before, legacy), "Native recognition changed old data");
        var explicitFalse = legacy.copy();
        explicitFalse.set(NVDataComponents.HELLFORGED.get(), false);
        require(!HellforgedHelper.isHellforged(explicitFalse) && !explicitFalse.is(NVTags.Items.HELLFORGEABLE), "Explicit false was overridden");
        rituals.hellforge(explicitFalse, false);
        try (var ignored = policy(slot, "OMIT")) {
            require(!HellforgedHelper.isHellforged(legacy) && !legacy.is(NVTags.Items.HELLFORGEABLE), "OMIT was overridden");
        }
        var invalid = legacy.copy();
        invalid.setCount(2);
        require(!HellforgedHelper.isHellforged(invalid) && !invalid.is(NVTags.Items.HELLFORGEABLE), "Malformed root inherited an upgrade");
        var bare = new ItemStack(legacy.getItem());
        require(!HellforgedHelper.isHellforged(bare) && !bare.is(NVTags.Items.HELLFORGEABLE), "Bare root inherited an upgrade");
        clientRules(slot, legacy);
        require(HellforgedHelper.isHellforged(legacy), "Client preview replaced server authority");
        report.append(slot).append(": paid neither/one/both inputs in both orders; native averaged IDs/fire intersection; full direct upgrade/live state; old OWNER inverse; explicit false/OMIT/invalid refusal; logical client/server isolation\n");
    }

    private static void clientRules(EquipmentSlot slot, ItemStack legacy) throws Exception {
        var failure = new AtomicReference<Throwable>();
        var thread = new Thread(new ThreadGroup("neovitae-client-rule-check"), () -> {
            try {
                preview(slot, null);
                require(!HellforgedHelper.isHellforged(legacy), "Unsynchronized client read server defaults");
                preview(slot, "OWNER");
                require(!HellforgedHelper.isHellforged(legacy) && !legacy.is(NVTags.Items.HELLFORGEABLE), "Client ignored synchronized OWNER");
                preview(slot, "SHARE");
                require(HellforgedHelper.isHellforged(legacy), "Client ignored synchronized SHARE");
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                preview(slot, null);
            }
        }, "native-hellforged-client-context");
        thread.start();
        thread.join(5000);
        require(!thread.isAlive() && failure.get() == null, "Logical client rule test failed: " + failure.get());
    }

    private static void bindingRefusal(MinecraftServer server) throws Exception {
        var root = pair(NeoVitaeChecks.item("sentient_leggings"), new ItemStack(Items.DIAMOND_LEGGINGS), EquipmentSlot.LEGS).getFirst();
        var service = NeoVitaeHellforgedService.INSTANCE;
        var field = service.getClass().getDeclaredField("marker");
        field.setAccessible(true);
        var binding = field.get(service);
        require(binding != null, "Production Hellforged binding never activated");
        try {
            field.set(service, null);
            require(!root.is(NVTags.Items.HELLFORGEABLE), "Refused binding still admits a ritual");
            var nativeItem = NeoVitaeChecks.item("sentient_leggings");
            HellforgedHelper.apply(nativeItem, server.registryAccess());
            require(nativeItem.is(NVTags.Items.HELLFORGEABLE) && HellforgedHelper.isHellforged(nativeItem), "Refused binding changed native items");
            var api = new NeoVitaeChecks.NativeApi();
            root.set(api.capacity, 64D);
            require(api.fill(root, api.raw, 7, true) == 7, "Hellforged refusal disabled infusion");
            require(root.is(NVTags.Items.SENTIENT_SET), "Refused binding disabled existing set tags");
        } finally {
            field.set(service, binding);
        }
    }

    private static List<ItemStack> paid(ServerPlayer player, ItemStack first, ItemStack second) {
        var result = new ArrayList<ItemStack>();
        for (int index = 0; index < 2; index++) {
            player.getInventory().clearContent();
            var menu = new WorkbenchMenu(601, player.getInventory());
            menu.getSlot(0).set(first.copy());
            menu.getSlot(1).set(second.copy());
            menu.slotsChanged(menu.getSlot(0).container);
            menu.clicked(index + 2, 0, ContainerInput.PICKUP, player);
            require(!menu.getCarried().isEmpty() && !menu.getSlot(0).hasItem() && !menu.getSlot(1).hasItem(), "Workbench did not consume paid inputs");
            result.add(menu.getCarried().copy());
            menu.setCarried(ItemStack.EMPTY);
            var companion = menu.getSlot(3 - index).getItem().copy();
            menu.removed(player);
            require(WorkbenchChecks.inventory(player).size() == 1 && WorkbenchChecks.contains(player, companion), "Workbench companion return changed");
        }
        return result;
    }

    private static List<ItemStack> pair(ItemStack left, ItemStack right, EquipmentSlot slot) {
        if (slot == EquipmentSlot.FEET) {
            return Boots.pair(left, right);
        }
        var pair = MixedLeggingsFactory.createPair(left, right);
        return List.of(pair.primary(), pair.inverse());
    }

    private static List<ItemStack> sources(ItemStack root, EquipmentSlot slot) {
        if (slot == EquipmentSlot.FEET) {
            return Boots.sources(root);
        }
        var data = MixedLeggingsStructure.validData(root).orElseThrow();
        return List.of(data.left().source(), data.right().source());
    }

    private static ItemStack recompose(ItemStack root, EquipmentSlot slot) {
        var sources = sources(root, slot);
        return slot == EquipmentSlot.FEET ? Boots.recompose(root, sources)
                : MixedLeggingsRecomposer.recompose(root, sources.getFirst(), sources.get(1));
    }

    private static ComponentRules components(ComponentRules previous, String policy) {
        var declarations = new LinkedHashMap<>(previous.declarations());
        var effective = new LinkedHashMap<>(previous.components().rules());
        var rule = ComponentRule.of(ComponentPolicy.valueOf(policy));
        declarations.put(MARKER, rule);
        effective.put(MARKER, rule);
        return new ComponentRules(previous.durability(), previous.repairCost(), declarations, new ComponentRuleSet(effective));
    }

    private static AutoCloseable policy(EquipmentSlot slot, String policy) throws Exception {
        if (slot == EquipmentSlot.FEET) {
            return Boots.policy(policy);
        }
        var old = CompositionRuleState.server();
        CompositionRuleState.installServer(new CompositionRules(old.attributes(), components(old.components(), policy)));
        return () -> CompositionRuleState.installServer(old);
    }

    private static void preview(EquipmentSlot slot, String policy) {
        if (slot == EquipmentSlot.FEET) {
            Boots.preview(policy);
        } else if (policy == null) {
            CompositionRuleState.resetPreview();
        } else {
            var old = CompositionRuleState.server();
            CompositionRuleState.installPreview(new CompositionRules(old.attributes(), components(old.components(), policy)));
        }
    }

    private static void histories(List<ItemStack> expected, List<ItemStack> actual) {
        require(ItemStack.matches(expected.getFirst(), actual.getFirst()) && ItemStack.matches(expected.get(1), actual.get(1)), "Source history mutated");
    }

    private static double total(ItemStack root, Holder<Attribute> attribute) {
        return root.getAttributeModifiers().modifiers().stream().filter(entry -> entry.attribute().equals(attribute))
                .mapToDouble(entry -> entry.modifier().amount()).sum();
    }

    private static void close(double actual, double expected, String label) {
        require(Math.abs(actual - expected) < 1E-6, label + ": " + actual + " != " + expected);
    }

    /** Loaded only in the explicit current-Boots graph; standalone Leggings never links Boots types. */
    private static final class Boots {
        private static List<ItemStack> pair(ItemStack left, ItemStack right) {
            var pair = games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory.createPair(left, right);
            return List.of(pair.primary(), pair.inverse());
        }

        private static List<ItemStack> sources(ItemStack root) {
            var data = games.alejandrocoria.mismatchedboots.content.ModContent.getMixedBootsData(root).orElseThrow();
            return List.of(data.left().source(), data.right().source());
        }

        private static ItemStack recompose(ItemStack root, List<ItemStack> sources) {
            return games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer.recompose(root, sources.getFirst(), sources.get(1));
        }

        private static games.alejandrocoria.mismatchedboots.component.ComponentRules components(
                games.alejandrocoria.mismatchedboots.component.ComponentRules previous, String policy) {
            var declarations = new LinkedHashMap<>(previous.declarations());
            var effective = new LinkedHashMap<>(previous.components().rules());
            var rule = games.alejandrocoria.mismatchedboots.component.ComponentRule.of(
                    games.alejandrocoria.mismatchedboots.component.ComponentPolicy.valueOf(policy));
            declarations.put(MARKER, rule);
            effective.put(MARKER, rule);
            return new games.alejandrocoria.mismatchedboots.component.ComponentRules(previous.durability(), previous.repairCost(),
                    declarations, new games.alejandrocoria.mismatchedboots.component.ComponentRuleSet(effective));
        }

        @SuppressWarnings("unchecked")
        private static AutoCloseable policy(String policy) throws Exception {
            var field = games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle.class.getDeclaredField("SERVER_RULES");
            field.setAccessible(true);
            var state = (AtomicReference<games.alejandrocoria.mismatchedboots.component.ComponentRules>) field.get(null);
            var old = state.get();
            state.set(components(old, policy));
            return () -> state.set(old);
        }

        private static void preview(String policy) {
            if (policy == null) {
                games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle.onDisconnected();
            } else {
                games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle.install(
                        new games.alejandrocoria.mismatchedboots.network.CompositionRules(
                                games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle.serverRules(),
                                components(games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle.serverRules(), policy)));
            }
        }
    }
}
