package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.UUID;

/** Real native event dispatch and snow/piglin hooks; no duplicate protection engine. */
public final class AllTheModiumTest {
    private static final String MODE = System.getProperty("mismatchedboots.runtimetest.allthemodium", "");
    private static boolean finished;
    private static int cases;
    private static int peers;

    private AllTheModiumTest() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> run(event.getServer()));
    }

    public static ItemStack observe(ItemStack original) {
        peers++;
        return original;
    }

    private static void run(MinecraftServer server) {
        if (finished) {
            return;
        }
        finished = true;

        try {
            boolean absent = MODE.equals("absent");
            require(ModList.get().isLoaded("allthemodium") != absent, "Wrong AllTheModium environment");
            var player = new FakePlayer(server.overworld(), new GameProfile(UUID.randomUUID(), "AllTheModiumTest"));
            player.setGameMode(GameType.SURVIVAL);

            check(player, new ItemStack(Items.DIAMOND_BOOTS), false, false, false);
            check(player, new ItemStack(Items.LEATHER_BOOTS), false, true, false);
            check(player, new ItemStack(Items.GOLDEN_BOOTS), false, false, true);
            var ordinary = MixedBootsFactory.createPair(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS));
            check(player, ordinary.primary(), false, false, false);
            check(player, ordinary.inverse(), false, false, false);

            if (!absent) {
                for (String name : List.of("allthemodium", "vibranium", "unobtainium")) {
                    ItemStack nativeBoots = boots(name);
                    ItemStack original = nativeBoots.copy();
                    check(player, nativeBoots, true, true, true);
                    var pair = MixedBootsFactory.createPair(nativeBoots, new ItemStack(Items.DIAMOND_BOOTS));
                    require(ItemStack.matches(nativeBoots, original), "Composition changed native input");

                    for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
                        check(player, root, true, true, true);
                        require(!root.has(DataComponents.UNBREAKABLE) && !root.has(DataComponents.DAMAGE_RESISTANT),
                                "A single source bypassed root component policies");
                        wrongSlots(player, root);
                    }
                }

                var pair = MixedBootsFactory.createPair(boots("allthemodium"), boots("vibranium"));
                for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
                    check(player, root, true, true, true);
                    require(root.has(DataComponents.UNBREAKABLE) && root.has(DataComponents.DAMAGE_RESISTANT),
                            "Two matching native entitlements lost their ordinary root components");
                }

                ItemStack missing = pair.primary().copy();
                missing.remove(ModContent.mixedBootsDataType().orElseThrow());
                check(player, missing, false, false, false);

                ItemStack overstack = pair.primary().copy();
                overstack.setCount(2);
                check(player, overstack, false, false, false);

                ItemStack spoof = new ItemStack(Items.STICK);
                spoof.set(ModContent.mixedBootsDataType().orElseThrow(), ModContent.getMixedBootsData(pair.primary()).orElseThrow());
                check(player, spoof, false, false, false);
            }

            require(MODE.equals("chains") ? peers > 0 : peers == 0, "Wrong cooperating-return environment");
            NetworkAcceptanceFiles.write("allthemodium-report.txt", cases + " native event/hook cases; " + MODE
                    + "; unchanged native/vanilla results, both orientations, slot boundaries, malformed roots, "
                    + "source immutability and component policies; peer returns=" + peers + "\n");
            NetworkAcceptanceFiles.write("allthemodium-success.txt", "neoforge\n");
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure("allthemodium", failure);
        } finally {
            server.halt(false);
        }
    }

    private static ItemStack boots(String name) {
        var key = Identifier.fromNamespaceAndPath("allthemodium", name + "_boots");
        return new ItemStack(BuiltInRegistries.ITEM.get(key).orElseThrow().value());
    }

    private static void equip(FakePlayer player, ItemStack stack, EquipmentSlot target) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        player.setItemSlot(target, stack);
        player.setShiftKeyDown(false);
    }

    private static void check(FakePlayer player, ItemStack input, boolean damageProtection, boolean snow, boolean piglins) {
        ItemStack root = input.copy();
        ItemStack before = root.copy();
        equip(player, root, EquipmentSlot.FEET);
        damage(player, damageProtection);

        require(root.getItem().makesPiglinsNeutral(root, player) == piglins, "Wrong piglin hook: " + input);
        require(PowderSnowBlock.canEntityWalkOnPowderSnow(player) == snow, "Wrong powder-snow decision: " + input);
        require(player.getItemBySlot(EquipmentSlot.FEET) == root, "Native equipment was substituted");
        require(ItemStack.matches(before, root), "Native events/hooks mutated the root or source history");
        cases++;
    }

    private static void wrongSlots(FakePlayer player, ItemStack input) {
        for (EquipmentSlot slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.CHEST)) {
            ItemStack root = input.copy();
            ItemStack before = root.copy();
            equip(player, root, slot);
            damage(player, false);
            require(!PowderSnowBlock.canEntityWalkOnPowderSnow(player), "Wrong-slot snow entitlement");
            require(ItemStack.matches(before, root), "Wrong-slot query changed the root/history");
            cases++;
        }
    }

    private static void damage(FakePlayer player, boolean expected) {
        var level = player.level();
        var fall = new LivingFallEvent(player, 9, 1);
        NeoForge.EVENT_BUS.post(fall);
        require(fall.isCanceled() == expected, "Wrong native fall cancellation");
        require(fall.getDistance() == 9 && fall.getDamageMultiplier() == 1, "Fall parameters changed");

        player.setRemainingFireTicks(80);
        var fire = new EntityInvulnerabilityCheckEvent(player, level.damageSources().lava(), false);
        NeoForge.EVENT_BUS.post(fire);
        require(fire.isInvulnerable() == expected, "Wrong native fire immunity");
        require(player.getRemainingFireTicks() == (expected ? 0 : 80), "Wrong native clear-fire side effect");
        player.clearFire();

        for (var source : List.of(level.damageSources().generic(), level.damageSources().freeze(), level.damageSources().drown())) {
            var unrelated = new EntityInvulnerabilityCheckEvent(player, source, false);
            NeoForge.EVENT_BUS.post(unrelated);
            require(!unrelated.isInvulnerable(), "Boots granted another piece's protection");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
