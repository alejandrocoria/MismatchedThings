package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearWearerApi;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Direct binding controls and a recalculation observer; no candidate set routing remains. */
public final class SilentGearWearerFixture {
    private static SilentGearWearerApi api;
    public static boolean active;

    private SilentGearWearerFixture() {
    }

    public static void bind() throws ReflectiveOperationException {
        api = new SilentGearWearerApi();
    }

    public static ItemStack carrier(ItemStack root) throws Throwable {
        return api.carrier(root);
    }

    public static List<?> effects(Object instance) throws Throwable {
        return api.effects(instance);
    }

    public static boolean isWielder(Object effect) {
        return api.isWielder(effect);
    }

    public static void tick(Object effect, Player player, Object instance, ItemStack carrier, boolean selected) throws Throwable {
        api.tick(effect, player, instance, carrier, selected);
    }

    /** Independent bounded probability oracle, shared by the tick and restart fixtures. */
    static long stellarRepairSeed(Object instance, boolean activate) throws Exception {
        Object repair = SilentGearTraitSpike.traitOf(instance).getClass().getMethod("getEffects").invoke(SilentGearTraitSpike.traitOf(instance));
        Object effect = ((List<?>) repair).stream()
                                          .filter(value -> value.getClass().getSimpleName().equals("SelfRepairTraitEffect"))
                                          .findFirst()
                                          .orElseThrow();
        var chance = effect.getClass().getDeclaredField("activationChance");
        chance.setAccessible(true);
        float probability = chance.getFloat(effect) * SilentGearTraitSpike.level(instance);
        for (long seed = 0; seed < 100_000; seed++) {
            if ((new java.util.Random(seed).nextDouble() < probability) == activate) {
                return seed;
            }
        }
        throw new AssertionError("No bounded native Stellar repair seed");
    }

    static void seedStellar(Object instance, boolean activate) throws Exception {
        var field = Class.forName("net.silentchaos512.lib.util.MathUtils").getDeclaredField("RANDOM");
        field.setAccessible(true);
        ((java.util.Random) field.get(null)).setSeed(stellarRepairSeed(instance, activate));
    }

    /** Verified vanilla/native commands, not invented recipes or a production debug command. */
    static void manualCommands(ServerPlayer player, NeoForgeSilentGearTest.NativeApi nativeApi) throws Exception {
        String component = "silentgear:material_list";
        var ops = player.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var commands = new StringBuilder("Native API-constructed test parts, NOT stock survival acquisition of these materials.\n"
                                                 + "Use a test world with cheats. Each command fits normal chat.\n"
                                                 + "Take the corresponding ORIGINAL Silent Gear iron armor piece from its creative examples.\n"
                                                 + "Craft that native piece + the command-given plate together in a crafting grid, BEFORE combining boots.\n"
                                                 + "The real native part-swap recipe and resulting trait are verified for every command below.\n\n");
        for (var family : List.of(new String[]{"adamant", "nickel"}, new String[]{"stellar", "refined_iron"})) {
            for (var piece : List.of(new String[]{"boots", "boot_plates"}, new String[]{"helmet", "helmet_plates"},
                                     new String[]{"chestplate", "chestplate_plates"}, new String[]{"leggings", "legging_plates"})) {
                ItemStack nativeItem = nativeApi.armor(piece[0], piece[1], family[1]);
                ItemStack part = nativeApi.part(piece[1], family[1]);
                var data = ItemStack.CODEC.encodeStart(ops, part).getOrThrow().getAsJsonObject().getAsJsonObject("components").get(component);
                // Valid SNBT permits unquoted simple keys; keep the verified fixture usable in normal chat.
                String snbt = data.toString().replaceAll("\"([A-Za-z_][A-Za-z0-9_]*)\":", "$1:");
                String command = "give @s silentgear:" + piece[1] + "[" + component + "=" + snbt + "]";
                if (command.length() + 1 > 256) {
                    throw new AssertionError("Manual gear command exceeds chat limit");
                }
                player.getInventory().clearContent();
                player.getInventory().setSelectedSlot(0);
                var source = player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER);
                player.level().getServer().getCommands().performPrefixedCommand(source, command);
                ItemStack givenPart = player.getMainHandItem();
                var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 1, List.of(nativeApi.armor(piece[0], piece[1], "iron"), givenPart));
                Object recipe = nativeApi.swapClass.getField("INSTANCE").get(null);
                if (!(boolean) nativeApi.matches.invoke(recipe, input, player.level())) {
                    throw new AssertionError("Native main-part swap refused " + piece[0]);
                }
                ItemStack given = (ItemStack) nativeApi.assemble.invoke(recipe, input);
                if (!given.is(nativeItem.getItem()) || nativeApi.traitLevel(given, family[0]) < 1) {
                    throw new AssertionError("Manual native gear command did not produce " + family[0] + " " + piece[0]);
                }
                commands.append(family[0]).append(' ').append(piece[0]).append('\n').append('/').append(command).append('\n');
            }
        }
        player.getInventory().clearContent();
        NetworkAcceptanceFiles.write("silent-gear-wearer-manual.txt", commands.toString());
    }
}
