package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Saved world data is compared before callbacks in a second game JVM, not merely codec round-tripped. */
final class SilentGearLifecycleTest {
    private static final BlockPos BARREL = new BlockPos(0, 80, 0);
    private static boolean finished;

    private SilentGearLifecycleTest() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (finished) { return; }
            finished = true;
            String phase = System.getProperty("mismatchedboots.runtimetest.silentGearLifecycle");
            try {
                if (phase.equals("save")) { save(event.getServer()); }
                else { restart(event.getServer()); }
                NetworkAcceptanceFiles.write("silent-gear-" + phase + "-success.txt", "neoforge\n");
            } catch (Throwable failure) { NetworkAcceptanceFiles.failure("silent-gear-" + phase, failure); }
            finally { event.getServer().halt(false); }
        });
    }

    private static void save(MinecraftServer server) throws Exception {
        var player = player(server);
        var api = new NeoForgeSilentGearTest.NativeApi();
        SilentGearTraitSpike.initialize(api);
        var counter = NeoForgeSilentGearTest.NativeApi.component("OXIDATION_COUNTER");
        ItemStack copper = api.boot("copper"), young = copper.copy();
        copper.set(counter, 1198); young.set(counter, 10);
        var roots = new ArrayList<>(NeoForgeSilentGearTest.workbench(player, copper, young));
        for (int index = 0; index < roots.size(); index++) {
            ItemStack root = roots.get(index);
            var history = ModContent.getMixedBootsData(root);
            tick(player, root, 20, null); tick(player, root, 40, null);
            var state = root.get(SilentGearCompatibility.state());
            var old = (index == 0 ? state.left() : state.right()).orElseThrow();
            var fresh = (index == 0 ? state.right() : state.left()).orElseThrow();
            require(old.phase().equals("oxidized") && old.level() == 1 && old.stage() == 1 && old.counter() == 0
                    && fresh.phase().equals("polished") && fresh.counter() == 12, "Save fixture missed unequal native transition");
            require(history.equals(ModContent.getMixedBootsData(root)), "Save tick changed source history");
        }
        // Explicit supplied policy differs from the server's average; no configuration-file rewrite is needed.
        var pair = MixedBootsFactory.createPair(api.boot("quartz"), new ItemStack(Items.LEATHER_BOOTS),
                SilentGearLiveStateTest.rules(AttributePolicy.MAXIMUM), ComponentRulesLifecycle.serverRules());
        for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
            root.hurtAndBreak(root.getMaxDamage() / 2, player, EquipmentSlot.FEET);
            tick(player, root, 21, EquipmentSlot.FEET);
            close(armor(root), SilentGearLiveStateTest.expectedArmor(root, api, player, AttributePolicy.MAXIMUM), "Save native worn maximum");
            roots.add(root);
        }
        ItemStack overridden = roots.get(3);
        var modifiers = new ArrayList<>(overridden.getAttributeModifiers().modifiers().stream()
                .filter(entry -> !entry.attribute().equals(Attributes.ARMOR)).toList());
        modifiers.add(new ItemAttributeModifiers.Entry(Attributes.LUCK,
                new AttributeModifier(Identifier.fromNamespaceAndPath("fixture", "persist"), .5, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.FEET));
        overridden.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(modifiers));
        overridden.set(DataComponents.CUSTOM_NAME, Component.literal("persist removal and foreign modifier"));
        tick(player, overridden, 21, null);
        require(!overridden.get(SilentGearCompatibility.state()).overrides().isEmpty() && armor(overridden) == 0,
                "Save did not record managed removal");

        for (var root : NeoForgeSilentGearTest.workbench(player, api.boot("refined_iron"), api.boot("gold"))) {
            root.setDamageValue(12); roots.add(root);
        }
        ItemStack wearerCopper = api.boot("copper"); wearerCopper.set(counter, 37);
        roots.addAll(NeoForgeSilentGearTest.workbench(player, api.boot("azure_silver"), wearerCopper));
        SilentGearWearerFixture.manualCommands(player, api);

        ItemStack iron = api.boot("iron");
        var upgrade = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("silentgear:magnetic_upgrade")));
        var input = CraftingInput.of(2, 1, List.of(iron, upgrade));
        Object recipe = api.swapClass.getField("INSTANCE").get(null);
        require((boolean) api.matches.invoke(recipe, input, player.level()), "Native Magnetic grid preparation failed");
        ItemStack magnetic = (ItemStack) api.assemble.invoke(recipe, input);
        require(api.traitLevel(magnetic, "magnetic") == 5, "Persisted native preparation lacks Magnetic V");
        roots.add(NeoForgeSilentGearTest.workbench(player, magnetic, new ItemStack(Items.DIAMOND_BOOTS)).getFirst());
        roots.addAll(SilentGearRemainingFixture.persisted(player, api));

        server.overworld().setBlock(BARREL, Blocks.BARREL.defaultBlockState(), 3);
        var barrel = barrel(server);
        var saved = new JsonArray();
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (int index = 0; index < roots.size(); index++) {
            barrel.setItem(index, roots.get(index)); saved.add(ItemStack.CODEC.encodeStart(ops, roots.get(index)).getOrThrow());
        }
        barrel.setChanged();
        var manifest = new JsonObject(); manifest.addProperty("process", ProcessHandle.current().pid()); manifest.add("roots", saved);
        NetworkAcceptanceFiles.write("silent-gear-saved.json", manifest.toString());
        NetworkAcceptanceFiles.write("silent-gear-save-report.txt", "World barrel: twelve complete paid roots; unequal transitioned copper inverses, "
                + "worn quartz inverses with maximum policy/removal/foreign metadata, damaged Stellar+Brilliant and Moonwalker+copper inverses; "
                + "one native grid-upgraded Magnetic V root; three used/damaged Void Ward, inverse Snow Walker and distinct synthetic cure roots; verified vanilla/native manual commands including cure sources\n");
    }

    private static void restart(MinecraftServer server) throws Exception {
        var manifest = JsonParser.parseString(Files.readString(Path.of(System.getProperty("mismatchedboots.runtimetest.markers"),
                "silent-gear-saved.json"))).getAsJsonObject();
        require(manifest.get("process").getAsLong() != ProcessHandle.current().pid(), "Reload reused the save JVM");
        var barrel = barrel(server);
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var saved = manifest.getAsJsonArray("roots"); require(saved.size() == 12, "Incomplete saved expectations");
        for (int index = 0; index < saved.size(); index++) {
            ItemStack expected = ItemStack.CODEC.parse(ops, saved.get(index)).getOrThrow();
            require(ItemStack.matches(expected, barrel.getItem(index)), "World reload changed root/source/projection/clock " + index);
        }
        var player = player(server);
        var api = new NeoForgeSilentGearTest.NativeApi();
        SilentGearTraitSpike.initialize(api);
        for (int index = 0; index < 4; index++) {
            ItemStack root = barrel.getItem(index);
            var history = ModContent.getMixedBootsData(root);
            if (index < 2) {
                ItemStack before = root.copy();
                for (EquipmentSlot slot : List.of(EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
                    tick(player, root, 20, slot); require(ItemStack.matches(before, root), "Reloaded selected lane progressed");
                }
                tick(player, root, 20, null);
                var state = root.get(SilentGearCompatibility.state());
                require((index == 0 ? state.left() : state.right()).orElseThrow().counter() == 1
                        && (index == 0 ? state.right() : state.left()).orElseThrow().counter() == 13, "Reload reset or advanced offline clocks");
            } else {
                root.hurtAndBreak(3, player, EquipmentSlot.FEET); tick(player, root, 21, EquipmentSlot.FEET);
                require(root.get(SilentGearCompatibility.state()).rules().stream().anyMatch(rule ->
                        rule.attribute().equals(Identifier.withDefaultNamespace("armor")) && rule.policy() == AttributePolicy.MAXIMUM),
                        "Reload discarded captured policy");
                close(armor(root), index == 3 ? 0 : SilentGearLiveStateTest.expectedArmor(root, api, player, AttributePolicy.MAXIMUM),
                        "Reload lost maximum or resurrected managed removal");
            }
            require(history.equals(ModContent.getMixedBootsData(root)), "Post-reload operations mutated source history");
            ItemStack transformed = trim(player, root.copy());
            var beforeState = root.get(SilentGearCompatibility.state()); var afterState = transformed.get(SilentGearCompatibility.state());
            require(beforeState.left().equals(afterState.left()) && beforeState.right().equals(afterState.right())
                    && beforeState.overrides().equals(afterState.overrides()), "Paid trim reset live clocks or overrides");
            if (index == 3) {
                require(armor(transformed) == 0 && transformed.getHoverName().equals(root.getHoverName())
                        && transformed.getAttributeModifiers().modifiers().stream().anyMatch(entry -> entry.attribute().equals(Attributes.LUCK)),
                        "Paid trim resurrected managed armor or lost foreign metadata");
            }
        }
        // Paid trim recomposes under the current average rules, while live root state survives.
        ItemStack original = barrel.getItem(2), trimmed = trim(player, original.copy());
        require(trimmed.get(SilentGearCompatibility.state()).rules().stream().anyMatch(rule ->
                rule.attribute().equals(Identifier.withDefaultNamespace("armor")) && rule.policy() == AttributePolicy.AVERAGE),
                "Paid recomposition did not capture current policy");
        close(armor(trimmed), SilentGearLiveStateTest.expectedArmor(trimmed, api, player, AttributePolicy.AVERAGE), "Trim worn armor");
        ItemStack repaired = repair(player, trimmed.copy());
        tick(player, repaired, 21, null);
        require(repaired.getDamageValue() < trimmed.getDamageValue(), "Paid repair did not restore durability");
        close(armor(repaired), SilentGearLiveStateTest.expectedArmor(repaired, api, player, AttributePolicy.AVERAGE), "Post-reload repaired armor");
        require(ModContent.getMixedBootsData(repaired).equals(ModContent.getMixedBootsData(trimmed)), "Repair changed source history");
        barrel.setItem(2, repaired); barrel.setChanged();
        for (int index = 4; index < 8; index++) {
            for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
            player.removeAllEffects(); ItemStack root = barrel.getItem(index), before = root.copy();
            player.setItemSlot(EquipmentSlot.FEET, root);
            tick(player, root, 21, EquipmentSlot.FEET);
            require(ItemStack.matches(before, root), "Reloaded wearer root changed before its native cadence");
            if (index < 6) {
                Object stellar = SilentGearCompatibility.effectiveTraits(root).stream().filter(value -> value.id().getPath().equals("stellar")).findFirst().orElseThrow().nativeInstance();
                SilentGearWearerFixture.seedStellar(stellar, true);
                tick(player, root, 20, EquipmentSlot.FEET);
                require(root.getDamageValue() == 12 - SilentGearTraitSpike.level(stellar), "Reloaded Stellar repair doubled or reset damage");
                before.setDamageValue(root.getDamageValue());
                require(ItemStack.matches(before, root) && player.hasEffect(net.minecraft.world.effect.MobEffects.JUMP_BOOST), "Reloaded Stellar lost potions or mutated unrelated state");
                require(net.minecraft.world.entity.monster.piglin.PiglinAi.isWearingSafeArmor(player), "Reloaded Brilliant lost native piglin recognition");
            } else {
                var state = root.get(SilentGearCompatibility.state());
                tick(player, root, 20, EquipmentSlot.FEET);
                require(ItemStack.matches(before, root), "Equipped Moonwalker advanced copper clock");
                var fall = new net.neoforged.neoforge.event.entity.living.LivingFallEvent(player, 10, 1);
                NeoForge.EVENT_BUS.post(fall);
                close(fall.getDistance(), 10 * (1 + 4 * -0.15F), "Reloaded native Moonwalker reduction");
                player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); tick(player, root, 20, null);
                var live = root.get(SilentGearCompatibility.state());
                require(live.left().equals(state.left()) && live.right().orElseThrow().counter() == 38
                                || live.right().equals(state.right()) && live.left().orElseThrow().counter() == 38,
                        "Reloaded wearer/copper root reset or lost one idle clock advance");
            }
        }
        for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
        player.removeAllEffects(); barrel.setChanged();
        require(SilentGearCompatibility.effectiveTraits(barrel.getItem(8)).stream().anyMatch(trait ->
                trait.id().toString().equals("silentgear:magnetic") && trait.level() == 5), "World restart lost prepared Magnetic V");
        SilentGearRemainingFixture.selected(player, List.of(barrel.getItem(9), barrel.getItem(10), barrel.getItem(11)));
        NetworkAcceptanceFiles.write("silent-gear-restart-report.txt", "Different JVM: twelve actual world roots compared before callbacks, including unchanged prepared Magnetic V and three used remaining-effect roots; "
                + "no offline progress or reseeding; selected pause/idle continuation, native worn maximum and persistent removal; "
                + "paid smithing/crafting repair, new average policy only on recomposition; Stellar potions + one seeded heal, Brilliant and Moonwalker resume on both inverses; "
                + "Void Ward, inverse Snow Walker and distinct cures resume unchanged; immutable sources\n");
    }

    static BarrelBlockEntity barrel(MinecraftServer server) {
        server.overworld().getChunk(0, 0);
        var result = server.overworld().getBlockEntity(BARREL);
        require(result instanceof BarrelBlockEntity, "Saved world barrel missing");
        return (BarrelBlockEntity) result;
    }

    static ItemStack trim(ServerPlayer player, ItemStack root) {
        return trim(player, root, Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE);
    }

    static ItemStack trim(ServerPlayer player, ItemStack root, Item template) {
        var menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.create(player.level(), BlockPos.ZERO));
        menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(new ItemStack(template));
        menu.getSlot(SmithingMenu.BASE_SLOT).set(root);
        menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.AMETHYST_SHARD));
        ItemStack preview = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
        menu.clicked(SmithingMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
        require(!preview.isEmpty() && ItemStack.matches(preview, menu.getCarried())
                && menu.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty()
                && menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().isEmpty()
                && menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().isEmpty(), "Paid smithing failed");
        ItemStack result = menu.getCarried(); menu.setCarried(ItemStack.EMPTY); return result;
    }

    static ItemStack repair(ServerPlayer player, ItemStack root) {
        var menu = new InventoryMenu(player.getInventory(), true, player);
        menu.getInputGridSlots().get(0).set(root); menu.getInputGridSlots().get(3).set(new ItemStack(Items.LEATHER_BOOTS));
        ItemStack preview = menu.getSlot(InventoryMenu.RESULT_SLOT).getItem().copy();
        ItemStack result = menu.getSlot(InventoryMenu.RESULT_SLOT).safeTake(1, 1, player);
        require(!result.isEmpty() && ItemStack.matches(result, preview)
                && menu.getInputGridSlots().get(0).getItem().isEmpty() && menu.getInputGridSlots().get(3).getItem().isEmpty(),
                "Paid crafting repair failed");
        return result;
    }

    static void tick(ServerPlayer player, ItemStack root, int tick, EquipmentSlot slot) {
        int prior = player.tickCount;
        try { player.tickCount = tick; root.inventoryTick(player.level(), player, slot); }
        finally { player.tickCount = prior; }
    }

    static double armor(ItemStack stack) { return stack.getAttributeModifiers().modifiers().stream()
            .filter(entry -> entry.attribute().equals(Attributes.ARMOR)).mapToDouble(entry -> entry.modifier().amount()).sum(); }
    static void close(double actual, double expected, String label) { require(Math.abs(actual - expected) < 1E-5, label + ": " + actual + " vs " + expected); }
    static void require(boolean valid, String label) { if (!valid) { throw new AssertionError(label); } }

    private static ServerPlayer player(MinecraftServer server) {
        var player = FakePlayerFactory.getMinecraft(server.overworld());
        player.setGameMode(GameType.SURVIVAL); player.setPos(0, 80, 0); player.setNoGravity(true); return player;
    }
}
