package games.alejandrocoria.mismatchedleggings.test.silentgear;

import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedleggings.data.MixedLeggingsData;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import games.alejandrocoria.mismatchedleggings.test.WorkbenchChecks;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.silentchaos512.gear.api.item.GearItem;
import net.silentchaos512.gear.api.traits.TraitInstance;
import net.silentchaos512.gear.api.util.DataResource;
import net.silentchaos512.gear.gear.part.PartInstance;
import net.silentchaos512.gear.setup.SgDataComponents;
import net.silentchaos512.gear.util.GearData;
import net.silentchaos512.gear.util.TraitHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Real optional material graph and paid roots from both packaged products, without research hooks. */
public final class AtmMaterialChecks {
    private static boolean finished;

    private AtmMaterialChecks() {}

    public static void tick(MinecraftServer server) {
        if (finished || server.getTickCount() < 25) { return; }
        finished = true;
        var report = new StringBuilder();
        try {
            for (String mod : List.of("mismatchedboots", "mismatchedleggings", "mismatchedthings", "atmcompat", "allthemodium", "silentgear")) {
                require(ModList.get().isLoaded(mod), "Missing actual material graph member: " + mod);
            }
            var player = FoundationChecks.player(server);
            String stage = System.getProperty("leggings.test.sgAtmStage", "materials");
            if (stage.equals("materials")) {
                CureDefinitionChecks.run(List.of("nausea", "levitation"));
                materials(server, player, report);
                naturalCures(player, report);
                ordinary(player, report);
                refusal(player, report);
            } else {
                AtmPersistenceChecks.run(server, player, stage, report);
            }
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "materials.txt"), report);
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            SkeletonChecks.failed("server", failure);
        } finally {
            server.halt(false);
        }
    }

    private static void materials(MinecraftServer server, ServerPlayer player, StringBuilder report) throws Exception {
        for (String material : List.of("allthemodium", "vibranium", "unobtainium")) {
            var plate = BuiltInRegistries.ITEM.getValue(Identifier.parse("allthemodium:silent_" + material + "_plate"));
            require(plate != null && plate != Items.AIR, "Missing actual Silent plate: " + material);
            for (var slot : List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS)) {
                var source = armor(material, slot);
                require(source.has(SgDataComponents.GEAR_PROPERTIES.get()) && source.has(SgDataComponents.GEAR_CONSTRUCTION.get()),
                        "Native construction did not populate parts/properties");
                var traits = TraitHelper.getTraits(source).stream().map(t -> t.getTraitId().toString()).toList();
                require(traits.containsAll(List.of("silentgear:brilliant", "silentgear:malleable", "silentgear:crushing")),
                        "Wrong acquired material traits: " + material + ' ' + traits);
                boolean cures = slot == EquipmentSlot.LEGS && !material.equals("allthemodium");
                require(traits.contains("silentgear:cure_wither") == cures && traits.contains("silentgear:cure_nausea") == cures
                                && traits.contains("silentgear:cure_levitation") == (slot == EquipmentSlot.LEGS && material.equals("unobtainium")),
                        "Changed native slot/material cure assignment");
                var original = source.copy();
                var vanilla = new ItemStack(slot == EquipmentSlot.FEET ? Items.DIAMOND_BOOTS : Items.DIAMOND_LEGGINGS);
                var roots = paid(player, source, vanilla, slot);
                double nativeArmor = worn(player, source, slot, Attributes.ARMOR);
                double nativeToughness = worn(player, source, slot, Attributes.ARMOR_TOUGHNESS);
                double vanillaArmor = worn(player, vanilla, slot, Attributes.ARMOR);
                double vanillaToughness = worn(player, vanilla, slot, Attributes.ARMOR_TOUGHNESS);
                for (var root : roots) {
                    require(root.getMaxDamage() == ((long) source.getMaxDamage() + vanilla.getMaxDamage()) / 2,
                            "Material durability no longer follows the ordinary average");
                    close(worn(player, root, slot, Attributes.ARMOR), (nativeArmor + vanillaArmor) / 2, "Effective material armor");
                    close(worn(player, root, slot, Attributes.ARMOR_TOUGHNESS), (nativeToughness + vanillaToughness) / 2, "Effective toughness");
                    require(PiglinAi.isWearingSafeArmor(player), "Actual SG Brilliant missing on mixed " + slot);
                    var provenance = sources(root);
                    root.setDamageValue(root.getMaxDamage() / 2);
                    player.tickCount = 21;
                    root.inventoryTick(player.level(), player, slot);
                    var projected = original.copy();
                    projected.setDamageValue((int) ((long) root.getDamageValue() * original.getMaxDamage() / root.getMaxDamage()));
                    GearData.recalculateGearData(projected, player);
                    double changedArmor = worn(player, projected, slot, Attributes.ARMOR);
                    require(Math.abs(changedArmor - nativeArmor) > 1E-6, "Native Crushing positive control did not change armor");
                    close(worn(player, root, slot, Attributes.ARMOR), (changedArmor + vanillaArmor) / 2, "Native Crushing projection");
                    var after = sources(root);
                    require(ItemStack.matches(provenance.getFirst(), after.getFirst()) && ItemStack.matches(provenance.getLast(), after.getLast()),
                            "Live projection changed source provenance");
                    var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
                    require(ItemStack.matches(root, ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, root).getOrThrow()).getOrThrow()),
                            "Material/root item codec lost state");
                }
                require(ItemStack.matches(source, original), "Paid material composition/query changed its input");
                report.append("Material ").append(material).append('/').append(slot)
                        .append(": two paid orientations/companion return; native traits; average attributes/durability; nonzero Crushing; Brilliant; immutable sources; codec\n");
            }
        }
    }

    private static void naturalCures(ServerPlayer player, StringBuilder report) throws Exception {
        for (String material : List.of("allthemodium", "vibranium", "unobtainium")) {
            var source = armor(material, EquipmentSlot.LEGS);
            var effects = material.equals("allthemodium") ? List.<Holder<MobEffect>>of()
                    : material.equals("vibranium") ? List.of(MobEffects.WITHER, MobEffects.NAUSEA)
                    : List.of(MobEffects.WITHER, MobEffects.NAUSEA, MobEffects.LEVITATION);
            checkCures(player, source, EquipmentSlot.LEGS, effects);
            var roots = FoundationChecks.paid(player, source, new ItemStack(Items.DIAMOND_LEGGINGS));
            for (var root : roots) {
                for (var slot : List.of(EquipmentSlot.LEGS, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
                    checkCures(player, root, slot, effects);
                }
                checkCures(player, root, null, List.of());
            }
        }
        // Overlapping acquisitions must not change union semantics in either source order.
        for (var root : FoundationChecks.paid(player, armor("vibranium", EquipmentSlot.LEGS), armor("unobtainium", EquipmentSlot.LEGS))) {
            checkCures(player, root, EquipmentSlot.LEGS, List.of(MobEffects.WITHER, MobEffects.NAUSEA, MobEffects.LEVITATION));
        }
        report.append("Natural cures: native/mixed acquisition equality; two orientations; each hand/worn/inactive; SG/SG union; Poison/Luck and complete stacks preserved\n");
    }

    private static void ordinary(ServerPlayer player, StringBuilder report) throws Exception {
        worn(player, armor("gold", EquipmentSlot.LEGS), EquipmentSlot.LEGS, Attributes.ARMOR);
        require(PiglinAi.isWearingSafeArmor(player), "Ordinary SG gold Brilliant regressed");
        var nativeAtm = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("allthemodium:allthemodium_leggings")));
        worn(player, nativeAtm, EquipmentSlot.LEGS, Attributes.ARMOR);
        require(PiglinAi.isWearingSafeArmor(player), "Native ATM piglin control regressed");
        report.append("Independent native controls: ordinary SG gold and native ATM remain piglin-positive\n");
    }

    private static void refusal(ServerPlayer player, StringBuilder report) throws Exception {
        String variant = System.getProperty("leggings.test.sgAtmVariant", "normal");
        if (variant.equals("normal")) { return; }
        var root = FoundationChecks.paid(player, armor("unobtainium", EquipmentSlot.LEGS), new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
        if (variant.equals("old-targets")) {
            var source = DefenseIncomingChecks.only(FoundationChecks.armor("iron"),
                    DefenseIncomingChecks.trait("cure_poison", 1), DefenseIncomingChecks.trait("cure_wither", 1));
            var legacy = FoundationChecks.paid(player, source, new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
            withChangedTarget("poison", () -> withChangedTarget("wither", () -> {
                checkCures(player, legacy, EquipmentSlot.LEGS, List.of());
                checkCures(player, legacy, EquipmentSlot.LEGS, List.of());
            }));
            // Both old lanes are now disabled: an obsolete two-lane early check would suppress these positives.
            checkCures(player, root, EquipmentSlot.LEGS, List.of(MobEffects.NAUSEA, MobEffects.LEVITATION));
            checkCures(player, root, EquipmentSlot.LEGS, List.of(MobEffects.NAUSEA, MobEffects.LEVITATION));
        } else {
            String target = variant.equals("nausea-target") ? "nausea" : "levitation";
            var healthy = variant.equals("nausea-target") ? List.of(MobEffects.WITHER, MobEffects.LEVITATION)
                    : List.of(MobEffects.WITHER, MobEffects.NAUSEA);
            withChangedTarget(target, () -> {
                checkCures(player, root, EquipmentSlot.LEGS, healthy);
                checkCures(player, root, EquipmentSlot.LEGS, healthy);
            });
            checkCures(player, root, EquipmentSlot.LEGS, healthy);
        }
        report.append("Local refusal ").append(variant).append(": exact target; healthy lanes independent; restored definition does not replay; unchanged Luck/root\n");
    }

    private static void withChangedTarget(String target, Runnable check) {
        Object cancellation = TraitInstance.of(DataResource.trait("silentgear:cure_" + target), 1).getTrait().getEffects().getFirst();
        try {
            var field = cancellation.getClass().getDeclaredField("effects");
            field.setAccessible(true);
            Object original = field.get(cancellation);
            try {
                field.set(cancellation, List.of(MobEffects.LUCK));
                check.run();
            } finally { field.set(cancellation, original); }
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Native cancellation target fixture failed", failure);
        }
    }

    private static void checkCures(ServerPlayer player, ItemStack stack, EquipmentSlot slot, List<Holder<MobEffect>> removed) {
        clear(player);
        var effects = List.of(MobEffects.WITHER, MobEffects.NAUSEA, MobEffects.LEVITATION, MobEffects.POISON, MobEffects.LUCK);
        for (var effect : effects) {
            require(player.addEffect(new MobEffectInstance(effect, 100)), "Potion positive setup failed");
        }
        if (slot == null) { player.getInventory().setItem(8, stack); }
        else { player.setItemSlot(slot, stack); }
        var before = stack.copy();
        player.tickCount = 11;
        stack.inventoryTick(player.level(), player, slot);
        for (var effect : effects) {
            require(player.hasEffect(effect) != removed.contains(effect), "Material cure mismatch: " + effect + " in " + slot);
        }
        require(ItemStack.matches(before, stack), "Cure changed complete native/root state");
    }

    static ItemStack armor(String material, EquipmentSlot slot) throws Exception {
        if (slot == EquipmentSlot.LEGS) { return FoundationChecks.armor(material); }
        return ((GearItem) BuiltInRegistries.ITEM.getValue(Identifier.parse("silentgear:boots"))).construct(
                List.of(PartInstance.from(FoundationChecks.part("boot_plates", material))));
    }

    static List<ItemStack> paid(ServerPlayer player, ItemStack source, ItemStack vanilla, EquipmentSlot slot) {
        if (slot == EquipmentSlot.LEGS) { return FoundationChecks.paid(player, source, vanilla); }
        var pair = MixedBootsFactory.createPair(source, vanilla);
        var roots = List.of(pair.primary(), pair.inverse());
        for (int selected : List.of(2, 3)) {
            clear(player);
            var menu = new WorkbenchMenu(402, player.getInventory());
            menu.getSlot(0).set(source.copy());
            menu.getSlot(1).set(vanilla.copy());
            menu.slotsChanged(menu.getSlot(0).container);
            require(ItemStack.matches(menu.getSlot(2).getItem(), roots.getFirst()) && ItemStack.matches(menu.getSlot(3).getItem(), roots.getLast()),
                    "Boot material preview differs from its factory");
            menu.clicked(selected, 0, ContainerInput.PICKUP, player);
            require(ItemStack.matches(menu.getCarried(), roots.get(selected - 2)) && menu.getSlot(0).getItem().isEmpty()
                    && menu.getSlot(1).getItem().isEmpty(), "Boot material paid extraction/conservation failed");
            menu.setCarried(ItemStack.EMPTY);
            menu.removed(player);
            require(WorkbenchChecks.inventory(player).size() == 1 && WorkbenchChecks.contains(player, roots.get(selected == 2 ? 1 : 0)),
                    "Boot material closing lost or duplicated its paid companion");
        }
        clear(player);
        return roots;
    }

    private static List<ItemStack> sources(ItemStack root) {
        for (var component : root.getComponents()) {
            if (component.value() instanceof MixedBootsData data) { return List.of(data.left().source(), data.right().source()); }
            if (component.value() instanceof MixedLeggingsData data) { return List.of(data.left().source(), data.right().source()); }
        }
        throw new AssertionError("Missing actual mixed provenance");
    }

    private static double worn(ServerPlayer player, ItemStack stack, EquipmentSlot slot, Holder<Attribute> attribute) throws Exception {
        clear(player);
        player.setItemSlot(slot, stack);
        var detect = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates");
        detect.setAccessible(true);
        detect.invoke(player);
        // These materials exceed vanilla caps; compare installed contributions, not clamped display values.
        return player.getAttribute(attribute).getModifiers().stream()
                .filter(modifier -> modifier.operation() == AttributeModifier.Operation.ADD_VALUE).mapToDouble(AttributeModifier::amount).sum();
    }

    private static void clear(ServerPlayer player) {
        for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
        player.getInventory().clearContent();
        player.removeAllEffects();
    }

    private static void close(double actual, double expected, String label) {
        require(Math.abs(actual - expected) < 1E-6, label + ": " + actual + " != " + expected);
    }
}
