package games.alejandrocoria.mismatchedleggings.test;

import com.breakinblocks.neovitae.common.datacomponent.NVDataComponents;
import com.breakinblocks.neovitae.common.item.soul.HellforgedHelper;
import com.breakinblocks.neovitae.common.tag.NVTags;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsRecomposer;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Local research only: real product factories, native callbacks and saved containers. */
public final class NeoVitaeUpdateSpike {
    static final boolean SHARE = Boolean.getBoolean("nv.spike.share");
    static final BlockPos STORAGE = new BlockPos(0, 80, 0);
    private static final List<ItemStack> stored = new ArrayList<>();
    private static final List<String> labels = new ArrayList<>();
    private static boolean finished;

    private NeoVitaeUpdateSpike() {}

    public static boolean projects(ItemStack root, TagKey<?> tag) {
        if (!tag.equals(NVTags.Items.HELLFORGEABLE)) { return false; }
        return sources(root).stream().anyMatch(source -> source.is(NVTags.Items.HELLFORGEABLE));
    }

    public static boolean inheritsHellforged(ItemStack root) {
        return sources(root).stream().anyMatch(source -> Boolean.TRUE.equals(source.get(NVDataComponents.HELLFORGED.get())));
    }

    static List<ItemStack> sources(ItemStack root) {
        var leggings = MixedLeggingsStructure.validData(root);
        if (leggings.isPresent()) {
            return List.of(leggings.get().left().source(), leggings.get().right().source());
        }
        var boots = ModContent.getMixedBootsData(root);
        return boots.isPresent() ? List.of(boots.get().left().source(), boots.get().right().source()) : List.of();
    }

    public static void tick(MinecraftServer server) {
        if (finished || !server.isDedicatedServer() || server.getTickCount() < 25) { return; }
        finished = true;
        var report = new StringBuilder();
        try {
            if (System.getProperty("nv.spike.stage").equals("reopen")) {
                reopen(server, report);
            } else {
                var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(server.overworld());
                player.setPos(8.5, 81, 8.5);
                for (int x = -2; x <= 12; x++) {
                    for (int z = -2; z <= 12; z++) { server.overworld().setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 3); }
                }
                new NeoVitaeUpdateSentient(server, player, report).run();
                var rituals = new NeoVitaeUpdateRituals(server, player, report);
                rituals.deus();
                for (var slot : List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS)) { materials(server, player, slot, rituals, report); }
                if (SHARE) { legacy(server, player, rituals, report); }
                save(server, report);
                clear(player);
            }
            Files.writeString(report("results.txt"), report);
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            try { Files.writeString(report("partial-results.txt"), report); }
            catch (java.io.IOException writing) { failure.addSuppressed(writing); }
            SkeletonChecks.failed("server", failure);
        } finally { server.halt(false); }
    }

    private static void materials(MinecraftServer server, ServerPlayer player, EquipmentSlot slot,
                                  NeoVitaeUpdateRituals rituals, StringBuilder report) throws Exception {
        var original = nativePiece(slot);
        var forged = original.copy();
        HellforgedHelper.apply(forged, server.registryAccess());
        require(HellforgedHelper.isHellforged(forged) && forged.has(DataComponents.DAMAGE_RESISTANT), "Native Hellforged control failed");
        close(total(forged, Attributes.ARMOR) - total(original, Attributes.ARMOR), 1, "Native Hellforged armor positive");
        close(total(forged, Attributes.ARMOR_TOUGHNESS) - total(original, Attributes.ARMOR_TOUGHNESS), 3, "Native Hellforged toughness positive");
        close(total(forged, Attributes.KNOCKBACK_RESISTANCE) - total(original, Attributes.KNOCKBACK_RESISTANCE), .1, "Native Hellforged knockback positive");
        remember(slot + "-native", forged);
        var vanilla = new ItemStack(slot == EquipmentSlot.FEET ? Items.DIAMOND_BOOTS : Items.DIAMOND_LEGGINGS);
        for (int count = 0; count <= 2; count++) {
            var first = count > 0 ? forged : original;
            var second = count == 2 ? forged.copy() : vanilla;
            if (count == 2) { second.set(DataComponents.CUSTOM_NAME, Component.literal("Second native Hellforged control")); }
            var roots = paid(player, first, second, slot);
            for (int index = 0; index < 2; index++) {
                var root = roots.get(index);
                for (var attribute : List.of(Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.KNOCKBACK_RESISTANCE)) {
                    close(total(root, attribute), (total(first, attribute) + total(second, attribute)) / 2, "Native effective composition " + slot);
                }
                var entries = root.getAttributeModifiers().modifiers();
                require(entries.stream().map(entry -> entry.attribute().unwrapKey().orElseThrow().identifier() + "/" + entry.modifier().id())
                        .distinct().count() == entries.size(), "Duplicate effective modifier identity");
                boolean marked = count == 2 || count == 1 && (SHARE || index == 0);
                require(HellforgedHelper.isHellforged(root) == marked, "Unexpected marker inheritance");
                require(root.is(NVTags.Items.HELLFORGEABLE) == SHARE, "Unexpected research tag projection");
                require(root.has(DataComponents.DAMAGE_RESISTANT) == (count == 2), "Fire intersection changed");
                remember(slot + "-" + count + "-" + index, root);
            }
        }
        report.append(slot).append(": paid none/one/both Hellforged inputs; two orientations; actual averaged modifiers, unique native IDs; marker=")
                .append(SHARE ? "shared" : "owner").append("; fire intersection unchanged\n");

        var plain = paid(player, original, vanilla, slot);
        for (int i = 0; i < plain.size(); i++) {
            var before = plain.get(i);
            var history = sources(before);
            var api = new NeoVitaeChecks.NativeApi();
            before.set(api.capacity, 128D);
            before.set(NVDataComponents.BLOOD_MENDING.get(), true);
            before.setDamageValue(19);
            require(api.fill(before, api.raw, 37.25, true) == 37.25, "Native resource fixture fill failed");
            var upgraded = rituals.hellforge(before, SHARE);
            var after = sources(upgraded);
            require(ItemStack.matches(after.get(0), history.get(0)) && ItemStack.matches(after.get(1), history.get(1)),
                    "Native Hellforging changed immutable sources");
            require(Double.valueOf(37.25).equals(upgraded.get(api.amount)), "Ritual lost root charge");
            require(Boolean.TRUE.equals(upgraded.get(NVDataComponents.BLOOD_MENDING.get())) && upgraded.getDamageValue() == 19,
                    "Ritual changed root Blood Mending/damage state");
            if (SHARE) {
                require(HellforgedHelper.isHellforged(upgraded) && upgraded.has(DataComponents.DAMAGE_RESISTANT), "Native root upgrade missing");
                close(total(upgraded, Attributes.ARMOR) - total(before, Attributes.ARMOR), 1, "Native direct-root armor bonus");
                var trimmed = smithing(player, upgraded);
                var menu = AnvilRepairChecks.ready(player, trimmed.copy(), ItemStack.EMPTY);
                menu.setItemName("Hellforged research root");
                int levels = player.experienceLevel;
                int price = menu.getCost();
                menu.clicked(2, 0, ContainerInput.PICKUP, player);
                var renamed = menu.getCarried().copy();
                menu.setCarried(ItemStack.EMPTY);
                require(!renamed.isEmpty() && player.experienceLevel == levels - price, "Native anvil take/payment failed");
                require(HellforgedHelper.isHellforged(renamed) && renamed.has(DataComponents.DAMAGE_RESISTANT)
                        && Double.valueOf(37.25).equals(renamed.get(api.amount)), "Paid recomposition lost direct root upgrade/resource state");
                close(total(renamed, Attributes.ARMOR), total(upgraded, Attributes.ARMOR), "Direct-root effective armor after recomposition");
                remember(slot + "-direct-" + i, renamed);
            }
        }
    }

    private static void legacy(MinecraftServer server, ServerPlayer player, NeoVitaeUpdateRituals rituals, StringBuilder report) throws Exception {
        var entries = JsonParser.parseString(Files.readString(Path.of(System.getProperty("nv.spike.legacy")))).getAsJsonArray();
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (var entry : entries) {
            var object = entry.getAsJsonObject();
            String label = object.get("label").getAsString();
            if (!label.endsWith("-1-1")) { continue; }
            var root = ItemStack.CODEC.parse(ops, object.get("stack")).getOrThrow();
            require(!root.has(NVDataComponents.HELLFORGED.get()), "Legacy control is not the actual owner-negative inverse");
            var history = sources(root);
            var refreshed = MixedLeggingsStructure.isMixedItem(root)
                    ? MixedLeggingsRecomposer.recompose(root, history.get(0), history.get(1), server.registryAccess())
                    : MixedBootsRecomposer.recompose(root, history.get(0), history.get(1), server.registryAccess());
            report.append("Legacy ").append(label).append(": stored marker absent; native recognition=").append(HellforgedHelper.isHellforged(root))
                    .append("; after ordinary recomposition=").append(HellforgedHelper.isHellforged(refreshed))
                    .append("; existing contributions retained\n");
            close(total(root, Attributes.ARMOR), total(refreshed, Attributes.ARMOR), "Old root effective contribution");
            if (!HellforgedHelper.isHellforged(refreshed)) {
                var forgedAgain = rituals.hellforge(refreshed, true);
                close(total(forgedAgain, Attributes.ARMOR), total(refreshed, Attributes.ARMOR), "Legacy repeated forge armor contribution");
                report.append("Legacy ").append(label)
                        .append(": native ritual accepted/charged after recomposition, but existing averaged armor modifier ID blocked any additional armor bonus\n");
            } else {
                require(!refreshed.has(NVDataComponents.HELLFORGED.get()), "Legacy recognition unexpectedly rewrote its marker");
                var explicitlyRemoved = refreshed.copy();
                explicitlyRemoved.set(NVDataComponents.HELLFORGED.get(), false);
                require(!HellforgedHelper.isHellforged(explicitlyRemoved), "Legacy reader overrode an explicit false marker");
                require(HellforgedHelper.readiness(refreshed) == HellforgedHelper.Readiness.ALREADY_HELLFORGED,
                        "Legacy reader did not reach native ritual refusal");
                rituals.hellforge(refreshed, false);
                report.append("Legacy ").append(label)
                        .append(": reader-only recognition; native repeated ritual refused with no operation charge/reagent loss; stored marker still absent\n");
            }
            remember(label + "-legacy", refreshed);
        }
    }

    private static ItemStack smithing(ServerPlayer player, ItemStack root) {
        var menu = new SmithingMenu(601, player.getInventory(), ContainerLevelAccess.create(player.level(), STORAGE));
        menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE));
        menu.getSlot(SmithingMenu.BASE_SLOT).set(root.copy());
        menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.AMETHYST_SHARD));
        var preview = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
        menu.clicked(SmithingMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
        require(!preview.isEmpty() && ItemStack.matches(preview, menu.getCarried())
                && !menu.getSlot(SmithingMenu.BASE_SLOT).hasItem() && !menu.getSlot(SmithingMenu.TEMPLATE_SLOT).hasItem()
                && !menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).hasItem(), "Native trim take/payment failed");
        var result = menu.getCarried();
        menu.setCarried(ItemStack.EMPTY);
        return result;
    }

    static ItemStack nativePiece(EquipmentSlot slot) {
        return item(slot == EquipmentSlot.FEET ? "sentient_boots" : "sentient_leggings");
    }

    static ItemStack item(String path) {
        var item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("neovitae", path));
        require(item != null && item != Items.AIR, "Missing actual native item: " + path);
        return new ItemStack(item);
    }

    static List<ItemStack> pair(ItemStack first, ItemStack second, EquipmentSlot slot) {
        if (slot == EquipmentSlot.FEET) {
            var pair = MixedBootsFactory.createPair(first, second);
            return List.of(pair.primary(), pair.inverse());
        }
        var pair = MixedLeggingsFactory.createPair(first, second);
        return List.of(pair.primary(), pair.inverse());
    }

    static List<ItemStack> paid(ServerPlayer player, ItemStack first, ItemStack second, EquipmentSlot slot) {
        var expected = pair(first, second, slot);
        var result = new ArrayList<ItemStack>();
        for (int index = 0; index < 2; index++) {
            clear(player);
            var menu = new WorkbenchMenu(600, player.getInventory());
            menu.getSlot(0).set(first.copy());
            menu.getSlot(1).set(second.copy());
            menu.slotsChanged(menu.getSlot(0).container);
            menu.clicked(index + 2, 0, ContainerInput.PICKUP, player);
            require(ItemStack.matches(menu.getCarried(), expected.get(index)) && !menu.getSlot(0).hasItem()
                    && !menu.getSlot(1).hasItem(), "Native workbench paid extraction failed");
            result.add(menu.getCarried().copy());
            menu.setCarried(ItemStack.EMPTY);
            menu.removed(player);
            require(WorkbenchChecks.inventory(player).size() == 1 && WorkbenchChecks.contains(player, expected.get(1 - index)),
                    "Paid companion return failed");
        }
        clear(player);
        player.giveExperienceLevels(100);
        return result;
    }

    static void clear(ServerPlayer player) {
        player.getInventory().clearContent();
        for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
        player.removeAllEffects();
    }

    static double total(ItemStack stack, Holder<Attribute> attribute) {
        return stack.getAttributeModifiers().modifiers().stream().filter(entry -> entry.attribute().equals(attribute))
                .mapToDouble(entry -> entry.modifier().amount()).sum();
    }

    static void close(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 1E-6, message + ": " + actual + " != " + expected);
    }

    static Path report(String file) { return Path.of(System.getProperty("leggings.test.report"), file); }

    private static void remember(String label, ItemStack root) {
        stored.add(root.copy());
        labels.add(label);
    }

    private static void save(MinecraftServer server, StringBuilder report) throws Exception {
        var level = server.overworld();
        level.setChunkForced(0, 0, true);
        level.setBlockAndUpdate(STORAGE, Blocks.BARREL.defaultBlockState());
        var barrel = (BarrelBlockEntity) level.getBlockEntity(STORAGE);
        require(barrel != null && stored.size() <= barrel.getContainerSize(), "Research barrel missing/too small");
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var json = new JsonArray();
        for (int i = 0; i < stored.size(); i++) {
            barrel.setItem(i, stored.get(i));
            var entry = new JsonObject();
            entry.addProperty("label", labels.get(i));
            entry.add("stack", ItemStack.CODEC.encodeStart(ops, stored.get(i)).getOrThrow());
            json.add(entry);
        }
        barrel.setChanged();
        Files.writeString(Path.of(System.getProperty("nv.spike.expected")), json.toString());
        report.append("Saved native barrel: ").append(stored.size()).append(" actual controls/paid roots for independent JVM reopen\n");
    }

    private static void reopen(MinecraftServer server, StringBuilder report) throws Exception {
        var level = server.overworld();
        level.getChunk(0, 0);
        var barrel = (BarrelBlockEntity) level.getBlockEntity(STORAGE);
        require(barrel != null, "Actual saved barrel did not load");
        var json = JsonParser.parseString(Files.readString(Path.of(System.getProperty("nv.spike.expected")))).getAsJsonArray();
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (int i = 0; i < json.size(); i++) {
            var loaded = barrel.getItem(i);
            require(ItemStack.CODEC.encodeStart(ops, loaded).getOrThrow().equals(json.get(i).getAsJsonObject().get("stack")),
                    "Disk load changed complete native/mixed state at " + i);
            var before = loaded.copy();
            loaded.getAttributeModifiers();
            HellforgedHelper.readiness(loaded);
            require(ItemStack.matches(before, loaded), "Restored queries mutated state");
        }
        report.append("Independent game JVM: ").append(json.size()).append(" actual native barrel loads matched complete encoded expectations; no reconstruction\n");
    }
}
