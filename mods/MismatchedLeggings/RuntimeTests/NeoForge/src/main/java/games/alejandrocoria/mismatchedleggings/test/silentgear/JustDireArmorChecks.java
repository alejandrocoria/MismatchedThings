package games.alejandrocoria.mismatchedleggings.test.silentgear;

import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearCompatibility;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearState;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.test.JustDirePassiveChecks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.silentchaos512.gear.setup.SgDataComponents;
import net.silentchaos512.gear.util.GearData;

import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.JustDirePassiveChecks.close;
import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Only the shared live armor boundary; not another run of SG's trait/release matrix. */
public final class JustDireArmorChecks {
    private JustDireArmorChecks() {}

    public static void run(ServerPlayer player) throws Exception {
        for (String material : List.of("bone", "copper")) {
            var nativeSg = FoundationChecks.armor(material);
            if (material.equals("copper")) { nativeSg.set(SgDataComponents.OXIDATION_COUNTER.get(), 1199); }
            var jdt = JustDirePassiveChecks.nativeStack("eclipsealloy");
            jdt.set(JustDireDataComponents.FORGE_ENERGY, 100);
            for (var root : FoundationChecks.paid(player, jdt, nativeSg)) {
                var history = root.get(LeggingsContent.mixedLeggingsDataType());
                var control = nativeSg.copy();
                root.setDamageValue(root.getMaxDamage() / 2);
                control.setDamageValue((int) ((long) root.getDamageValue() * control.getMaxDamage() / root.getMaxDamage()));
                player.tickCount = 20;
                control.inventoryTick(player.level(), player, null);
                player.tickCount = 21;
                control.inventoryTick(player.level(), player, null);
                GearData.recalculateGearData(control, null);
                player.tickCount = 20;
                root.inventoryTick(player.level(), player, null);
                for (int charge : new int[]{0, 100, 0}) {
                    root.set(JustDireDataComponents.FORGE_ENERGY, charge);
                    var nativeJdt = jdt.copy();
                    nativeJdt.set(JustDireDataComponents.FORGE_ENERGY, charge);
                    double expected = (JustDirePassiveChecks.amount(control.getAttributeModifiers(), Attributes.ARMOR)
                            + JustDirePassiveChecks.amount(nativeJdt.getAttributeModifiers(), Attributes.ARMOR)) / 2;
                    close(JustDirePassiveChecks.amount(root.getAttributeModifiers(), Attributes.ARMOR), expected, "Coordinated SG/JDT live armor " + material + '/' + charge);
                    require(!root.get(SilentGearCompatibility.state()).armorOverridden(), "JDT state was mistaken for a foreign SG override");
                }
                require(history.equals(root.get(LeggingsContent.mixedLeggingsDataType())), "Shared refresh rewrote sources");
                var entries = new ArrayList<>(root.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream().filter(entry -> !SilentGearState.managed(entry)).toList());
                entries.add(new ItemAttributeModifiers.Entry(Attributes.ARMOR, new AttributeModifier(SilentGearState.MODIFIER, 99,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.LEGS));
                root.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(entries));
                player.tickCount = 21;
                root.inventoryTick(player.level(), player, EquipmentSlot.LEGS);
                root.set(JustDireDataComponents.FORGE_ENERGY, 100);
                close(JustDirePassiveChecks.amount(root.getAttributeModifiers(), Attributes.ARMOR), 99, "JDT reclaimed foreign shared armor");
                require(root.get(SilentGearCompatibility.state()).armorOverridden(), "SG failed to retain foreign shared override");
            }
        }
        JustDirePassiveChecks.note("shared-armor.txt", "Paid SG/JDT inverse roots: native damage/oxidation control, changed root FE and explicit shared armor override passed\n");
    }
}
