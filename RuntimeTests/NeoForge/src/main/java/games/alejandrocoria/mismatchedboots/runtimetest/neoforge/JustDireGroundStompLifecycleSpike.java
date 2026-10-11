package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
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

/** World/container and native-recipe proof. The preservation seam is never packaged for production. */
public final class JustDireGroundStompLifecycleSpike {
    private static final BlockPos STORAGE = new BlockPos(0, 80, 0);
    private static final List<String> ACTIVE = List.of("ability_bindings", "groundstomp_bindingmode",
            "groundstomp_custom_setting", "ability_cooldowns");
    static final String MODE = System.getProperty("mismatchedboots.runtimetest.justDireGroundStompLifecycle", "");
    private static boolean preserve;
    private static final JsonArray saved = new JsonArray();
    private static final String PREFIX = "just-dire-ground-stomp-lifecycle";

    private JustDireGroundStompLifecycleSpike() {}

    public static void preserve(ItemStack previous, ItemStack output) {
        if (preserve && ModContent.getMixedBootsData(previous).isPresent()) {
            NeoForgeJustDireGroundStompSpike.preserveActiveSettings(previous, output);
        }
    }

    static BarrelBlockEntity storage(ServerPlayer player, boolean create) {
        player.level().getChunk(STORAGE);
        if (create) { player.level().setBlock(STORAGE, Blocks.BARREL.defaultBlockState(), 3); }
        var barrel = (BarrelBlockEntity) player.level().getBlockEntity(STORAGE);
        require(barrel != null, "Missing lifecycle world container");
        return barrel;
    }

    static void save(ServerPlayer player, int slot, ItemStack root, JustDireGroundStompSpikeNetwork.Activate packet) {
        var barrel = storage(player, slot == 0);
        barrel.setItem(slot, root); barrel.setChanged();
        saved.add(ItemStack.CODEC.encodeStart(player.registryAccess().createSerializationContext(JsonOps.INSTANCE), root).getOrThrow());
        NetworkAcceptanceFiles.write(PREFIX + "-saved.json", saved.toString());
        NetworkAcceptanceFiles.write(PREFIX + "-packet-" + slot + ".txt", packet.sequence() + "\n" + packet.nonce());
    }

    static void verifyDisk(ServerPlayer player) throws Exception {
        var expected = JsonParser.parseString(Files.readString(marker("saved.json"))).getAsJsonArray();
        require(expected.size() == 2, "Incomplete lifecycle expectations");
        var barrel = storage(player, false);
        for (int slot = 0; slot < 2; slot++) {
            var root = barrel.getItem(slot);
            var decoded = ItemStack.CODEC.parse(player.registryAccess().createSerializationContext(JsonOps.INSTANCE), expected.get(slot)).getOrThrow();
            require(ItemStack.matches(decoded, root), "Disk-loaded stack differs in orientation " + slot);
            require(NeoForgeJustDireGroundStompSpike.timer(root) > 0, "Disk cooldown was lost");
        }
    }

    static JustDireGroundStompSpikeNetwork.Activate oldPacket(int slot) throws Exception {
        var lines = Files.readAllLines(marker("packet-" + slot + ".txt"));
        return new JustDireGroundStompSpikeNetwork.Activate(Integer.parseInt(lines.getFirst()),
                java.util.UUID.fromString(lines.get(1)), 71, false);
    }

    static ItemStack smithing(ServerPlayer player, ItemStack root, boolean quick, boolean adapted) {
        require(!preserve, "Preservation seam leaked beyond the preceding recipe");
        var before = root.copy();
        var menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.create(player.level(), STORAGE));
        var template = nativeItem("template_eclipsealloy");
        var addition = nativeItem("eclipsealloy_ingot");
        preserve = adapted;
        try {
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
            if (adapted) { sameActive(before, preview); }
            menu.clicked(SmithingMenu.RESULT_SLOT, 0, quick ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP, player);
            var taken = quick ? player.getInventory().getNonEquipmentItems().stream()
                    .filter(stack -> ItemStack.matches(preview, stack)).findFirst().orElseThrow().copy() : menu.getCarried().copy();
            require(ItemStack.matches(preview, taken) && menu.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty()
                    && menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().isEmpty()
                    && menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().isEmpty(), "Tier take/result/one-input consumption mismatch");
            require(ItemStack.matches(before, root), "Tier take mutated the old root");
            return taken;
        } finally { preserve = false; }
    }

    static String boundaryMatrix(ServerPlayer player, ItemStack live) throws Exception {
        // The native recipe itself owns whether active source components are carried across tiers.
        // Compare it without a preservation seam; do not assume every real recipe resets them.
        player.getInventory().clearContent();
        var baseline = smithing(player, live, false, false);
        StringBuilder changed = new StringBuilder();
        for (String name : ACTIVE) {
            var component = JustDireAttributeTestSupport.component(name);
            if (!Objects.equals(live.get(component), baseline.get(component))) { changed.append(name).append(' '); }
        }
        for (boolean absent : new boolean[]{false, true}) {
            var original = ModContent.getMixedBootsData(live).orElseThrow();
            var historicalOwner = original.left().source();
            if (!absent) {
                for (String name : ACTIVE) {
                    var component = JustDireAttributeTestSupport.component(name);
                    historicalOwner.remove(component);
                    if (live.has(component)) { historicalOwner.set(component, live.get(component)); }
                }
                JustDireAttributeTestSupport.set(historicalOwner, "groundstomp_custom_setting", 0);
            }
            var root = absent ? live.copy() : MixedBootsFactory.createPair(historicalOwner, original.right().source()).primary();
            var data = ModContent.getMixedBootsData(root).orElseThrow();
            var owner = data.left().source();
            for (String name : ACTIVE) {
                var component = JustDireAttributeTestSupport.component(name);
                root.remove(component);
                if (!absent && owner.has(component)) { root.set(component, owner.get(component)); }
            }
            player.getInventory().clearContent();
            sameActive(root, smithing(player, root, absent, true));
            // Deliberately change the replacement projection; a recipe may happen to retain
            // all fields, which alone would not exercise the root/projection equality hazard.
            var replacement = data.left().source();
            JustDireAttributeTestSupport.set(replacement, "groundstomp_bindingmode", 0);
            JustDireAttributeTestSupport.set(replacement, "groundstomp_custom_setting", 1);
            replacement.set(JustDireAttributeTestSupport.component("ability_bindings"),
                    List.of(Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                            .getConstructor(String.class, int.class, boolean.class, boolean.class).newInstance("groundstomp", 72, false, true)));
            replacement.set(JustDireAttributeTestSupport.component("ability_cooldowns"),
                    List.of(Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityCooldown")
                            .getConstructor(String.class, int.class, boolean.class).newInstance("groundstomp", 17, false)));
            var unadapted = MixedBootsRecomposer.recompose(root, replacement, data.right().source());
            require(ACTIVE.stream().anyMatch(name -> !Objects.equals(root.get(JustDireAttributeTestSupport.component(name)),
                    unadapted.get(JustDireAttributeTestSupport.component(name)))), "Changed projection did not exercise the preservation boundary");
            preserve = true;
            try {
                sameActive(root, MixedBootsRecomposer.recompose(root, replacement, data.right().source()));
            } finally { preserve = false; }
        }
        var unrelated = live.copy();
        var timers = new java.util.ArrayList<>(unrelated.getOrDefault(JustDireAttributeTestSupport.<List<Object>>component("ability_cooldowns"), List.of()));
        timers.add(Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityCooldown")
                .getConstructor(String.class, int.class, boolean.class).newInstance("earthquake", 77, true));
        unrelated.set(JustDireAttributeTestSupport.component("ability_cooldowns"), List.copyOf(timers));
        var bindings = new java.util.ArrayList<>(unrelated.getOrDefault(JustDireAttributeTestSupport.<List<Object>>component("ability_bindings"), List.of()));
        bindings.add(Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                .getConstructor(String.class, int.class, boolean.class, boolean.class).newInstance("earthquake", 72, false, true));
        unrelated.set(JustDireAttributeTestSupport.component("ability_bindings"), List.copyOf(bindings));
        player.getInventory().clearContent();
        sameActive(unrelated, smithing(player, unrelated, true, true));
        return "native tier recipe changed active components: [" + changed + "]; adapted equal/absent and unrelated records passed; "
                + "separate changed-projection equal/absent control requires preservation";
    }

    private static void sameActive(ItemStack before, ItemStack after) {
        for (String name : ACTIVE) {
            var component = JustDireAttributeTestSupport.component(name);
            require(before.has(component) == after.has(component) && Objects.equals(before.get(component), after.get(component)),
                    "Preservation changed " + name);
        }
        for (String name : List.of("groundstomp_upgrade_installed", "groundstomp_toggle", "groundstomp_value", "tool_enabled")) {
            var component = JustDireAttributeTestSupport.component(name);
            require(Objects.equals(before.get(component), after.get(component)), "Existing control preservation changed " + name);
        }
    }
    private static ItemStack nativeItem(String name) { return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("justdirethings", name))); }
    private static Path marker(String suffix) { return Path.of(System.getProperty("mismatchedboots.runtimetest.markers"), PREFIX + "-" + suffix); }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
