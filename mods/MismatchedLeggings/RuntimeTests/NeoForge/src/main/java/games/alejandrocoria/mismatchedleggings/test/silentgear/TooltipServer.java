package games.alejandrocoria.mismatchedleggings.test.silentgear;

import com.google.gson.JsonArray;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.CraftingInput;
import net.neoforged.fml.ModList;
import net.silentchaos512.gear.crafting.recipe.GearPartSwapRecipe;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Real paid roots in unticked chest storage; ordinary menu packets carry the complete source caches. */
public final class TooltipServer {
    private static int ticks;
    private static boolean initialized;
    private static boolean offered;
    private static boolean finished;
    private static List<ItemStack> samples;
    private static List<ItemStack> snapshots;

    private TooltipServer() {
    }

    public static void tick(MinecraftServer server) {
        if (finished || !server.isDedicatedServer()) {
            return;
        }
        try {
            require(++ticks < 3600, "SG tooltip server timed out");
            require(!Files.exists(StateChecks.report("client.failure.txt")), "SG tooltip client failed");
            if (!initialized) {
                if (ticks < 20) {
                    return;
                }
                boolean absent = System.getProperty("leggings.test.sgTooltipVariant", "normal").equals("absent");
                require(ModList.get().isLoaded("silentgear") != absent && ModList.get().isLoaded("mismatchedboots")
                        == Boolean.getBoolean("leggings.test.boots"), "Wrong tooltip graph");
                samples = absent ? FoundationChecks.paid(FoundationChecks.player(server),
                        new ItemStack(Items.GOLDEN_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS)) : samples(server);
                snapshots = samples.stream().map(ItemStack::copy).toList();
                TooltipFixtures.save(server, samples, absent);
                initialized = true;
                Files.writeString(StateChecks.report("ready.txt"), "Production tooltip fixtures ready\n");
            }
            var players = server.getPlayerList().getPlayers();
            if (offered) {
                if (!Files.exists(StateChecks.report("client.ok")) || !players.isEmpty()) {
                    return;
                }
                for (int i = 0; i < samples.size(); i++) {
                    require(ItemStack.matches(samples.get(i), snapshots.get(i)), "Tooltip changed server paid root/history");
                }
                Files.writeString(StateChecks.report("state.txt"), "Paid inverse source roots sent by actual menu packets; unchanged server snapshots\n");
                server.saveEverything(true, true, true);
                SkeletonChecks.passed("server");
                finished = true;
                server.halt(false);
                return;
            }
            if (players.isEmpty()) {
                return;
            }
            var player = players.getFirst();
            player.setInvulnerable(true);
            var container = new SimpleContainer(27);
            var expected = new JsonArray();
            var ops = player.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            for (int i = 0; i < samples.size(); i++) {
                container.setItem(i, samples.get(i));
                expected.add(ItemStack.CODEC.encodeStart(ops, samples.get(i)).getOrThrow());
            }
            player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> ChestMenu.threeRows(id, inventory, container),
                    Component.literal("Silent Gear source tooltip acceptance")));
            Files.writeString(StateChecks.report("tooltip-samples.json"), expected.toString());
            offered = true;
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("server", failure);
            server.halt(false);
        }
    }

    private static List<ItemStack> samples(MinecraftServer server) throws Exception {
        var owner = FoundationChecks.player(server);
        var iron = FoundationChecks.armor("iron");
        iron.set(DataComponents.CUSTOM_NAME, Component.literal("Original SG leggings"));
        iron.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Original source lore"))));
        var lined = FoundationChecks.armor("gold", "lining", "wool");
        var plain = FoundationChecks.armor("iron");
        // Native armor always carries a dye line. Omit only that cosmetic component for the empty-detail control.
        plain.remove(DataComponents.DYED_COLOR);
        var coated = FoundationChecks.armor("copper", "coating", "gold");
        var upgrade = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("silentgear:magnetic_upgrade")));
        var upgraded = GearPartSwapRecipe.INSTANCE.assemble(CraftingInput.of(2, 1,
                List.of(FoundationChecks.armor("iron"), upgrade)));
        require(!upgraded.isEmpty(), "Native magnetic upgrade fixture failed");

        var result = new ArrayList<ItemStack>();
        result.addAll(FoundationChecks.paid(owner, iron, new ItemStack(Items.DIAMOND_LEGGINGS)));
        result.addAll(FoundationChecks.paid(owner, lined, plain));
        result.addAll(FoundationChecks.paid(owner, coated, upgraded));
        if (Boolean.getBoolean("leggings.test.boots")) {
            result.addAll(TooltipFixtures.boots(owner));
        }
        return result;
    }
}
