package games.alejandrocoria.mismatchedleggings.test.silentgear;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;
import static games.alejandrocoria.mismatchedleggings.test.silentgear.DefenseIncomingChecks.nativePiece;

/** Vanilla saved container with native/product items; no debug command or production fixture component. */
final class RemainingManualFixtures {
    private RemainingManualFixtures() {}

    static void write(ServerPlayer player) throws Exception {
        var ward = FoundationChecks.armor("tyrian_steel");
        var cures = RemainingLifecycle.cures(FoundationChecks.armor("iron"));
        var pieces = new ArrayList<ItemStack>();
        pieces.add(ward.copy());
        pieces.addAll(FoundationChecks.paid(player, ward, new ItemStack(Items.DIAMOND_LEGGINGS)));
        pieces.add(cures.copy());
        pieces.addAll(FoundationChecks.paid(player, cures, new ItemStack(Items.DIAMOND_LEGGINGS)));
        pieces.add(new ItemStack(Items.DIAMOND_LEGGINGS));
        var feet = nativePiece("boots", "boot_plates", "tyrian_steel");
        pieces.add(feet);
        if (Boolean.getBoolean("leggings.test.boots")) { pieces.add(RemainingLifecycle.mixedFeet(feet)); }
        var position = new BlockPos(5, 81, 5);
        player.level().setBlock(position, Blocks.BARREL.defaultBlockState(), 3);
        require(player.level().getBlockEntity(position) instanceof BarrelBlockEntity, "Manual fixture barrel was not created");
        var barrel = (BarrelBlockEntity) player.level().getBlockEntity(position);
        for (int i = 0; i < pieces.size(); i++) { barrel.setItem(i, pieces.get(i).copy()); }
        barrel.setChanged();
        Files.writeString(StateChecks.report("remaining-manual.txt"), "Use a COPY of this profile's server/world in an ordinary NeoForge 26.1.2 instance with Things/Leggings, SG 4.2.3 and Silent Lib 11.2.0; include current Boots for the boots graph.\n"
                + "Source world (generated under RuntimeTests/NeoForge/build/run): " + Path.of("world").toAbsolutePath().normalize() + "\n"
                + "Copy the entire world folder into the target client's saves folder; optionally rename the copy.\n"
                + "Do not open the disposable world while its test server is running. Enable cheats or use LAN cheats for teleport/effect commands.\n"
                + "Saved vanilla barrel: 5 81 5, on the stone platform. /tp @s 8 82 8\n"
                + "Barrel slots 1..3: native Tyrian Steel leggings, paid primary/inverse Ward mixed. Slots 4..6: synthetic native Cure Poison+Wither leggings and both paid inverses. Slot 7: plain diamond leggings. Slot 8: native Tyrian Steel boots. Slot 9 (boots graph only): mixed Ward boots.\n"
                + "Cures have no stock material/recipe acquisition. These prepared properties are test fixtures, not added survival recipes. No test-mod item/component or production debug command is needed.\n"
                + "Ward: use disposable item copies and equip native, then each mixed result separately, without original companion armor. In survival Overworld, /tp @s 8 -97 8 is strictly below minY-32. Compare native upward rescue and Levitation amplifier 9 (level X)/Slow Falling amplifier 3 (level IV). Return to /tp @s 8 82 8 and /effect clear @s before the next sample. Do not rely on Resistance to prevent void loss; keep backups in the copied fixture barrel.\n"
                + "With Boots installed, test mixed Ward LEGS with plain feet, mixed Ward FEET with plain legs, then both mixed. Native still owns one rescue branch, not one per matching source/piece.\n"
                + "Cures: start with the prepared item idle in a nonselected inventory slot, no other cure gear selected. /effect give @s minecraft:poison 60 and /effect give @s minecraft:wither 60 must remain; /effect give @s minecraft:luck 120 is an unrelated control. Move the item to LEGS, main hand, then offhand: both targets should disappear and Luck remain. Return it to idle and reapply the targets: they must persist. Repeat native and both mixed orientations. Stay protected from damage during these tests.\n"
                + "Finally save/reopen the copied world, then save/restart a same-loader dedicated server and reconnect with prepared mixed items. Recheck effects and unchanged item histories. Do not use cross-loader connections.\n");
    }
}
