package games.alejandrocoria.mismatchedleggings.test;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SmithingCredits {
    private static final Map<UUID, Map<Identifier, Integer>> COUNTS = new HashMap<>();

    private SmithingCredits() {
    }

    public static void crafted(ServerPlayer player, RecipeHolder<?> recipe) {
        if (Boolean.getBoolean("leggings.test.smithing") || Boolean.getBoolean("leggings.test.forbiddenArcanus")
                || Boolean.getBoolean("leggings.test.forbiddenRecovery")) {
            COUNTS.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>()).merge(recipe.id().identifier(), 1, Integer::sum);
        }
    }

    static int count(ServerPlayer player, Identifier id) {
        return COUNTS.getOrDefault(player.getUUID(), Map.of()).getOrDefault(id, 0);
    }
}
