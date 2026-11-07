package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.test.mixin.EquipmentUpdatesInvoker;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Dedicated-server equipment attributes and slot boundaries, not client component previews. */
final class OritechGameplayChecks {
    private static boolean verified;

    private OritechGameplayChecks() {
    }

    static void tick(MinecraftServer server) {
        if (!server.isDedicatedServer() || server.getTickCount() < 20) {
            return;
        }
        if (!verified) {
            verified = true;
            try {
                verify(server);
            } catch (Throwable failure) {
                SkeletonChecks.failed("server", failure);
                server.halt(false);
                return;
            }
        }
        // Retain the same packaged workbench/world setup used by the client profiles.
        SkeletonChecks.serverTick(server);
    }

    private static void verify(MinecraftServer server) throws Exception {
        var source = nativeItem("exo_leggings");
        var before = source.copy();
        var player = RepairFixtures.player(server);
        double movement = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        // Vanilla players do not register flying_speed: even native Exo's modifier does
        // not control creative/Elytra flight. Preserve its declaration, not an invented effect.
        require(!player.getAttributes().hasAttribute(Attributes.FLYING_SPEED), "Player flying-speed contract changed; revisit native parity");
        wear(player, source);
        double nativeArmor = player.getAttributeValue(Attributes.ARMOR);
        close(player.getAttributeValue(Attributes.MOVEMENT_SPEED), movement * 1.2, "Native movement control");
        flyingModifier(source, 0.2);
        wear(player, new ItemStack(Items.IRON_LEGGINGS));
        double ironArmor = player.getAttributeValue(Attributes.ARMOR);

        var pair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.IRON_LEGGINGS), CompositionRules.defaults());
        var both = MixedLeggingsFactory.createPair(source, source, CompositionRules.defaults()).primary();
        for (var root : List.of(pair.primary(), pair.inverse(), both)) {
            var original = root.copy();
            wear(player, root);
            double multiplier = root == both ? 1.2 : 1.1;
            close(player.getAttributeValue(Attributes.MOVEMENT_SPEED), movement * multiplier, "Mixed movement attribute");
            flyingModifier(root, root == both ? 0.2 : 0.1);
            close(player.getAttributeValue(Attributes.ARMOR), root == both ? nativeArmor : (nativeArmor + ironArmor) / 2,
                    "Mixed equipped armor");
            require(root.has(DataComponents.UNBREAKABLE) == (root == both), "Wrong unbreakability policy");
            require(root.get(DataComponents.EQUIPPABLE).equipSound().value().location().toString().equals("oritech:short_servo"),
                    "Servo sound not selected in both orientations");
            for (int tick = 0; tick < 20; tick++) {
                root.getItem().inventoryTick(root, player.level(), player, EquipmentSlot.LEGS);
            }
            require(!player.hasEffect(MobEffects.NIGHT_VISION), "Leggings gained the helmet-only ability");
            fall(player, false);
            require(ItemStack.matches(root, original), "Equipped checks changed root/source snapshots");
        }
        wear(player, ItemStack.EMPTY);
        close(player.getAttributeValue(Attributes.MOVEMENT_SPEED), movement, "Unequipped movement");

        // Use actual damage dispatch, as Boots' maintained Oritech test does.
        player.setItemSlot(EquipmentSlot.FEET, nativeItem("exo_boots"));
        fall(player, true);
        if (Boolean.getBoolean("leggings.test.boots")) {
            player.setItemSlot(EquipmentSlot.FEET, BootsCoexistence.pair(nativeItem("exo_boots"), new ItemStack(Items.IRON_BOOTS)).getFirst());
            wear(player, pair.inverse());
            fall(player, true);
        }
        require(ItemStack.matches(source, before), "Native input changed");
        Files.writeString(Path.of(System.getProperty("leggings.test.report"), "oritech-gameplay.txt"),
                "equipped.armorMovementAndUnequip=true\nflying.nativeModifierPreservedNotPlayerFlight=true\ncomposition.soundUnbreakabilityNonMutation=true\n"
                        + "slots.noHelmetOrFeetAbilities=true\n");
    }

    private static ItemStack nativeItem(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("oritech:" + name)));
    }

    private static void wear(ServerPlayer player, ItemStack stack) {
        player.setItemSlot(EquipmentSlot.LEGS, stack);
        ((EquipmentUpdatesInvoker) player).leggings$detectEquipmentUpdates();
    }

    private static void fall(ServerPlayer player, boolean expected) {
        // A fresh disconnected ServerPlayer still has login damage protection. Use the
        // same native zombie subject as Boots, not a false-positive invulnerable player.
        var subject = new Zombie(EntityType.ZOMBIE, player.level());
        subject.setItemSlot(EquipmentSlot.LEGS, player.getItemBySlot(EquipmentSlot.LEGS).copy());
        subject.setItemSlot(EquipmentSlot.FEET, player.getItemBySlot(EquipmentSlot.FEET).copy());
        float before = subject.getHealth();
        subject.causeFallDamage(9, 1, player.level().damageSources().fall());
        require((subject.getHealth() == before) == expected, "Leggings changed fall protection or displaced the native Boots control");
    }

    private static void close(double actual, double expected, String description) {
        require(Math.abs(actual - expected) < 0.000001, description + ": " + actual + " != " + expected);
    }

    private static void flyingModifier(ItemStack stack, double expected) {
        var modifiers = stack.getAttributeModifiers().modifiers().stream()
                .filter(entry -> entry.attribute().equals(Attributes.FLYING_SPEED)).toList();
        require(modifiers.size() == 1, "Missing/duplicated native flying-speed declaration");
        var entry = modifiers.getFirst();
        close(entry.modifier().amount(), expected, "Flying-speed modifier policy");
        require(entry.modifier().operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                        && entry.slot() == EquipmentSlotGroup.LEGS,
                "Flying-speed operation or slot changed");
    }
}
