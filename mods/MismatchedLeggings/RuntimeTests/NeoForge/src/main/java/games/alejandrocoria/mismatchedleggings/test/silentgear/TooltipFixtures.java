package games.alejandrocoria.mismatchedleggings.test.silentgear;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.LeggingHalf;
import games.alejandrocoria.mismatchedleggings.data.MixedLeggingsData;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.silentchaos512.gear.api.part.PartList;
import net.silentchaos512.gear.api.util.DataResource;
import net.silentchaos512.gear.core.component.GearConstructionData;
import net.silentchaos512.gear.core.component.GearPropertiesData;
import net.silentchaos512.gear.gear.part.PartInstance;
import net.silentchaos512.gear.setup.SgDataComponents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Local fixture operations only; exceptional provenance is deliberately not a survival editing feature. */
final class TooltipFixtures {
    private TooltipFixtures() {
    }

    static List<ItemStack> sources(ItemStack root) throws Exception {
        if (MixedLeggingsStructure.isMixedItem(root)) {
            var data = MixedLeggingsStructure.validData(root).orElseThrow();
            return List.of(data.left().source(), data.right().source());
        }
        var content = Class.forName("games.alejandrocoria.mismatchedboots.content.ModContent");
        var optional = (java.util.Optional<?>) content.getMethod("getMixedBootsData", ItemStack.class).invoke(null, root);
        var data = optional.orElseThrow();
        var result = new ArrayList<ItemStack>();
        for (String side : List.of("left", "right")) {
            var half = data.getClass().getMethod(side).invoke(data);
            result.add((ItemStack) half.getClass().getMethod("source").invoke(half));
        }
        return result;
    }

    static ItemStack replace(ItemStack root, ItemStack source) {
        var result = root.copy();
        var data = MixedLeggingsStructure.validData(root).orElseThrow();
        var before = source.copy();
        result.set(LeggingsContent.mixedLeggingsDataType(), new MixedLeggingsData(new LeggingHalf(source), data.right()));
        require(ItemStack.matches(before, source), "Exceptional fixture construction normalized its source control");
        return result;
    }

    static ItemStack invalidParts(ItemStack source) {
        var result = source.copy();
        var construction = result.get(SgDataComponents.GEAR_CONSTRUCTION.get());
        var raw = new ArrayList<>(construction.parts());
        var invalid = PartInstance.of(DataResource.part("silentgear:mismatchedleggings_test_missing_part"));
        require(!invalid.isValid(), "Invalid-reference control unexpectedly resolved");
        raw.add(invalid);
        result.set(SgDataComponents.GEAR_CONSTRUCTION.get(), new GearConstructionData(PartList.immutable(raw)));
        return result;
    }

    static List<ItemStack> boots(ServerPlayer player) throws Exception {
        var source = DefenseIncomingChecks.nativePiece("boots", "boot_plates", "gold");
        var other = new ItemStack(Items.DIAMOND_BOOTS);
        var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
        var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, source, other);
        var result = List.of((ItemStack) pair.getClass().getMethod("primary").invoke(pair),
                (ItemStack) pair.getClass().getMethod("inverse").invoke(pair));
        for (int index = 0; index < 2; index++) {
            player.getInventory().clearContent();
            var menu = new WorkbenchMenu(401, player.getInventory());
            menu.getSlot(0).set(source.copy());
            menu.getSlot(1).set(other.copy());
            menu.slotsChanged(menu.getSlot(0).container);
            menu.clicked(index + 2, 0, ContainerInput.PICKUP, player);
            require(ItemStack.matches(menu.getCarried(), result.get(index))
                    && menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty(), "Paid Boots fixture failed");
            menu.setCarried(ItemStack.EMPTY);
            menu.removed(player);
        }
        player.getInventory().clearContent();
        return result;
    }

    static void save(MinecraftServer server, List<ItemStack> samples, boolean absent) throws Exception {
        for (int x = 3; x < 14; x++) {
            for (int z = 3; z < 14; z++) {
                server.overworld().setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        var position = new BlockPos(5, 81, 5);
        server.overworld().setBlock(position, Blocks.BARREL.defaultBlockState(), 3);
        var barrel = (BarrelBlockEntity) server.overworld().getBlockEntity(position);
        require(barrel != null, "Manual tooltip barrel missing");
        for (int i = 0; i < samples.size(); i++) {
            barrel.setItem(i, samples.get(i).copy());
        }
        if (!absent) {
            var natives = List.of(sources(samples.get(0)).getFirst(), sources(samples.get(2)).getFirst(),
                    sources(samples.get(4)).getFirst(), sources(samples.get(4)).get(1));
            for (int i = 0; i < 4; i++) {
                barrel.setItem(9 + i, natives.get(i));
            }
            var normal = samples.get(2);
            var original = sources(normal).getFirst();
            var missing = original.copy();
            missing.remove(SgDataComponents.GEAR_PROPERTIES.get());
            barrel.setItem(13, replace(normal, missing));
            missing = original.copy();
            missing.remove(SgDataComponents.GEAR_CONSTRUCTION.get());
            barrel.setItem(14, replace(normal, missing));
            missing = original.copy();
            missing.set(SgDataComponents.GEAR_PROPERTIES.get(), GearPropertiesData.EMPTY);
            barrel.setItem(15, replace(normal, missing));
            var broken = original.copy();
            broken.setDamageValue(broken.getMaxDamage());
            barrel.setItem(16, replace(normal, broken));
            barrel.setItem(17, broken);
        }
        barrel.setChanged();
        Files.writeString(StateChecks.report("tooltip-manual.txt"),
                "Source world (absolute; generated under RuntimeTests/NeoForge/build/run): " + Path.of("world").toAbsolutePath().normalize() + "\n"
                + "After the test server exits, COPY the entire world folder into the target NeoForge client's saves folder. Do not open the original while tests are running.\n"
                + "Use current Leggings and, for the boots graph, current Boots; their nested Things is sufficient. Native profiles also require SG 4.2.3 / Silent Lib 11.2.0.\n"
                + "Enable cheats/LAN cheats; /tp @s 8 82 8. Saved barrel at 5 81 5.\n"
                + (absent ? "Absent profile: slots 1..2 are ordinary vanilla-source mixed leggings. No SG is required.\n"
                : "Barrel slots 1..6: paid inverse SG/vanilla, lined gold/iron and coated copper/magnetic-upgraded iron mixed leggings. Slots 7..8: paid inverse mixed Boots only when the boots graph is selected.\n"
                + "Slots 10..13: corresponding native named/lore, lined, coated and upgraded leggings controls. Slots 14..16: synthetic missing-properties, missing-construction and empty-properties roots; slot 17: synthetic Broken-source mixed; slot 18: that native Broken source.\n"
                + "Exceptional histories were prepared directly by tests, not by a new survival recipe or production editing command. Unresolved part references are checked only in unsaved automated client probes, not distributed as world fixtures.\n"
                + "Check both inverse outputs and native controls: compact, Ctrl, Ctrl+Shift, Alt, combinations and F3+H; change SG vanilla-style tooltip config and rebind its keys. Native source blocks belong under existing identities, with no extra heading/root table.\n"
                + "Check tall two-SG-source output at 1080p / GUI scale 2. With Boots installed, compare both products and ordinary non-SG output. Exceptional roots omit only unusable source details; the healthy source remains. Mixed Broken provenance must not import the native repairable Broken diagnostic.\n")
                + "No manual save/rejoin/gameplay cycle is required for this presentation plan. The copied world is just reproducible item setup.\n");
    }
}
