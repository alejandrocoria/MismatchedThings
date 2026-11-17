package games.alejandrocoria.mismatchedleggings.test;

import com.breakinblocks.neovitae.common.datacomponent.NVDataComponents;
import com.breakinblocks.neovitae.common.item.soul.HellforgedHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.NeoVitaeHellforgedChecks.pair;
import static games.alejandrocoria.mismatchedleggings.test.NeoVitaeHellforgedChecks.sources;
import static games.alejandrocoria.mismatchedleggings.test.NeoVitaeHellforgedChecks.total;
import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Native upgraded items, paid workstations and a real saved barrel, reused by reopen and client checks. */
final class NeoVitaeHellforgedLifecycle {
    private static final BlockPos BARREL = new BlockPos(0, 80, 0);

    private NeoVitaeHellforgedLifecycle() {
    }

    static void save(MinecraftServer server) throws Exception {
        var player = FakePlayerFactory.getMinecraft(server.overworld());
        player.setGameMode(GameType.SURVIVAL);
        player.giveExperienceLevels(1000);
        var samples = new ArrayList<Sample>();
        seed(server, player, EquipmentSlot.LEGS, samples);
        if (ModList.get().isLoaded("mismatchedboots")) {
            seed(server, player, EquipmentSlot.FEET, samples);
        }

        server.overworld().setBlock(BARREL, Blocks.BARREL.defaultBlockState(), 3);
        var barrel = (BarrelBlockEntity) server.overworld().getBlockEntity(BARREL);
        require(barrel != null && samples.size() <= barrel.getContainerSize(), "Missing/insufficient persistence barrel");
        var entries = new JsonArray();
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (int i = 0; i < samples.size(); i++) {
            var sample = samples.get(i);
            barrel.setItem(i, sample.stack());
            var entry = new JsonObject();
            entry.addProperty("label", sample.label());
            entry.addProperty("slot", sample.slot().name());
            entry.add("stack", ItemStack.CODEC.encodeStart(ops, sample.stack()).getOrThrow());
            entry.addProperty("recognized", HellforgedHelper.isHellforged(sample.stack()));
            var totals = new JsonArray();
            for (var attribute : List.of(Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.KNOCKBACK_RESISTANCE)) {
                totals.add(total(sample.stack(), attribute));
            }
            entry.add("totals", totals);
            entries.add(entry);
        }
        barrel.setChanged();
        var manifest = new JsonObject();
        manifest.addProperty("process", ProcessHandle.current().pid());
        manifest.add("samples", entries);
        Files.writeString(report("neovitae-hellforged-saved.json"), manifest.toString());
        Files.writeString(report("neovitae-hellforged-lifecycle.txt"),
                samples.size() + " complete native/inherited/direct/legacy/false/fallback items stored in world barrel\n"
                        + "Both orientations; paid trim and rename preserve resource/repair/damage/fire state; repeated recomposition keeps all bonus IDs/amounts\n");
    }

    private static void seed(MinecraftServer server, ServerPlayer player, EquipmentSlot slot, List<Sample> samples) throws Exception {
        var plain = NeoVitaeChecks.item(slot == EquipmentSlot.LEGS ? "sentient_leggings" : "sentient_boots");
        var vanilla = new ItemStack(slot == EquipmentSlot.LEGS ? Items.DIAMOND_LEGGINGS : Items.DIAMOND_BOOTS);
        var forged = plain.copy();
        HellforgedHelper.apply(forged, server.registryAccess());
        samples.add(new Sample("native", slot, forged));
        var api = new NeoVitaeChecks.NativeApi();
        int inheritedIndex = 0;
        for (var root : pair(forged, vanilla, slot)) {
            live(root, api);
            samples.add(new Sample("inherited-" + inheritedIndex++, slot, workstations(server, player, root, slot)));
        }
        var both = pair(forged, forged, slot).getFirst();
        samples.add(new Sample("both-inherited", slot, both));
        int index = 0;
        for (var root : pair(plain, vanilla, slot)) {
            live(root, api);
            HellforgedHelper.apply(root, server.registryAccess());
            samples.add(new Sample("direct-" + index++, slot, workstations(server, player, root, slot)));
        }
        var second = plain.copy();
        second.set(DataComponents.CUSTOM_NAME, Component.literal("Second Sentient input"));
        var directBoth = pair(plain, second, slot);
        for (var root : directBoth) {
            HellforgedHelper.apply(root, server.registryAccess());
        }
        add(samples, "both-direct", slot, directBoth);
        ItemStack legacy;
        try (var ignored = NeoVitaeHellforgedChecks.policy(slot, "OWNER")) {
            legacy = pair(forged, vanilla, slot).get(1);
            require(!legacy.has(NVDataComponents.HELLFORGED.get()), "Legacy inverse unexpectedly stored a marker");
        }
        samples.add(new Sample("legacy", slot, legacy));
        var explicitFalse = legacy.copy();
        explicitFalse.set(NVDataComponents.HELLFORGED.get(), false);
        samples.add(new Sample("false", slot, explicitFalse));

        // The ineligible special model forces the normal full-model fallback. The
        // eligible Sentient model still uses the real native tint producer.
        var special = vanilla.copy();
        special.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace("shield"));
        var fallback = pair(plain, special, slot);
        for (var root : fallback) {
            HellforgedHelper.apply(root, server.registryAccess());
        }
        add(samples, "fallback-direct", slot, fallback);
    }

    private static void add(List<Sample> samples, String label, EquipmentSlot slot, List<ItemStack> roots) {
        for (int i = 0; i < roots.size(); i++) {
            samples.add(new Sample(label + '-' + i, slot, roots.get(i)));
        }
    }

    private static void live(ItemStack root, NeoVitaeChecks.NativeApi api) throws Exception {
        root.set(api.capacity, 128D);
        root.set(api.blood, true);
        root.setDamageValue(19);
        root.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        require(api.fill(root, api.raw, 37.25, true) == 37.25, "Could not prepare live root Spiritus");
    }

    private static ItemStack workstations(MinecraftServer server, ServerPlayer player, ItemStack input, EquipmentSlot slot) throws Exception {
        var original = input.copy();
        var menu = new SmithingMenu(602, player.getInventory(), ContainerLevelAccess.create(server.overworld(), BlockPos.ZERO));
        menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE));
        menu.getSlot(SmithingMenu.BASE_SLOT).set(input.copy());
        menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.AMETHYST_SHARD));
        var preview = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
        menu.clicked(SmithingMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
        var trimmed = menu.getCarried().copy();
        require(!trimmed.isEmpty() && ItemStack.matches(trimmed, preview) && ItemStack.matches(original, input)
                && !menu.getSlot(SmithingMenu.BASE_SLOT).hasItem() && !menu.getSlot(SmithingMenu.TEMPLATE_SLOT).hasItem()
                && !menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).hasItem(), "Trim did not pay once or changed the original");
        menu.setCarried(ItemStack.EMPTY);
        for (var source : sources(trimmed, slot)) {
            require(source.has(DataComponents.TRIM), "Paid trim missed a source");
        }
        var anvil = AnvilRepairChecks.ready(player, trimmed.copy(), ItemStack.EMPTY);
        anvil.setItemName("Hellforged live root");
        int levels = player.experienceLevel;
        int price = anvil.getCost();
        anvil.clicked(2, 0, ContainerInput.PICKUP, player);
        var renamed = anvil.getCarried().copy();
        require(!renamed.isEmpty() && price > 0 && player.experienceLevel == levels - price
                && !anvil.getSlot(0).hasItem() && renamed.getHoverName().getString().equals("Hellforged live root"), "Rename failed to pay once");
        anvil.setCarried(ItemStack.EMPTY);
        var api = new NeoVitaeChecks.NativeApi();
        require(HellforgedHelper.isHellforged(renamed)
                && java.util.Objects.equals(input.get(DataComponents.DAMAGE_RESISTANT), renamed.get(DataComponents.DAMAGE_RESISTANT))
                && renamed.getDamageValue() == 19 && Boolean.TRUE.equals(renamed.get(api.blood))
                && Double.valueOf(128).equals(renamed.get(api.capacity)) && Double.valueOf(37.25).equals(renamed.get(api.amount))
                && api.raw.equals(renamed.get(api.type)) && renamed.hasFoil(), "Workstations changed live root state");
        var attributes = input.getAttributeModifiers();
        for (int i = 0; i < 2; i++) {
            renamed = NeoVitaeHellforgedChecks.recompose(renamed, slot);
            require(attributes.equals(renamed.getAttributeModifiers()), "Recomposition changed native bonus identities/amounts");
        }
        return renamed;
    }

    static void restart(MinecraftServer server) throws Exception {
        var manifest = manifest();
        require(manifest.get("process").getAsLong() != ProcessHandle.current().pid(), "Reopen reused seed JVM");
        var samples = load(server);
        Files.writeString(report("neovitae-hellforged-restart.txt"), samples.size()
                + " full items/history compared before native reads; different JVM, same world barrel, unchanged recognition/fire/bonus state\n");
    }

    static List<Sample> load(MinecraftServer server) throws Exception {
        server.overworld().getChunk(0, 0);
        var barrel = (BarrelBlockEntity) server.overworld().getBlockEntity(BARREL);
        require(barrel != null, "Missing saved Hellforged barrel");
        var entries = manifest().getAsJsonArray("samples");
        require(entries.size() == (ModList.get().isLoaded("mismatchedboots") ? 24 : 12), "Incomplete saved sample set");
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var result = new ArrayList<Sample>();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i).getAsJsonObject();
            var root = barrel.getItem(i).copy();
            var expected = ItemStack.CODEC.parse(ops, entry.get("stack")).getOrThrow();
            require(ItemStack.matches(expected, root), "Reopen changed complete root/history " + i);
            // Native effective reads occur only after the complete saved-data comparison.
            require(HellforgedHelper.isHellforged(root) == entry.get("recognized").getAsBoolean(), "Reopen lost recognition " + i);
            int attributeIndex = 0;
            for (var attribute : List.of(Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.KNOCKBACK_RESISTANCE)) {
                require(Math.abs(total(root, attribute) - entry.getAsJsonArray("totals").get(attributeIndex++).getAsDouble()) < 1E-6,
                        "Reopen changed native bonuses " + i);
            }
            require(ItemStack.matches(expected, root), "Native reads changed reopened root/history " + i);
            result.add(new Sample(entry.get("label").getAsString(), EquipmentSlot.valueOf(entry.get("slot").getAsString()), root));
        }
        return result;
    }

    private static JsonObject manifest() throws Exception {
        return JsonParser.parseString(Files.readString(report("neovitae-hellforged-saved.json"))).getAsJsonObject();
    }

    static Path report(String name) {
        return Path.of(System.getProperty("leggings.test.report"), name);
    }

    record Sample(String label, EquipmentSlot slot, ItemStack stack) {
    }
}
