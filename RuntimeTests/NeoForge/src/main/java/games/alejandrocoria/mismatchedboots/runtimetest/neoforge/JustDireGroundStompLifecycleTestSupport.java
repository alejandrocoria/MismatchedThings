package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveNetwork;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Disk expectations and actual native tier transactions; no alternate mixed state/action/UI implementation. */
final class JustDireGroundStompLifecycleTestSupport {
    private static final BlockPos STORAGE = new BlockPos(0, 80, 0);
    private static final String PREFIX = "just-dire-ground-stomp-lifecycle";
    private static final JsonArray saved = new JsonArray(), packets = new JsonArray();
    private static final List<String> CONTROLS = List.of("ability_bindings", "ability_cooldowns",
            "groundstomp_custom_setting", "tool_enabled", "groundstomp_upgrade_installed", "groundstomp_toggle", "groundstomp_value",
            "groundstomp_bindingmode", "stepheight_upgrade_installed", "stepheight_toggle", "stepheight_bindingmode",
            "jumpboost_upgrade_installed", "jumpboost_toggle", "jumpboost_value", "jumpboost_bindingmode",
            "negatefalldamage_upgrade_installed", "negatefalldamage_toggle", "negatefalldamage_bindingmode");

    private JustDireGroundStompLifecycleTestSupport() {}

    static BarrelBlockEntity storage(ServerPlayer player, boolean create) {
        player.level().getChunk(STORAGE);
        if (create) { player.level().setBlock(STORAGE, Blocks.BARREL.defaultBlockState(), 3); }
        var barrel = (BarrelBlockEntity) player.level().getBlockEntity(STORAGE);
        require(barrel != null, "Missing lifecycle world container");
        return barrel;
    }

    static void save(ServerPlayer player, int slot, ItemStack root,
                     JustDireActiveNetwork.Activate consumed, JustDireActiveNetwork.Activate unconsumed) {
        var barrel = storage(player, slot == 0);
        barrel.setItem(slot, root); barrel.setChanged();
        saved.add(ItemStack.CODEC.encodeStart(player.registryAccess().createSerializationContext(JsonOps.INSTANCE), root).getOrThrow());
        var pair = new JsonArray(); pair.add(packet(consumed)); pair.add(packet(unconsumed)); packets.add(pair);
        NetworkAcceptanceFiles.write(PREFIX + "-saved.json", saved.toString());
        NetworkAcceptanceFiles.write(PREFIX + "-packets.json", packets.toString());
    }

    static void verifyDisk(ServerPlayer player) throws Exception {
        var expected = JsonParser.parseString(Files.readString(marker("saved.json"))).getAsJsonArray();
        require(expected.size() == 2, "Incomplete lifecycle expectations");
        var barrel = storage(player, false);
        for (int slot = 0; slot < 2; slot++) {
            var root = barrel.getItem(slot);
            var decoded = ItemStack.CODEC.parse(player.registryAccess().createSerializationContext(JsonOps.INSTANCE), expected.get(slot)).getOrThrow();
            require(ItemStack.matches(decoded, root), "Disk-loaded stack differs in orientation " + slot);
            require(JustDireGroundStompTestSupport.timer(root) > 0, "Disk cooldown was lost");
        }
    }

    static JustDireActiveNetwork.Activate oldPacket(int slot, boolean unconsumed) throws Exception {
        var packet = JsonParser.parseString(Files.readString(marker("packets.json"))).getAsJsonArray()
                .get(slot).getAsJsonArray().get(unconsumed ? 1 : 0).getAsJsonObject();
        return new JustDireActiveNetwork.Activate(packet.get("sequence").getAsInt(), UUID.fromString(packet.get("nonce").getAsString()),
                packet.get("key").getAsInt(), packet.get("mouse").getAsBoolean());
    }

    private static JsonObject packet(JustDireActiveNetwork.Activate packet) {
        require(packet != null && !packet.nonce().equals(JustDireActiveNetwork.NONE), "No genuine permit to persist");
        var json = new JsonObject();
        json.addProperty("sequence", packet.sequence()); json.addProperty("nonce", packet.nonce().toString());
        json.addProperty("key", packet.key()); json.addProperty("mouse", packet.mouse());
        return json;
    }

    static ItemStack smithing(ServerPlayer player, ItemStack root, boolean quick) {
        var before = root.copy();
        var menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.create(player.level(), STORAGE));
        var template = nativeItem("template_eclipsealloy");
        var addition = nativeItem("eclipsealloy_ingot");
        menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(template.copy());
        menu.getSlot(SmithingMenu.BASE_SLOT).set(root.copy());
        menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(addition.copy());
        var preview = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
        require(!preview.isEmpty(), "Native tier recipe produced no mixed preview");
        require(ItemStack.matches(before, root) && ItemStack.matches(before, menu.getSlot(SmithingMenu.BASE_SLOT).getItem()),
                "Smithing preview mutated input/history");
        var originalData = ModContent.getMixedBootsData(before).orElseThrow();
        var outputData = ModContent.getMixedBootsData(preview).orElseThrow();
        boolean left = originalData.left().source().is(nativeItem("celestigem_boots").getItem());
        require((left ? outputData.left().source() : outputData.right().source()).is(nativeItem("eclipsealloy_boots").getItem())
                && ItemStack.matches(left ? originalData.right().source() : originalData.left().source(),
                left ? outputData.right().source() : outputData.left().source()), "Tier recipe transformed the wrong source");
        require(preview.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0)
                .intValue() == before.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0).intValue()
                && preview.getDamageValue() == Math.min(before.getDamageValue(), preview.getMaxDamage()),
                "Tier preview refilled root resources/wear: FE " + before.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0)
                        + " -> " + preview.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0)
                        + ", damage " + before.getDamageValue() + " -> " + preview.getDamageValue()
                        + ", new maximum " + preview.getMaxDamage());
        sameControls(before, preview, true);
        menu.clicked(SmithingMenu.RESULT_SLOT, 0, quick ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP, player);
        var taken = quick ? player.getInventory().getNonEquipmentItems().stream()
                .filter(stack -> ItemStack.matches(preview, stack)).findFirst().orElseThrow().copy() : menu.getCarried().copy();
        require(ItemStack.matches(preview, taken) && menu.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty()
                && menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().isEmpty()
                && menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().isEmpty(), "Tier take/result/one-input consumption mismatch");
        require(ItemStack.matches(before, root), "Tier take mutated the old root");
        return taken;
    }


    static void sameControls(ItemStack before, ItemStack after, boolean timer) {
        for (String name : CONTROLS) {
            if (!timer && name.equals("ability_cooldowns")) { continue; }
            var component = JustDireAttributeTestSupport.component(name);
            require(before.has(component) == after.has(component) && Objects.equals(before.get(component), after.get(component)),
                    "Live preservation changed " + name);
        }
    }
    private static ItemStack nativeItem(String name) { return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("justdirethings", name))); }
    private static Path marker(String suffix) { return Path.of(System.getProperty("mismatchedboots.runtimetest.markers"), PREFIX + "-" + suffix); }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}

