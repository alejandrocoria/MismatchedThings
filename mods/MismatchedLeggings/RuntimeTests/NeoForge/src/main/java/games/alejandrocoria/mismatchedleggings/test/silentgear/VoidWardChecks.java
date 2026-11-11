package games.alejandrocoria.mismatchedleggings.test.silentgear;

import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearVoidWard;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.silentchaos512.gear.util.TraitHelper;

import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;
import static games.alejandrocoria.mismatchedleggings.test.silentgear.DefenseIncomingChecks.clear;
import static games.alejandrocoria.mismatchedleggings.test.silentgear.DefenseIncomingChecks.nativePiece;

/** Real native event and original-push witness for the packaged adapter, with research disabled. */
public final class VoidWardChecks {
    private static Player watched;
    private static int pushes;

    private VoidWardChecks() {}

    public static void push(Player player, double x, double y, double z) {
        if (player == watched) {
            require(x == 0 && y == 20 && z == 0, "Native Void Ward push parameters changed");
            pushes++;
        }
    }

    static void run(ServerPlayer player, StringBuilder report, boolean absent) throws Exception {
        String variant = System.getProperty("leggings.test.sgVoidVariant", "");
        if (variant.isEmpty()) {
            return;
        }
        require(!Boolean.getBoolean("leggings.test.sgRemainingSpike"), "Research candidate must not participate in production acceptance");
        var event = new PlayerTickEvent.Pre(player);
        require(SilentGearVoidWard.recognize(true, event, false), "Void Ward lost an original true result");
        if (absent) {
            require(!SilentGearVoidWard.recognize(false, event, true), "Absent SG activated Void Ward");
            report.append("Void Ward: quiet optional absence; original true retained\n");
            return;
        }

        boolean enabled = !List.of("permission", "event").contains(variant);
        String events = "net.silentchaos512.gear.event.GearEvents";
        require(proof(events, "mismatchedleggings$sgVoidReady") == enabled, "Final local Void Ward proof differs");
        require(!VoidWardTestPlugin.actualOrder.isEmpty() && VoidWardTestPlugin.mutations == (variant.equals("normal") ? 0 : 1),
                "Missing actual Void Ward order/mutation witness");
        require(proof("net.silentchaos512.gear.item.gear.GearArmorItem", "mismatchedleggings$sgBrilliantReady"),
                "Void Ward refusal affected Brilliant");
        require(proof(events, "mismatchedleggings$sgIncomingReady"), "Void Ward refusal affected defensive readiness");
        boolean boots = Boolean.getBoolean("leggings.test.boots");
        if (boots) {
            require(proof(events, "mismatchedboots$sgVoidReady"), "Leggings changed Boots' Void Ward proof");
        }

        var source = FoundationChecks.armor("tyrian_steel");
        require(TraitHelper.getTraits(source).stream().anyMatch(t -> t.getTraitId().toString().equals("silentgear:void_ward") && t.getLevel() == 1),
                "Native Tyrian Steel LEGS lacks computed Void Ward I");
        var sourceBefore = source.copy();
        var roots = FoundationChecks.paid(player, source, new ItemStack(Items.DIAMOND_LEGGINGS));
        var duplicates = FoundationChecks.paid(player, source, source);
        double threshold = player.level().getMinY() - 32;
        for (var root : List.of(source, roots.getFirst(), roots.get(1), duplicates.getFirst(), duplicates.get(1))) {
            boolean active = root == source || enabled;
            check(player, root, ItemStack.EMPTY, EquipmentSlot.LEGS, threshold, false);
            check(player, root, ItemStack.EMPTY, EquipmentSlot.LEGS, threshold - .5, active);
            for (var slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD)) {
                // Native armor scans all armor slots even when forced there; mixed recognition is LEGS-only.
                check(player, root, ItemStack.EMPTY, slot, threshold - .5, root == source && slot == EquipmentSlot.HEAD);
            }
            check(player, root, ItemStack.EMPTY, null, threshold - .5, false);
        }

        var malformed = roots.getFirst().copy();
        malformed.remove(DataComponents.EQUIPPABLE);
        var foreign = new ItemStack(Items.IRON_LEGGINGS);
        foreign.set(LeggingsContent.mixedLeggingsDataType(), roots.getFirst().get(LeggingsContent.mixedLeggingsDataType()));
        var negatives = new ArrayList<>(List.of(new ItemStack(roots.getFirst().getItem()), roots.getFirst().copyWithCount(2), malformed, foreign));
        for (boolean quarantine : List.of(false, true)) {
            var suspended = roots.getFirst().copy();
            suspended.set(LeggingsContent.createSuspendedDataType(), quarantine
                    ? SuspendedLeggingsData.quarantined(new CompoundTag(), new CompoundTag())
                    : SuspendedLeggingsData.of(new CompoundTag(), new CompoundTag(), new CompoundTag()));
            negatives.add(suspended);
        }
        negatives.addAll(FoundationChecks.paid(player, new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS)));
        negatives.addAll(FoundationChecks.paid(player, FoundationChecks.armor("iron"), new ItemStack(Items.DIAMOND_LEGGINGS)));
        for (var root : negatives) {
            check(player, root, ItemStack.EMPTY, EquipmentSlot.LEGS, threshold - .5, false);
        }
        // Invalid items are ordinary negatives, not permanent failures: the positive must still work.
        check(player, roots.getFirst(), ItemStack.EMPTY, EquipmentSlot.LEGS, threshold - .5, enabled);

        var nativeFeet = nativePiece("boots", "boot_plates", "tyrian_steel");
        check(player, roots.getFirst(), nativeFeet, EquipmentSlot.LEGS, threshold - .5, true);
        if (boots) {
            var mixedFeet = mixedFeet(nativeFeet);
            check(player, new ItemStack(Items.DIAMOND_LEGGINGS), mixedFeet, EquipmentSlot.LEGS, threshold - .5, true);
            for (var root : roots) {
                check(player, root, mixedFeet, EquipmentSlot.LEGS, threshold - .5, true);
            }
        }
        require(ItemStack.matches(sourceBefore, source), "Paid composition/rescue changed native source history");
        clear(player);
        player.getInventory().clearContent();
        player.removeAllEffects();
        player.setPos(0, 65, 0);
        report.append("Void Ward production: native Tyrian Steel I, paid inverses/duplicates, strict height boundary, worn/held/idle and malformed/suspended/no-trait controls; ")
                .append("two consecutive qualifying native events each push once with exact native potions; roots unchanged; native/Boots peers preserved. variant=")
                .append(variant).append("\nActual Void Ward order: ").append(VoidWardTestPlugin.actualOrder).append('\n');
    }

    private static void check(ServerPlayer player, ItemStack root, ItemStack feet, EquipmentSlot slot, double y, boolean active) {
        clear(player);
        player.getInventory().clearContent();
        player.removeAllEffects();
        player.setItemSlot(EquipmentSlot.FEET, feet);
        if (slot == null) {
            player.getInventory().setItem(8, root);
        } else {
            player.setItemSlot(slot, root);
        }
        player.setPos(8.5, y, 8.5);
        player.addEffect(new MobEffectInstance(MobEffects.LUCK, 100));
        var before = root.copy();
        var beforeFeet = feet.copy();
        watched = player;
        pushes = 0;
        try {
            for (int tick = 1; tick <= 2; tick++) {
                NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
                require(pushes == (active ? tick : 0) && player.hasEffect(MobEffects.LEVITATION) == active
                        && player.hasEffect(MobEffects.SLOW_FALLING) == active, "Void Ward event differs slot=" + slot + " y=" + y + " tick=" + tick);
                require(player.getDeltaMovement().y == (active ? tick * 20 : 0), "Void Ward duplicated/replaced native additive push");
                if (active) {
                    require(player.getEffect(MobEffects.SLOW_FALLING).getDuration() == 400
                            && player.getEffect(MobEffects.SLOW_FALLING).getAmplifier() == 3
                            && player.getEffect(MobEffects.LEVITATION).getDuration() == 200
                            && player.getEffect(MobEffects.LEVITATION).getAmplifier() == 9, "Native exact potion parameters changed");
                }
                require(player.hasEffect(MobEffects.LUCK) && ItemStack.matches(before, root) && ItemStack.matches(beforeFeet, feet),
                        "Rescue changed unrelated effects, roots or source history");
            }
        } finally {
            watched = null;
        }
    }

    private static boolean proof(String owner, String name) throws Exception {
        var method = Class.forName(owner).getDeclaredMethod(name);
        method.setAccessible(true);
        return (boolean) method.invoke(null);
    }

    private static ItemStack mixedFeet(ItemStack source) throws Exception {
        var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
        var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, source, new ItemStack(Items.DIAMOND_BOOTS));
        return (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
    }
}
