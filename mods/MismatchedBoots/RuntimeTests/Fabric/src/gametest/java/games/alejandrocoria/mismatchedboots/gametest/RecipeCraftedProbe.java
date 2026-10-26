package games.alejandrocoria.mismatchedboots.gametest;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class RecipeCraftedProbe {
    private static final Map<ServerPlayer, Map<ResourceKey<Recipe<?>>, Integer>> COUNTS = new WeakHashMap<>();

    private RecipeCraftedProbe() {
    }

    public static void record(ServerPlayer player, ResourceKey<Recipe<?>> id) {
        COUNTS.computeIfAbsent(player, ignored -> new HashMap<>()).merge(id, 1, Integer::sum);
    }

    static int count(ServerPlayer player, ResourceKey<Recipe<?>> id) {
        return COUNTS.getOrDefault(player, Map.of()).getOrDefault(id, 0);
    }
}
