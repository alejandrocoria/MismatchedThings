package games.alejandrocoria.mismatchedleggings.test.silentgear;

import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearCompatibility;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;

import java.util.List;
import java.util.Random;

/** Production inventory/wear dispatch, native controls and independent seeded probability oracle. */
public final class EffectsChecks {
    private static boolean observing;
    private static int recalculations;
    private static Random random;

    private EffectsChecks() {
    }

    static void run(ServerPlayer player, StringBuilder report) throws Exception {
        clear(player);
        var field = Class.forName("net.silentchaos512.lib.util.MathUtils").getDeclaredField("RANDOM");
        field.setAccessible(true);
        random = (Random) field.get(null);
        ItemStack calibration = FoundationChecks.armor("iron");
        boolean witnessed = false;
        observing = true;
        try {
            nativeClass("util.GearData").getMethod("recalculateGearData", ItemStack.class, net.minecraft.world.entity.player.Player.class)
                                        .invoke(null, calibration, player);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            witnessed = failure.getCause() instanceof AssertionError && recalculations == 1;
        } finally {
            observing = false;
        }
        require(witnessed, "Production recalculation witness did not intercept its positive control");
        recalculations = 0;
        String variant = System.getProperty("leggings.test.sgEffectsVariant", "");
        if (!variant.isEmpty()) {
            for (String name : List.of("SelfRepairTraitEffect", "DurabilityTraitEffect")) {
                require(Boolean.TRUE.equals(nativeClass("gear.trait.effect." + name).getMethod("sgtest$modified").invoke(null)),
                        "Competing native modification was not installed");
            }
            if (!variant.equals("compatible")) {
                refusal(player, variant, report);
                if (!variant.equals("wear-delta")) { sibling(player, report); }
                return;
            }
        }
        require(EffectsTestPlugin.actualOrder != null, "No actual transformation-order witness");
        report.append("B actual repair mixin order ").append(EffectsTestPlugin.actualOrder).append('\n');
        report.append("\nPRODUCTION B: variant=").append(variant.isEmpty() ? "live" : variant).append('\n');
        for (String[] sample : List.of(new String[]{"amethyst", "renew"}, new String[]{"refined_iron", "stellar"})) {
            ItemStack source = FoundationChecks.armor(sample[0]);
            Object instance = view(source).get("silentgear:" + sample[1]);
            Object effect = effect(instance, "SelfRepairTraitEffect");
            float chance = chance(effect) * level(instance);
            for (boolean activate : List.of(true, false)) {
                long seed = seed(chance, activate);
                ItemStack nativeBoot = source.copy();
                nativeBoot.setDamageValue(12);
                random.setSeed(seed);
                nativeBoot.inventoryTick(player.level(), player, null);
                require(nativeBoot.getDamageValue() == (activate ? 12 - level(instance) : 12), "Native repair amount/probability differs");
                for (ItemStack root : pair(player, source, new ItemStack(Items.DIAMOND_LEGGINGS))) {
                    root.setDamageValue(12);
                    ItemStack before = root.copy();
                    random.setSeed(seed);
                    observe(() -> root.inventoryTick(player.level(), player, null));
                    require(root.getDamageValue() == nativeBoot.getDamageValue(), "Production repair differs from native control");
                    unchanged(before, root);
                }
            }
            for (String mode : List.of("duplicate", "equipped", "healthy", "creative", "unbreakable", "wrong-tick", "no-state")) {
                ItemStack root = pair(player, source, mode.equals("duplicate") ? source : new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
                root.setDamageValue(mode.equals("healthy") ? 0 : 12);
                player.setGameMode(mode.equals("creative") ? GameType.CREATIVE : GameType.SURVIVAL);
                player.tickCount = mode.equals("wrong-tick") ? 21 : 20;
                if (mode.equals("unbreakable")) {
                    root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
                }
                if (mode.equals("no-state")) {
                    root.remove(SilentGearCompatibility.state());
                }

                ItemStack before = root.copy();
                random.setSeed(seed(chance, true));
                observe(() -> root.inventoryTick(player.level(), player,
                                                 mode.equals("equipped") ? EquipmentSlot.LEGS : null));
                int expected = mode.equals("duplicate") || mode.equals("equipped") ? 12 - level(instance) : before.getDamageValue();
                require(root.getDamageValue() == expected, "Production repair eligibility/max-level differs: " + mode);
                unchanged(before, root);
                clear(player);
            }
            report.append("B REPAIR ")
                  .append(sample[1])
                  .append(": native tick vs both mixed orientations, seeded success/failure; duplicate, equipped/inventory, healthy/creative/Unbreakable/cadence/absent state\n");
        }
        for (String[] sample : List.of(new String[]{"iron", "malleable"}, new String[]{"diamond", "brittle"},
                                       new String[]{"tyrian_steel", "sturdy"}, new String[]{"iron", "flexible"}, new String[]{"iron", "bending"})) {
            ItemStack source = FoundationChecks.armor(sample[0]);
            if (sample[1].equals("flexible")) {
                source = FoundationChecks.armor("iron", "lining", "wool");
            }
            if (sample[1].equals("bending")) {
                source = bending(source);
            }

            Object instance = view(source).get("silentgear:" + sample[1]);
            Object effect = effect(instance, "DurabilityTraitEffect");
            int scale = sample[1].equals("brittle") || sample[1].equals("bending") ? 1 : -1;
            // Flexible lining also has Malleable: isolate the selected computed native instance for this definition control.
            if (sample[1].equals("flexible")) {
                source = only(source, instance);
            }
            for (boolean activate : List.of(true, false)) {
                long seed = seed(chance(effect) * level(instance), activate);
                ItemStack control = source.copy();
                control.setDamageValue(10);
                random.setSeed(seed);
                control.hurtAndBreak(3, player, EquipmentSlot.LEGS);
                require(control.getDamageValue() == 13 + (activate ? scale : 0), "Native wear control differs: " + sample[1]);
                for (ItemStack root : pair(player, source, new ItemStack(Items.DIAMOND_LEGGINGS))) {
                    root.setDamageValue(10);
                    ItemStack before = root.copy();
                    random.setSeed(seed);
                    observe(() -> root.hurtAndBreak(3, player, EquipmentSlot.LEGS));
                    require(root.getDamageValue() == control.getDamageValue(), "Actual mixed damageItem differs: " + sample[1]);
                    unchanged(before, root);
                }
            }
            report.append("B WEAR ").append(sample[1]).append(": native hurtAndBreak and both mixed orientations; success/failure\n");
        }
        combined(player, report);
        ItemStack renewSource = FoundationChecks.armor("amethyst");
        ItemStack stellarSource = FoundationChecks.armor("refined_iron");
        long bothRepair = 0;
        for (; bothRepair < 100_000; bothRepair++) {
            var oracle = new Random(bothRepair);
            if (oracle.nextDouble() < .072F && oracle.nextDouble() < .02F) {
                break;
            }
        }
        require(bothRepair < 100_000, "Missing two-effect repair seed");
        for (ItemStack root : pair(player, renewSource, stellarSource)) {
            root.setDamageValue(12);
            ItemStack before = root.copy();
            random.setSeed(bothRepair);
            observe(() -> root.inventoryTick(player.level(), player, null));
            require(root.getDamageValue() == 7, "Distinct repair effects did not commit their one combined heal");
            unchanged(before, root);
        }
        report.append("B REPAIR combined: Renew IV + Stellar I, both inverse roots heal five once, one post-callback commit\n");
        ordering(player, report);
        levelsAndConditions(player, report);
        sibling(player, report);
        clear(player);
        require(recalculations == 0, "Production native gear recalculation was entered");
        report.append("B mutation witness: zero native recalculations during all production callbacks; sources/metadata immutable\n");
    }

    private static void combined(ServerPlayer player, StringBuilder report) throws Exception {
        for (ItemStack root : pair(player, FoundationChecks.armor("iron"), FoundationChecks.armor("diamond"))) {
            for (boolean brittle : List.of(false, true)) {
                for (boolean malleable : List.of(false, true)) {
                    long seed = 0;
                    for (; seed < 100_000; seed++) {
                        var oracle = new Random(seed);
                        if ((oracle.nextDouble() < .5) == brittle && (oracle.nextDouble() < .5) == malleable) {
                            break;
                        }
                    }
                    require(seed < 100_000, "Missing combined roll seed");
                    for (int incoming : List.of(1, 3)) {
                        root.setDamageValue(10);
                        ItemStack before = root.copy();
                        random.setSeed(seed);
                        observe(() -> root.hurtAndBreak(incoming, player, EquipmentSlot.LEGS));
                        require(root.getDamageValue() == 10 + incoming + (brittle ? 1 : 0) - (malleable ? 1 : 0),
                                "Combined production wear/order differs");
                        unchanged(before, root);
                    }
                }
            }
        }
        report.append("B WEAR combined: four independent seeded outcomes, both orientations, one root debit\n");
    }

    private static void ordering(ServerPlayer player, StringBuilder report) throws Exception {
        ItemStack source = FoundationChecks.armor("iron");
        Object instance = view(source).get("silentgear:malleable");
        Object effect = effect(instance, "DurabilityTraitEffect");
        long seed = seed(chance(effect) * level(instance), true);
        for (ItemStack root : pair(player, source, new ItemStack(Items.DIAMOND_LEGGINGS))) {
            root.setDamageValue(10);
            random.setSeed(seed);
            observe(() -> root.hurtAndBreak(1, player, EquipmentSlot.LEGS));
            require(root.getDamageValue() == 10, "One-point wear was not reduced to zero");
            observe(() -> root.hurtAndBreak(0, player, EquipmentSlot.LEGS));
            require(root.getDamageValue() == 10, "Zero wear changed damage");
            var unbreaking = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
            ItemStack ordinary = new ItemStack(Items.DIAMOND_LEGGINGS);
            ordinary.enchant(unbreaking, 3);
            ordinary.setDamageValue(10);
            root.enchant(unbreaking, 3);
            player.level().getRandom().setSeed(184);
            ordinary.hurtAndBreak(2, player, EquipmentSlot.LEGS);
            player.level().getRandom().setSeed(184);
            random.setSeed(seed);
            observe(() -> root.hurtAndBreak(3, player, EquipmentSlot.LEGS));
            require(root.getDamageValue() == ordinary.getDamageValue(), "Unbreaking did not process adjusted wear exactly once");
            root.remove(DataComponents.ENCHANTMENTS);
            root.setDamageValue(10);
            root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
            root.hurtAndBreak(3, player, EquipmentSlot.LEGS);
            require(root.getDamageValue() == 10, "Unbreakable was bypassed");
            root.remove(DataComponents.UNBREAKABLE);
            player.setGameMode(GameType.CREATIVE);
            root.hurtAndBreak(3, player, EquipmentSlot.LEGS);
            require(root.getDamageValue() == 10, "Creative wear exemption was bypassed");
            clear(player);
            root.setDamageValue(root.getMaxDamage() - 1);
            random.setSeed(seed);
            observe(() -> root.hurtAndBreak(4, player, EquipmentSlot.LEGS));
            require(root.isEmpty(), "Mixed inherited native non-destructive broken state");
        }

        ItemStack nativeBroken = source.copy();
        nativeBroken.setDamageValue(nativeBroken.getMaxDamage() - 1);
        random.setSeed(seed);
        nativeBroken.hurtAndBreak(4, player, EquipmentSlot.LEGS);
        require(!nativeBroken.isEmpty(), "Native broken-survival positive control disappeared");
        ItemStack worn = pair(player, source, new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
        // FakePlayer is sufficient for item callbacks, not a real connected-player damage control.
        player.level().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        ServerPlayer actor = FoundationChecks.player(player.level().getServer());
        ItemStack ordinaryArmor = new ItemStack(Items.DIAMOND_LEGGINGS);
        actor.setItemSlot(EquipmentSlot.LEGS, ordinaryArmor);
        var attack = actor.damageSources()
                          .mobAttack(new net.minecraft.world.entity.monster.zombie.Zombie(net.minecraft.world.entity.EntityType.ZOMBIE,
                                                                                          actor.level()));
        require(actor.hurtServer(actor.level(), attack, 12) && ordinaryArmor.getDamageValue() == 3, "Ordinary real armor-hit control differs");
        actor.invulnerableTime = 0;
        actor.setHealth(actor.getMaxHealth());
        actor.setItemSlot(EquipmentSlot.LEGS, worn);
        random.setSeed(seed);
        observe(() -> require(actor.hurtServer(actor.level(), attack, 12), "Fixture player rejected real armor hit"));
        require(worn.getDamageValue() == 2, "Actual armor damage path differs: " + worn.getDamageValue());
        actor.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        clear(player);
        report.append(
                "B lifecycle: zero/small wear, vanilla Unbreaking after native adjustment, creative/Unbreakable, normal mixed destruction vs native survival, actual armor hurt dispatch\n");
    }

    private static void sibling(ServerPlayer player, StringBuilder report) throws Exception {
        if (!Boolean.getBoolean("leggings.test.boots")) { return; }
        clear(player);
        var nativeItem = (net.silentchaos512.gear.api.item.GearItem) net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                net.minecraft.resources.Identifier.parse("silentgear:boots"));
        var renew = nativeItem.construct(List.of(net.silentchaos512.gear.gear.part.PartInstance.from(FoundationChecks.part("boot_plates", "amethyst"))));
        var wear = nativeItem.construct(List.of(net.silentchaos512.gear.gear.part.PartInstance.from(FoundationChecks.part("boot_plates", "iron"))));
        var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
        var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, renew, wear);
        for (String name : List.of("primary", "inverse")) {
            var root = (ItemStack) pair.getClass().getMethod(name).invoke(pair);
            root.setDamageValue(12);
            random.setSeed(seed(.072F, true));
            root.inventoryTick(player.level(), player, EquipmentSlot.FEET);
            require(root.getDamageValue() == 8, "Leggings capture/refusal disabled or doubled Boots repair");
            random.setSeed(seed(.5F, true));
            root.hurtAndBreak(3, player, EquipmentSlot.FEET);
            require(root.getDamageValue() == 10, "Leggings capture/refusal disabled or doubled Boots wear");
            require(!root.has(SilentGearCompatibility.state()), "Sibling acquired Leggings authority");
        }
        report.append("B sibling: both Boots roots repair/wear once through their own carrier, including local Leggings refusal\n");
    }

    private static void levelsAndConditions(ServerPlayer player, StringBuilder report) throws Exception {
        clear(player);
        var source = FoundationChecks.armor("amethyst");
        var renew = net.silentchaos512.gear.api.util.DataResource.trait("silentgear:renew");
        var lower = only(source, net.silentchaos512.gear.api.traits.TraitInstance.of(renew, 1));
        for (var root : pair(player, lower, source)) {
            root.setDamageValue(12);
            random.setSeed(seed(.072F, true));
            observe(() -> root.inventoryTick(player.level(), player, null));
            require(root.getDamageValue() == 8, "Unequal duplicate levels did not use maximum once");
        }
        var conditional = only(source, net.silentchaos512.gear.api.traits.TraitInstance.of(renew, 4,
                new net.silentchaos512.gear.gear.trait.condition.GearTypeTraitCondition(net.silentchaos512.gear.setup.gear.GearTypes.BOOTS)));
        for (var root : pair(player, conditional, new ItemStack(Items.DIAMOND_LEGGINGS))) {
            root.setDamageValue(12);
            var before = root.copy();
            random.setSeed(seed(.072F, true));
            observe(() -> root.inventoryTick(player.level(), player, EquipmentSlot.LEGS));
            require(ItemStack.matches(before, root), "Unmatched native repair condition ran");
        }
        // Eternal's existing root projection removes damageability. B cannot recreate either component.
        var eternal = pair(player, source, new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
        eternal.remove(DataComponents.MAX_DAMAGE);
        eternal.remove(DataComponents.DAMAGE);
        var eternalBefore = eternal.copy();
        observe(() -> eternal.inventoryTick(player.level(), player, EquipmentSlot.LEGS));
        eternal.hurtAndBreak(3, player, EquipmentSlot.LEGS);
        require(ItemStack.matches(eternalBefore, eternal), "Non-damageable Eternal projection lost authority");
        var root = pair(player, source, new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
        root.setDamageValue(20);
        root.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING), 1);
        player.setItemSlot(EquipmentSlot.LEGS, root);
        var before = root.copy();
        new net.minecraft.world.entity.ExperienceOrb(player.level(), 0, 65, 0, 5).playerTouch(player);
        require(root.getDamageValue() == 10, "Native Mending no longer repairs the live root");
        unchanged(before, root);
        clear(player);
        report.append("B source guards: unequal duplicate levels, unmatched conditions; non-damageable Eternal projection and real Mending root repair\n");
    }

    private static void refusal(ServerPlayer player, String variant, StringBuilder report) throws Exception {
        ItemStack repairSource = FoundationChecks.armor("amethyst");
        ItemStack wearSource = FoundationChecks.armor("iron");
        Object renew = view(repairSource).get("silentgear:renew");
        Object malleable = view(wearSource).get("silentgear:malleable");
        for (int repeat = 0; repeat < 2; repeat++) {
            ItemStack root = pair(player, repairSource, wearSource).getFirst();
            root.setDamageValue(12);
            random.setSeed(seed(chance(effect(renew, "SelfRepairTraitEffect")) * level(renew), true));
            observe(() -> root.inventoryTick(player.level(), player, null));
            require(root.getDamageValue() == (variant.equals("repair") ? 12 : 12 - level(renew)), "Independent repair refusal differs");
            int before = root.getDamageValue();
            random.setSeed(seed(chance(effect(malleable, "DurabilityTraitEffect")) * level(malleable), true));
            observe(() -> root.hurtAndBreak(3, player, EquipmentSlot.LEGS));
            require(root.getDamageValue() == before + ((variant.equals("wear") || variant.equals("wear-delta")) ? 3 : 2), "Independent wear refusal differs");
        }
        report.append("PRODUCTION B: variant=")
              .append(variant)
              .append("; refused local effect twice, other effect stays functional, no native recalculation\n");
    }

    public static boolean repairAlias(ItemStack stack, int amount, LivingEntity entity) {
        return games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearEffects.captureRepair(stack, amount, entity);
    }

    public static boolean falseAlias() { return false; }

    public static void recalculation(ItemStack stack) {
        if (observing) {
            recalculations++;
            throw new AssertionError("Production B entered native gear recalculation");
        }
    }

    private static void observe(Runnable action) {
        observing = true;
        try {
            action.run();
        } finally {
            observing = false;
        }
    }

    private static Object effect(Object instance, String type) throws Exception {
        return ((List<?>) traitOf(instance).getClass().getMethod("getEffects").invoke(traitOf(instance))).stream()
                                                                                                         .filter(value -> value.getClass()
                                                                                                                               .getSimpleName()
                                                                                                                               .equals(type))
                                                                                                         .findFirst()
                                                                                                         .orElseThrow();
    }

    private static float chance(Object effect) throws Exception {
        var field = effect.getClass().getDeclaredField("activationChance");
        field.setAccessible(true);
        return field.getFloat(effect);
    }

    private static long seed(float chance, boolean activate) {
        for (long seed = 0; seed < 100_000; seed++) {
            if ((new Random(seed).nextDouble() < chance) == activate) {
                return seed;
            }
        }
        throw new AssertionError("No bounded native roll control");
    }

    private static ItemStack bending(ItemStack source) {
        return only(source, net.silentchaos512.gear.api.traits.TraitInstance.of(
                net.silentchaos512.gear.api.util.DataResource.trait("silentgear:bending"), 1));
    }

    private static ItemStack only(ItemStack source, Object instance) {
        var copy = source.copy();
        var values = new java.util.HashMap<>(copy.get(net.silentchaos512.gear.setup.SgDataComponents.GEAR_PROPERTIES.get()).properties());
        values.put(net.silentchaos512.gear.setup.gear.GearProperties.TRAITS.get(),
                new net.silentchaos512.gear.api.property.TraitListPropertyValue(List.of((net.silentchaos512.gear.api.traits.TraitInstance) instance)));
        copy.set(net.silentchaos512.gear.setup.SgDataComponents.GEAR_PROPERTIES.get(), new net.silentchaos512.gear.core.component.GearPropertiesData(values));
        return copy;
    }

    private static java.util.Map<String, Object> view(ItemStack source) {
        var result = new java.util.HashMap<String, Object>();
        for (var trait : net.silentchaos512.gear.util.TraitHelper.getTraits(source)) {
            result.put(trait.getTraitId().toString(), trait);
        }
        return result;
    }

    private static Object traitOf(Object instance) {
        return ((net.silentchaos512.gear.api.traits.TraitInstance) instance).getTrait();
    }

    private static int level(Object instance) {
        return ((net.silentchaos512.gear.api.traits.TraitInstance) instance).getLevel();
    }

    private static Class<?> nativeClass(String name) throws ClassNotFoundException {
        return Class.forName("net.silentchaos512.gear." + name);
    }

    private static List<ItemStack> pair(ServerPlayer player, ItemStack first, ItemStack second) {
        return FoundationChecks.paid(player, first, second);
    }

    private static void unchanged(ItemStack before, ItemStack after) {
        var a = before.copy();
        var b = after.copy();
        a.remove(DataComponents.DAMAGE);
        b.remove(DataComponents.DAMAGE);
        require(ItemStack.matches(a, b), "Production B changed sources or unrelated root metadata");
    }

    private static void clear(ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        player.removeAllEffects();
        player.tickCount = 20;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
