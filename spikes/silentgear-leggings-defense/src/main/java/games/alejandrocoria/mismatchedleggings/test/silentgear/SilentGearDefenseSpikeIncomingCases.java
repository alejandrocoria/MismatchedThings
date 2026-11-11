package games.alejandrocoria.mismatchedleggings.test.silentgear;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.silentchaos512.gear.api.item.GearItem;
import net.silentchaos512.gear.api.property.TraitListPropertyValue;
import net.silentchaos512.gear.api.traits.TraitInstance;
import net.silentchaos512.gear.api.util.DataResource;
import net.silentchaos512.gear.core.component.GearPropertiesData;
import net.silentchaos512.gear.gear.part.PartInstance;
import net.silentchaos512.gear.gear.trait.effect.NegateDamageTraitEffect;
import net.silentchaos512.gear.setup.SgDataComponents;
import net.silentchaos512.gear.setup.gear.GearProperties;
import net.silentchaos512.gear.util.TraitHelper;

import java.util.HashMap;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Extra LEGS measurements; the existing Boots suite is not treated as evidence for this candidate. */
final class SilentGearDefenseSpikeIncomingCases {
    static ItemStack only(ItemStack source, TraitInstance... traits) {
        var copy = source.copy();
        var map = new HashMap<>(copy.get(SgDataComponents.GEAR_PROPERTIES.get()).properties());
        map.put(GearProperties.TRAITS.get(), new TraitListPropertyValue(List.of(traits)));
        copy.set(SgDataComponents.GEAR_PROPERTIES.get(), new GearPropertiesData(map));
        return copy;
    }

    static TraitInstance trait(String id, int level) { return TraitInstance.of(DataResource.trait("silentgear:" + id), level); }

    static ItemStack nativePiece(String item, String part, String material) throws Exception {
        return ((GearItem) BuiltInRegistries.ITEM.getValue(Identifier.parse("silentgear:" + item)))
                .construct(List.of(PartInstance.from(FoundationChecks.part(part, material))));
    }

    static void clear(ServerPlayer player) {
        for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
        player.setShiftKeyDown(false);
        player.stopFallFlying();
        player.setDeltaMovement(0, 0, 0);
    }

    static void run(MinecraftServer server, StringBuilder report) throws Exception {
        var player = FoundationChecks.player(server);
        var source = only(FoundationChecks.armor("crimson_iron"), trait("heat_resistant", 4), trait("bounce", 1));
        var nativeBefore = source.copy();
        var roots = new java.util.ArrayList<>(FoundationChecks.paid(player, source, new ItemStack(Items.DIAMOND_LEGGINGS)));
        roots.addAll(FoundationChecks.paid(player, source, source));
        var helmet = only(nativePiece("helmet", "helmet_plates", "crimson_iron"), trait("heat_resistant", 4));
        for (var root : roots) {
            clear(player);
            player.setItemSlot(EquipmentSlot.LEGS, root);
            var before = root.copy();
            hit(player, player.damageSources().lava(), 20, 20, 16.8F, 2);
            player.setItemSlot(EquipmentSlot.HEAD, helmet.copy());
            hit(player, player.damageSources().lava(), 20, 20, 13.6F, 2);
            hit(player, player.damageSources().lava(), 20, 8, 5.44F, 2);
            require(ItemStack.matches(before, root), "Multi-piece incoming changed source history");
        }
        var unequal = FoundationChecks.paid(player, only(source, trait("heat_resistant", 1)), only(source, trait("heat_resistant", 5)));
        for (var root : unequal) {
            clear(player);
            player.setItemSlot(EquipmentSlot.LEGS, root);
            hit(player, player.damageSources().lava(), 20, 20, 16, 1);
        }
        var effect = TraitHelper.getTraits(source).stream().filter(t -> t.getTraitId().getPath().equals("bounce")).findFirst().orElseThrow()
                .getTrait().getEffects().stream().filter(e -> e.getClass() == NegateDamageTraitEffect.class).findFirst().orElseThrow();
        var tag = effect.getClass().getDeclaredField("damageType");
        tag.setAccessible(true);
        var previous = tag.get(effect);
        try {
            tag.set(effect, DamageTypeTags.IS_FIRE);
            clear(player);
            player.setItemSlot(EquipmentSlot.LEGS, roots.getFirst());
            hit(player, player.damageSources().lava(), 20, 20, 12.8F, 2);
        } finally { tag.set(effect, previous); }
        var heatEffect = TraitHelper.getTraits(source).stream().filter(t -> t.getTraitId().getPath().equals("heat_resistant")).findFirst().orElseThrow()
                .getTrait().getEffects().stream().filter(e -> e.getClass() == NegateDamageTraitEffect.class).findFirst().orElseThrow();
        var scale = heatEffect.getClass().getDeclaredField("negatedDamageScale");
        scale.setAccessible(true);
        float oldScale = scale.getFloat(heatEffect);
        try {
            scale.setFloat(heatEffect, .5F);
            require(hit(player, player.damageSources().lava(), 20, 20, 0, 2).isCanceled(), "Native zero clamp did not cancel positive hit");
            require(!hit(player, player.damageSources().lava(), 0, 0, 0, 2).isCanceled(), "Zero original damage was canceled");
        } finally { scale.setFloat(heatEffect, oldScale); }

        var attacker = FoundationChecks.player(server);
        var sword = only(nativePiece("sword", "sword_blade", "iron"), trait("adamant", 2));
        attacker.setItemSlot(EquipmentSlot.MAINHAND, sword);
        var swordBefore = sword.copy();
        var maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        double oldHealth = maxHealth.getBaseValue();
        try {
            maxHealth.setBaseValue(40);
            player.setHealth(player.getMaxHealth());
            var fireAttack = new DamageSource(player.damageSources().lava().typeHolder(), attacker);
            clear(player);
            hit(player, fireAttack, 20, 20, 24, 0);
            player.setItemSlot(EquipmentSlot.LEGS, roots.getFirst());
            player.setItemSlot(EquipmentSlot.HEAD, helmet);
            hit(player, fireAttack, 20, 20, 24 * .68F, 2);
        } finally { maxHealth.setBaseValue(oldHealth); }
        require(ItemStack.matches(swordBefore, sword) && ItemStack.matches(nativeBefore, source), "Offensive branch/source fixture changed");

        clear(player);
        var root = roots.getFirst();
        player.setItemSlot(EquipmentSlot.LEGS, root);
        var canceled = new LivingIncomingDamageEvent(player, new DamageContainer(player.damageSources().lava(), 20));
        canceled.setCanceled(true);
        SilentGearDefenseSpike.calls = 0;
        NeoForge.EVENT_BUS.post(canceled);
        require(canceled.getAmount() == 20 && SilentGearDefenseSpike.calls == 0, "Pre-canceled hit gained candidate work");
        for (String invalid : List.of("count", "slot", "data")) {
            var bad = root.copy();
            if (invalid.equals("count")) { bad.setCount(2); }
            else if (invalid.equals("slot")) { bad.remove(DataComponents.EQUIPPABLE); }
            else { bad.remove(games.alejandrocoria.mismatchedleggings.content.LeggingsContent.mixedLeggingsDataType()); }
            player.setItemSlot(EquipmentSlot.LEGS, bad);
            hit(player, player.damageSources().lava(), 20, 20, 20, 0);
        }
        clear(player);
        player.setItemSlot(EquipmentSlot.MAINHAND, root);
        hit(player, player.damageSources().lava(), 20, 20, 20, 0);
        var mob = new Zombie(EntityType.ZOMBIE, player.level());
        mob.setItemSlot(EquipmentSlot.LEGS, root.copy());
        var nonPlayer = new LivingIncomingDamageEvent(mob, new DamageContainer(mob.damageSources().lava(), 20));
        SilentGearDefenseSpike.calls = 0;
        NeoForge.EVENT_BUS.post(nonPlayer);
        require(Math.abs(nonPlayer.getAmount() - 16.8F) < .0001 && SilentGearDefenseSpike.calls == 2, "Native living-actor incoming scope was restricted to players");
        report.append("LEGS incoming: paid duplicates/inverses, fixture Heat I/V max, multiple native pieces, changed entering/common base, overlapping live tags, real Adamant attack, canceled/held/malformed negatives; fixed native leaf counts and unchanged snapshots passed\n");
    }

    private static LivingIncomingDamageEvent hit(ServerPlayer player, DamageSource source, float original, float entering, float expected, int count) {
        var event = new LivingIncomingDamageEvent(player, new DamageContainer(source, original));
        event.setAmount(entering);
        SilentGearDefenseSpike.calls = 0;
        NeoForge.EVENT_BUS.post(event);
        require(Math.abs(event.getAmount() - expected) < .0001 && SilentGearDefenseSpike.calls == count,
                "Incoming amount/calls " + event.getAmount() + "/" + SilentGearDefenseSpike.calls + " expected " + expected + "/" + count);
        return event;
    }
}
