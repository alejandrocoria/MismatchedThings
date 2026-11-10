package games.alejandrocoria.mismatchedleggings.test.silentgear;

import com.mojang.serialization.JsonOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.silentchaos512.gear.api.util.DataResource;
import net.silentchaos512.gear.util.TraitHelper;

import java.nio.file.Files;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Chat-sized native part commands are parsed and crafted by the real server before delivery to the owner. */
final class ManualFixtures {
    static void write(ServerPlayer player) throws Exception {
        var text = new StringBuilder("Test world with cheats; prepared native parts, not a survival acquisition promise.\n"
                + "Take original Silent Gear iron leggings from the creative examples. Craft them with each given plate/lining before mixing.\n"
                + "Phantom lining adds Light; feather plates add Flutter; amethyst Renew; iron Malleable; diamond Brittle; bone Chipping; quartz Crushing; copper Polished.\n"
                + "Copper uses its ordinary native clocks. The saved barrel at 0 65 0 instead contains explicitly accelerated, unequal transitioned clocks; native Oxidized I can reset repeatedly.\n\n");
        for (var sample : List.of(new String[]{"lining", "phantom_membrane", "light"}, new String[]{"legging_plates", "feather", "flutter"},
                new String[]{"legging_plates", "amethyst", "renew"}, new String[]{"legging_plates", "iron", "malleable"},
                new String[]{"legging_plates", "diamond", "brittle"}, new String[]{"legging_plates", "bone", "chipping"},
                new String[]{"legging_plates", "quartz", "crushing"}, new String[]{"legging_plates", "copper", "polished"})) {
            var part = FoundationChecks.part(sample[0], sample[1]);
            var encoded = ItemStack.CODEC.encodeStart(player.registryAccess().createSerializationContext(JsonOps.INSTANCE), part).getOrThrow();
            String value = encoded.getAsJsonObject().getAsJsonObject("components").get("silentgear:material_list").toString()
                    .replaceAll("\"([A-Za-z_][A-Za-z0-9_]*)\":", "$1:");
            String command = "give @s silentgear:" + sample[0] + "[silentgear:material_list=" + value + "]";
            require(command.length() + 1 <= 256, "Manual part command exceeds chat limit");
            player.getInventory().clearContent();
            player.getInventory().setSelectedSlot(0);
            player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER), command);
            require(ItemStack.matches(part, player.getMainHandItem()), "Native give command changed part " + sample[1]);
            var input = CraftingInput.of(2, 1, List.of(FoundationChecks.armor("iron"), player.getMainHandItem().copy()));
            var recipe = player.level().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, player.level()).orElseThrow();
            var crafted = recipe.value().assemble(input);
            require(TraitHelper.getTraitLevel(crafted, DataResource.trait("silentgear:" + sample[2])) > 0, "Native crafting missed requested trait " + sample[2]);
            text.append(sample[2]).append(":\n/").append(command).append('\n');
        }
        player.getInventory().clearContent();
        Files.writeString(StateChecks.report("manual-fixtures.txt"), text);
    }
}
