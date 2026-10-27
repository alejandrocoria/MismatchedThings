package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireRefundCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireRefundCredits;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Refund-specific observations hosted by the existing resources save/restart gate. */
final class JustDireRefundLifecycleTest {
    private static final int FIRST_SLOT = 12;

    private JustDireRefundLifecycleTest() {
    }

    static void save(MinecraftServer server, BarrelBlockEntity barrel) {
        var player = JustDireGroundStompTestSupport.player(server, GameType.SURVIVAL);
        var source = NeoForgeJustDireResourcesTest.boots("celestigem", 6_000);
        JustDireAttributeTestSupport.set(source, "stepheight_upgrade_installed", true);
        var bench = new WorkbenchMenu(1, player.getInventory());
        bench.getSlot(0).set(source);
        bench.getSlot(1).set(new ItemStack(Items.DIAMOND_BOOTS));
        var outputs = List.of(bench.getSlot(2).safeTake(1, 1, player), bench.getSlot(3).safeTake(1, 1, player));
        var saved = new JsonArray();
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (int orientation = 0; orientation < outputs.size(); orientation++) {
            ItemStack root = outputs.get(orientation);
            require(credits(root).equals(orientation == 0 ? List.of("stepheight") : List.of()), "Wrong actual owner credit");
            root = take(server, player, root, ItemStack.EMPTY, item("upgrade_jumpboost"));
            require(credits(root).equals(orientation == 0 ? List.of("stepheight", "jumpboost") : List.of("jumpboost")), "Later paid credit missing");
            root = take(server, player, root, item("template_eclipsealloy"), item("eclipsealloy_ingot"));
            player = JustDireRefundBreakTest.equip(server, root);
            var credit = root.get(JustDireRefundCompatibility.component());
            var history = ModContent.getMixedBootsData(root);
            root.hurtAndBreak(2, player, EquipmentSlot.FEET);
            root.setDamageValue(12);
            root.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING), 1);
            var orb = new ExperienceOrb(EntityType.EXPERIENCE_ORB, server.overworld());
            orb.setValue(5);
            orb.playerTouch(player);
            require(root.getDamageValue() == 2, "Actual Mending did not repair credited root");
            JustDireAttributeTestSupport.set(root, "tool_enabled", false);
            JustDireAttributeTestSupport.set(root, "jumpboost_toggle", false);
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            var battery = NeoForgeJustDireResourcesTest.boots("eclipsealloy", 1_000);
            int old = JustDireResourceTestSupport.handler(root).getAmountAsInt();
            require(EnergyHandlerUtil.move(JustDireResourceTestSupport.handler(battery),
                                           JustDireResourceTestSupport.handler(root),
                                           1_000,
                                           null) == 1_000
                            && JustDireResourceTestSupport.handler(root).getAmountAsInt() == old + 1_000, "Credited root recharge failed");
            var data = history.orElseThrow();
            root = MixedBootsRecomposer.recompose(root, data.right().source(), data.left().source());
            require(Objects.equals(credit, root.get(JustDireRefundCompatibility.component()))
                            && root.getDamageValue() == 2, "Wear/repair/control/recharge/recomposition changed credit");
            JustDireRefundBreakTest.refunds();
            for (int state = 0; state < 3; state++) {
                var stored = root.copy();
                if (state == 1) {
                    stored.set(JustDireRefundCompatibility.component(), new JustDireRefundCredits(List.of()));
                }
                if (state == 2) {
                    stored.remove(JustDireRefundCompatibility.component());
                }
                barrel.setItem(FIRST_SLOT + saved.size(), stored);
                saved.add(ItemStack.CODEC.encodeStart(ops, stored).getOrThrow());
            }
        }
        NetworkAcceptanceFiles.write("just-dire-refunds-saved.json", saved.toString());
        NetworkAcceptanceFiles.write("just-dire-refunds-lifecycle-report.txt",
                                     "two real Workbench outputs; paid root-only Jump and tier smithing takes; direct wear, actual Mending, off controls, unequip, FE recharge and recomposition preserve credits without early drops; six paid/empty/absent whole roots saved in the existing barrel\n");
    }

    static void restart(MinecraftServer server, BarrelBlockEntity barrel) throws java.io.IOException {
        var saved = JsonParser.parseString(Files.readString(Path.of(System.getProperty("mismatchedboots.runtimetest.markers"),
                                                                    "just-dire-refunds-saved.json"))).getAsJsonArray();
        require(saved.size() == 6, "Missing independent refund save expectations");
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (int index = 0; index < saved.size(); index++) {
            var expected = ItemStack.CODEC.parse(ops, saved.get(index)).getOrThrow();
            var root = barrel.removeItemNoUpdate(FIRST_SLOT + index);
            require(ItemStack.matches(expected, root), "Actual world restart changed credited whole root");
            var data = ModContent.getMixedBootsData(root).orElseThrow();
            require(!data.left().source().getOrDefault(JustDireAttributeTestSupport.<Boolean>component("jumpboost_upgrade_installed"), false)
                            && !data.right()
                                    .source()
                                    .getOrDefault(JustDireAttributeTestSupport.<Boolean>component("jumpboost_upgrade_installed"), false),
                    "Later credit unexpectedly came from history");
            require(root.has(JustDireRefundCompatibility.component()) == (index % 3 != 2), "Empty/absent credit collapsed on disk");
            var owed = index % 3 != 0 ? List.<String>of() : index < 3 ? List.of("stepheight", "jumpboost") : List.of("jumpboost");
            require(credits(root).equals(owed), "Restart reimported historical purchases");
            JustDireRefundBreakTest.direct(server, root, owed.toArray(String[]::new));
        }
        NetworkAcceptanceFiles.write("just-dire-refunds-restart-report.txt",
                                     "actual second JVM reopens six barrel roots; independent complete-stack equality; later paid Jump absent from both histories; primary/inverse owner credits and exact empty/absence retained; six actual equipped breaks return exactly saved credits, including zero-refund controls\n");
    }

    static ItemStack take(MinecraftServer server, ServerPlayer player, ItemStack root, ItemStack template, ItemStack addition) {
        var menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.create(server.overworld(), BlockPos.ZERO));
        menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(template);
        menu.getSlot(SmithingMenu.BASE_SLOT).set(root);
        menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(addition);
        var expected = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
        menu.clicked(SmithingMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
        require(!expected.isEmpty() && ItemStack.matches(expected, menu.getCarried()) && menu.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty()
                        && menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().isEmpty() && menu.getSlot(SmithingMenu.ADDITIONAL_SLOT)
                                                                                               .getItem()
                                                                                               .isEmpty(), "Actual paid smithing take failed");
        return menu.getCarried();
    }

    static List<String> credits(ItemStack root) {
        var value = root.get(JustDireRefundCompatibility.component());
        return value == null ? List.of() : value.names();
    }

    static ItemStack item(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("justdirethings", name)).orElseThrow());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
