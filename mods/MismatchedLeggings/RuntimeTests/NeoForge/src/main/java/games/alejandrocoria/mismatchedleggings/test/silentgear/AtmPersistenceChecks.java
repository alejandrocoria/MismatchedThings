package games.alejandrocoria.mismatchedleggings.test.silentgear;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.silentchaos512.gear.setup.SgDataComponents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Complete native container persistence across independent game JVMs, never a reconstructed load. */
final class AtmPersistenceChecks {
    private static final BlockPos STORAGE = new BlockPos(0, 2, 0);

    private AtmPersistenceChecks() {}

    static void run(MinecraftServer server, ServerPlayer player, String stage, StringBuilder report) throws Exception {
        var expectedFile = Path.of(System.getProperty("leggings.test.sgAtmExpected"));
        var level = server.overworld();
        level.getChunk(0, 0);
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        if (stage.equals("write")) {
            var stored = new ArrayList<ItemStack>();
            for (String material : List.of("allthemodium", "vibranium", "unobtainium")) {
                for (var slot : List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS)) {
                    var source = AtmMaterialChecks.armor(material, slot);
                    stored.add(source.copy());
                    var other = new ItemStack(slot == EquipmentSlot.FEET ? Items.DIAMOND_BOOTS : Items.DIAMOND_LEGGINGS);
                    for (var root : AtmMaterialChecks.paid(player, source, other, slot)) {
                        root.setDamageValue(root.getMaxDamage() / 3);
                        stored.add(root);
                    }
                }
            }
            player.getInventory().clearContent();
            level.setChunkForced(0, 0, true);
            level.setBlockAndUpdate(STORAGE, Blocks.BARREL.defaultBlockState());
            var barrel = (BarrelBlockEntity) level.getBlockEntity(STORAGE);
            require(barrel != null && stored.size() == 18, "Incomplete material container setup");
            barrel.clearContent();
            var expected = new JsonArray();
            for (int i = 0; i < stored.size(); i++) {
                barrel.setItem(i, stored.get(i));
                expected.add(ItemStack.CODEC.encodeStart(ops, stored.get(i)).getOrThrow());
            }
            barrel.setChanged();
            Files.writeString(expectedFile, expected.toString());
            report.append("Container write: 18 native/paid stacks; three materials; both products/orientations; damaged roots\n");
        } else {
            require(stage.equals("reopen"), "Unknown material persistence stage");
            var barrel = (BarrelBlockEntity) level.getBlockEntity(STORAGE);
            require(barrel != null, "Saved material barrel did not load");
            var expected = JsonParser.parseString(Files.readString(expectedFile)).getAsJsonArray();
            require(expected.size() == 18, "Incomplete saved expectations");
            for (int i = 0; i < expected.size(); i++) {
                var loaded = barrel.getItem(i);
                var encoded = ItemStack.CODEC.encodeStart(ops, loaded).getOrThrow();
                require(encoded.equals(expected.get(i))
                        && ItemStack.matches(loaded, ItemStack.CODEC.parse(ops, expected.get(i)).getOrThrow()),
                        "Native disk load changed complete material/root components at " + i);
                var snapshot = loaded.copy();
                var sources = i % 3 == 0 ? List.of(loaded) : TooltipFixtures.sources(loaded);
                var material = sources.stream().filter(source -> source.has(SgDataComponents.GEAR_CONSTRUCTION.get())).findFirst().orElseThrow();
                require(material.has(SgDataComponents.GEAR_PROPERTIES.get()), "Restored native properties missing");
                for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
                player.getInventory().clearContent();
                player.setItemSlot(i % 6 < 3 ? EquipmentSlot.FEET : EquipmentSlot.LEGS, loaded);
                require(PiglinAi.isWearingSafeArmor(player), "Restored material Brilliant missing at " + i);
                require(ItemStack.matches(snapshot, loaded), "Restored material query changed disk state");
            }
            report.append("Container reopen: 18 actual loaded stacks; exact encoded components and material caches; native/mixed Brilliant; no reconstruction or replacement\n");
        }
    }
}
