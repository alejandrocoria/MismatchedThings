package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.common.items.armors.basearmors.BaseLeggings;
import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import com.direwolf20.justdirethings.common.items.interfaces.PoweredItem;
import com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool;
import com.direwolf20.justdirethings.setup.Config;
import com.direwolf20.justdirethings.setup.JDTRegistration;
import com.direwolf20.justdirethings.util.ModTags;
import games.alejandrocoria.mismatchedleggings.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.composition.NeoForgeCompositionAttributes;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.test.mixin.EquipmentUpdatesInvoker;
import games.alejandrocoria.mismatchedthings.api.composition.AttributeMerger;
import games.alejandrocoria.mismatchedthings.api.composition.AttributePolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.transfer.access.ItemAccess;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

public final class JustDirePassiveChecks {
    private JustDirePassiveChecks() {
    }

    public static ItemStack nativeStack(String tier) {
        var stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("justdirethings", tier + "_leggings")));
        for (Ability ability : List.of(Ability.WALKSPEED, Ability.RUNSPEED, Ability.SWIMSPEED, Ability.PHASE, Ability.DECOY)) {
            stack.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(ability), true);
        }
        return stack;
    }

    public static List<ItemStack> pair(ItemStack first, ItemStack second) {
        var pair = MixedLeggingsFactory.createPair(first, second);
        return List.of(pair.primary(), pair.inverse());
    }

    public static void movement(Player player, boolean client) throws Exception {
        var eclipse = nativeStack("eclipsealloy");
        var ferricore = nativeStack("ferricore");
        var mixed = pair(eclipse, new ItemStack(Items.DIAMOND_LEGGINGS));
        var unequal = pair(ferricore, eclipse);
        var duplicate = pair(eclipse, eclipse.copy());
        var cases = new ArrayList<ItemStack>();
        cases.add(eclipse);
        cases.add(ferricore);
        for (String tier : List.of("blazegold", "celestigem")) {
            var nativeTier = nativeStack(tier);
            cases.add(nativeTier);
            cases.addAll(pair(nativeTier, new ItemStack(Items.DIAMOND_LEGGINGS)));
        }
        cases.addAll(mixed);
        cases.addAll(unequal);
        cases.addAll(duplicate);
        var water = player.blockPosition();
        var oldBlock = player.level().getBlockState(water);
        try {
            player.level().setBlock(water, Blocks.AIR.defaultBlockState(), 3);
            for (ItemStack stack : cases) {
                var snapshot = stack.copy();
                for (Ability ability : List.of(Ability.WALKSPEED, Ability.RUNSPEED, Ability.SWIMSPEED)) {
                    player.setItemSlot(EquipmentSlot.LEGS, stack);
                    player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                    player.zza = 1;
                    player.fallDistance = 0;
                    player.setYRot(0);
                    player.setSprinting(ability == Ability.RUNSPEED);
                    player.setOnGround(true);
                    setWater(player, ability == Ability.SWIMSPEED);
                    player.setDeltaMovement(Vec3.ZERO);
                    execute(player, stack, client);
                    var profile = stack.getItem() instanceof ToggleableTool ? stack : stack.get(LeggingsContent.mixedLeggingsDataType()).left().source();
                    if (!(profile.getItem() instanceof ToggleableTool)) {
                        profile = stack.get(LeggingsContent.mixedLeggingsDataType()).right().source();
                    }
                    // Unequal Ferricore/Eclipse controls select the larger native range, not the owner range.
                    if (unequal.contains(stack) || duplicate.contains(stack)) { profile = eclipse; }
                    var nativeProfile = (ToggleableTool) profile.getItem();
                    double tier = nativeProfile.hasAbility(ability) ? nativeProfile.getAbilityParams(ability).defaultValue : 0;
                    double expected = tier / (ability == Ability.SWIMSPEED ? 50.0 : 25.0);
                    close(player.getDeltaMovement().length(), expected, "Native/production " + ability + " on " + stack.getItem());
                    if (ability != Ability.SWIMSPEED) {
                        player.setOnGround(false);
                        player.setDeltaMovement(Vec3.ZERO);
                        execute(player, stack, client);
                        close(player.getDeltaMovement().length(), expected / 4, "Native airborne reduction");
                        player.setOnGround(true);
                    }
                    player.zza = 0;
                    player.setDeltaMovement(Vec3.ZERO);
                    execute(player, stack, client);
                    close(player.getDeltaMovement().length(), 0, "Native forward predicate");
                    player.zza = 1;
                    ToggleableTool.setSetting(stack, ability.getName(), false);
                    player.setDeltaMovement(Vec3.ZERO);
                    execute(player, stack, client);
                    close(player.getDeltaMovement().length(), 0, "Per-ability off");
                    ToggleableTool.setSetting(stack, ability.getName(), true);
                    stack.set(JustDireDataComponents.TOOL_ENABLED, false);
                    player.setDeltaMovement(Vec3.ZERO);
                    execute(player, stack, client);
                    close(player.getDeltaMovement().length(), 0, "Global off");
                    stack.remove(JustDireDataComponents.TOOL_ENABLED);
                    stack.remove(JustDireDataComponents.ABILITY_TOGGLES.get(ability));
                    var config = Config.AVAILABLE_ABILITY_MAP.get(ability);
                    boolean previous = config.get();
                    try {
                        config.set(false);
                        player.setDeltaMovement(Vec3.ZERO);
                        execute(player, stack, client);
                        close(player.getDeltaMovement().length(), 0, "Native config off");
                    } finally {
                        config.set(previous);
                    }
                }
                require(ItemStack.matches(stack, snapshot), "Movement changed FE/history/components");
            }
        } finally {
            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            player.zza = 0;
            player.setSprinting(false);
            player.level().setBlock(water, oldBlock, 3);
            setWater(player, false);
        }
        if (!client) { rootControls(player); }
        note(client ? "client-movement.txt" : "server-movement.txt",
                "Native and real mixed roots: walk/run/swim callbacks, empty FE, airborne reduction, both orientations, unequal tiers, duplicates, global/per-ability/config toggles and unchanged state passed\n");
    }

    private static void rootControls(Player player) throws Exception {
        var uninstalled = nativeStack("eclipsealloy");
        for (var ability : List.of(Ability.WALKSPEED, Ability.RUNSPEED, Ability.SWIMSPEED)) {
            uninstalled.remove(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(ability));
        }
        player.setSprinting(false);
        player.setOnGround(true);
        player.zza = 1;
        for (var root : pair(nativeStack("ferricore"), uninstalled)) {
            root.remove(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.WALKSPEED));
            player.setItemSlot(EquipmentSlot.LEGS, root);
            player.setDeltaMovement(Vec3.ZERO);
            execute(player, root, false);
            close(player.getDeltaMovement().length(), 0, "Historical installation resurrected motion");
            root.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.WALKSPEED), true);
            for (int stored : new int[]{-100, 99}) {
                root.set(JustDireDataComponents.ABILITY_VALUES.get(Ability.WALKSPEED), stored);
                player.setDeltaMovement(Vec3.ZERO);
                execute(player, root, false);
                var nativeView = new ItemStack(uninstalled.getItem());
                nativeView.applyComponents(root.getComponents());
                close(player.getDeltaMovement().length(), ToggleableTool.getToolValue(nativeView, "walkspeed") / 25.0,
                        "Effective higher tier/range bounds used owner or historical values");
            }
            player.fallDistance = 1;
            player.setDeltaMovement(Vec3.ZERO);
            execute(player, root, false);
            close(player.getDeltaMovement().length(), 0, "Native falling Walk predicate lost");
            player.fallDistance = 0;
            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            player.setDeltaMovement(Vec3.ZERO);
            root.inventoryTick(player.level(), player, EquipmentSlot.MAINHAND);
            close(player.getDeltaMovement().length(), 0, "Stored/held root granted motion");
        }
        player.zza = 0;
    }

    private static void execute(Player player, ItemStack stack, boolean client) {
        if (client) {
            // The actual native client entry checks the local player before executing armorTick.
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
        } else if (stack.getItem() instanceof BaseLeggings leggings) {
            leggings.inventoryTick(stack, (ServerLevel) player.level(), player, EquipmentSlot.LEGS);
        } else {
            stack.getItem().inventoryTick(stack, (ServerLevel) player.level(), player, EquipmentSlot.LEGS);
        }
    }

    private static void setWater(Player player, boolean value) throws Exception {
        // Control only the environmental predicate; do not duplicate native movement physics.
        var field = Entity.class.getDeclaredField("wasTouchingWater");
        field.setAccessible(true);
        field.setBoolean(player, value);
    }

    public static double phase(ItemStack stack) {
        return stack.getAttributeModifiers().modifiers().stream().filter(entry -> entry.attribute().equals(JDTRegistration.PHASE))
                .mapToDouble(entry -> entry.modifier().amount()).sum();
    }

    public static void phase(Player player) throws Exception {
        var nativeItem = nativeStack("eclipsealloy");
        var roots = pair(nativeItem, new ItemStack(Items.DIAMOND_LEGGINGS));
        for (ItemStack stack : List.of(nativeItem, roots.getFirst(), roots.getLast())) {
            for (int energy : new int[]{0, 49, 50, 49999, 50000}) {
                stack.set(JustDireDataComponents.FORGE_ENERGY, energy);
                close(phase(stack), energy < 50 ? 0 : 1, "Phase FE boundary " + energy);
            }
            stack.set(JustDireDataComponents.TOOL_ENABLED, false);
            close(phase(stack), 0, "Phase global toggle");
            stack.remove(JustDireDataComponents.TOOL_ENABLED);
            ToggleableTool.setSetting(stack, Ability.PHASE.getName(), false);
            close(phase(stack), 0, "Phase per-ability toggle");
            ToggleableTool.setSetting(stack, Ability.PHASE.getName(), true);
        }
        var setting = Config.AVAILABLE_ABILITY_MAP.get(Ability.PHASE);
        boolean previous = setting.get();
        try {
            setting.set(false);
            for (ItemStack stack : roots) { close(phase(stack), 0, "Phase native config off"); }
            close(phase(nativeItem), 0, "Native Phase config control");
        } finally {
            setting.set(previous);
        }
        for (ItemStack root : roots) { close(phase(root), 1, "Phase restored without recomposition"); }
        nativeItem.set(JustDireDataComponents.FORGE_ENERGY, 50000);
        var chargedRoots = pair(nativeItem, new ItemStack(Items.DIAMOND_LEGGINGS));
        chargedRoots.getFirst().set(JustDireDataComponents.FORGE_ENERGY, 0);
        close(phase(chargedRoots.getFirst()), 0, "Production failed to remove frozen creation-time Phase");
        var sentinel = ((ToggleableTool) nativeItem.getItem()).getAbilityParams(Ability.PHASE);
        require(sentinel.minSlider == -1 && sentinel.maxSlider == -1, "Native Phase parameters changed");
        collision(player, roots.getFirst());
        var sourceData = chargedRoots.getFirst().get(LeggingsContent.mixedLeggingsDataType());
        var history = sourceData;
        var live = chargedRoots.getFirst();
        live.set(JustDireDataComponents.FORGE_ENERGY, 50);
        player.setItemSlot(EquipmentSlot.LEGS, live);
        ((EquipmentUpdatesInvoker) player).leggings$detectEquipmentUpdates();
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        close(player.getAttributeValue(JDTRegistration.PHASE), 1, "Worn Phase positive");
        try {
            setting.set(false);
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
            close(player.getAttributeValue(JDTRegistration.PHASE), 0, "Config change left worn Phase stuck without stack edits");
        } finally { setting.set(previous); }
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        close(player.getAttributeValue(JDTRegistration.PHASE), 1, "Config restore did not restore worn Phase");
        require(live.get(LeggingsContent.mixedLeggingsDataType()).equals(history), "Worn refresh edited historical sources");
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        note("phase.txt", "0/49/50/49999/50000 FE, global/per-ability/config toggles and restore, both orientations: passed. No 50000 FE activation threshold; Phase sentinel is -1/-1. Native wall/floor/unbreakable collision consumers passed\n");
    }

    public static void armor(Player player) throws Exception {
        var source = nativeStack("eclipsealloy");
        source.set(JustDireDataComponents.FORGE_ENERGY, 500000);
        for (var sources : List.of(List.of(source, new ItemStack(Items.DIAMOND_LEGGINGS)), List.of(source, source.copy()))) {
            for (var root : pair(sources.getFirst(), sources.getLast())) {
                var history = root.get(LeggingsContent.mixedLeggingsDataType());
                for (int charge : new int[]{0, 49, 50, 49999, 50000}) {
                    root.set(JustDireDataComponents.FORGE_ENERGY, charge);
                    var expectedSources = new ArrayList<ItemAttributeModifiers>();
                    for (var input : List.of(history.left().source(), history.right().source())) {
                        if (input.getItem() instanceof PoweredItem) {
                            input.set(JustDireDataComponents.FORGE_ENERGY, charge);
                        }
                        expectedSources.add(new NeoForgeCompositionAttributes().query(input));
                    }
                    var expected = AttributeMerger.merge(
                            expectedSources.getFirst(), expectedSources.getLast(),
                            CompositionRules.defaults().attributes()::policyFor).primary();
                    for (var attribute : List.of(Attributes.ARMOR,
                            Attributes.ARMOR_TOUGHNESS,
                            Attributes.KNOCKBACK_RESISTANCE, JDTRegistration.PHASE)) {
                        close(amount(root.getAttributeModifiers(), attribute), amount(expected, attribute), "Live source-composed " + attribute + '/' + charge);
                    }
                    require(history.equals(root.get(LeggingsContent.mixedLeggingsDataType())), "FE refresh changed history");
                }
            }
        }
        for (var policy : List.of(AttributePolicy.AVERAGE,
                AttributePolicy.SEPARATE)) {
            var defaults = CompositionRules.defaults();
            var rules = new java.util.LinkedHashMap<>(defaults.attributes().rules());
            rules.put(Identifier.parse("justdirethings:phase"), policy);
            var pair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.DIAMOND_LEGGINGS),
                    new CompositionRules(
                            new AttributeRuleSet(defaults.attributes().fallback(), rules), defaults.components()));
            close(phase(pair.primary()), policy == AttributePolicy.SEPARATE ? 1 : .5, "Explicit Phase policy owner");
            close(phase(pair.inverse()), policy == AttributePolicy.SEPARATE ? 0 : .5, "Explicit Phase policy inverse");
        }
        var setting = Config.AVAILABLE_ABILITY_MAP.get(Ability.PHASE);
        boolean previous = setting.get();
        ItemStack disabledCreation;
        try {
            setting.set(false);
            disabledCreation = pair(source, new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
            close(phase(disabledCreation), 0, "Disabled creation Phase");
        } finally { setting.set(previous); }
        close(phase(disabledCreation), 1, "Creation-time config froze missing Phase forever");
        var defaults = CompositionRules.defaults();
        var rejectedRules = new java.util.LinkedHashMap<>(defaults.attributes().rules());
        rejectedRules.put(Identifier.parse("justdirethings:phase"), AttributePolicy.REJECT);
        boolean rejected = false;
        try {
            MixedLeggingsFactory.createPair(source, new ItemStack(Items.DIAMOND_LEGGINGS), new CompositionRules(
                    new AttributeRuleSet(defaults.attributes().fallback(), rejectedRules), defaults.components()));
        } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "Explicit Phase rejection was bypassed by live projection");
        var root = pair(source, new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
        java.util.function.Consumer<net.neoforged.neoforge.event.ItemAttributeModifierEvent> foreign = event -> {
            if (!event.getItemStack().has(LeggingsContent.mixedLeggingsDataType())) { return; }
            event.removeModifier(Attributes.ARMOR, Identifier.withDefaultNamespace("armor.leggings"));
            event.addModifier(Attributes.ARMOR, new AttributeModifier(Identifier.withDefaultNamespace("armor.leggings"), 88,
                    AttributeModifier.Operation.ADD_VALUE), net.minecraft.world.entity.EquipmentSlotGroup.LEGS);
        };
        NeoForge.EVENT_BUS.addListener(foreign);
        try {
            for (int charge : new int[]{0, 100}) {
                root.set(JustDireDataComponents.FORGE_ENERGY, charge);
                close(amount(root.getAttributeModifiers(), Attributes.ARMOR), 88, "Pre-event projection erased foreign event override");
            }
        } finally { NeoForge.EVENT_BUS.unregister(foreign); }
        var control = root.copy();
        var unrelatedSlot = new ItemAttributeModifiers.Entry(Attributes.ARMOR,
                new AttributeModifier(Identifier.withDefaultNamespace("armor.leggings"), 17, AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.CHEST);
        var entries = new ArrayList<>(root.get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS).modifiers());
        entries.add(unrelatedSlot);
        root.set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(entries));
        for (int charge : new int[]{0, 100}) {
            root.set(JustDireDataComponents.FORGE_ENERGY, charge);
            control.set(JustDireDataComponents.FORGE_ENERGY, charge);
            var queried = root.getAttributeModifiers();
            require(queried.modifiers().contains(unrelatedSlot), "Owned projection removed an unrelated slot identity");
            close(queried.modifiers().stream().filter(entry -> entry.attribute().equals(Attributes.ARMOR)
                            && entry.slot() == net.minecraft.world.entity.EquipmentSlotGroup.LEGS).mapToDouble(entry -> entry.modifier().amount()).sum(),
                    amount(control.getAttributeModifiers(), Attributes.ARMOR), "Unrelated slot disabled the owned live projection");
        }
        note("armor.txt", "Native physical/Phase boundaries, one/two powered sources, both orientations, explicit policies, config-off creation/restore and worn config refresh passed\n");
    }

    public static double amount(ItemAttributeModifiers modifiers,
                                Holder<Attribute> attribute) {
        return modifiers.modifiers().stream().filter(entry -> entry.attribute().equals(attribute)).mapToDouble(entry -> entry.modifier().amount()).sum();
    }

    public static void fault(Player player) throws Exception {
        var source = nativeStack("eclipsealloy");
        source.set(JustDireDataComponents.FORGE_ENERGY, 1000);
        var root = pair(source, new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
        player.setItemSlot(EquipmentSlot.LEGS, root);
        player.zza = 1;
        player.fallDistance = 0;
        player.setSprinting(false);
        player.setOnGround(true);
        setWater(player, false);
        player.setDeltaMovement(Vec3.ZERO);
        execute(player, root, false);
        boolean movement = System.getProperty("leggings.test.jdtFault").equals("movement");
        close(player.getDeltaMovement().length(), movement ? 0 : .2, "Local hook refusal did not isolate movement");
        close(phase(root), movement ? 1 : 0, "Local hook refusal did not isolate Phase");
        var storage = ItemAccess.forStack(root)
                .getCapability(Capabilities.Energy.ITEM);
        require(storage != null && storage.getAmountAsLong() == 500, "Local refusal disabled independent root storage");
        player.setItemSlot(EquipmentSlot.LEGS, source);
        player.setDeltaMovement(Vec3.ZERO);
        execute(player, source, false);
        close(player.getDeltaMovement().length(), .2, "Native movement control was changed by a local hook fault");
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        note("fault.txt", "Refused local " + (movement ? "movement" : "attribute") + " seam; independent route, FE and native positive remain healthy\n");
    }

    private static void collision(Player player, ItemStack root) {
        var instance = player.getAttribute(JDTRegistration.PHASE);
        require(instance != null, "No native Phase player attribute");
        var id = Identifier.fromNamespaceAndPath("leggings_jdt_tests", "consumer");
        var pos = new BlockPos(8, 70, 8);
        player.setPos(8.5, 70, 8.5);
        player.level().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        var box = new AABB(8.1, 70.1, 8.1, 8.9, 71.9, 8.9);
        require(StreamSupport.stream(player.level().getBlockCollisions(player, box).spliterator(), false).count() > 0,
                "Native collision negative control absent");
        instance.addTransientModifier(new AttributeModifier(id, phase(root),
                AttributeModifier.Operation.ADD_VALUE));
        try {
            require(StreamSupport.stream(player.level().getBlockCollisions(player, box).spliterator(), false).count() == 0,
                    "Native Phase does not recognize production attribute");
            require(!player.level().collidesWithSuffocatingBlock(player, box), "Native suffocation consumer");
            player.level().setBlock(pos, Blocks.BEDROCK.defaultBlockState(), 3);
            require(StreamSupport.stream(player.level().getBlockCollisions(player, box).spliterator(), false).count() > 0,
                    "Phase incorrectly bypassed unbreakable blocks");
            player.level().setBlock(pos, Blocks.GLASS.defaultBlockState(), 3);
            require(Blocks.GLASS.defaultBlockState().is(ModTags.Blocks.PHASEDENY)
                    && Blocks.GLASS.defaultBlockState().getDestroySpeed(player.level(), pos) >= 0
                    && StreamSupport.stream(player.level().getBlockCollisions(player, box).spliterator(), false).count() > 0,
                    "Breakable deny-tag control was not preserved independently of unbreakability");
            player.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            player.level().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            var floorBox = new AABB(8.1, 69.9, 8.1, 8.9, 71.7, 8.9);
            require(StreamSupport.stream(player.level().getBlockCollisions(player, floorBox).spliterator(), false).count() > 0,
                    "Phase incorrectly removed floor collision");
        } finally {
            instance.removeModifier(id);
            player.level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            player.level().setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    public static void note(String file, String text) throws Exception {
        Files.writeString(Path.of(System.getProperty("leggings.test.report")).resolve(file), text);
    }

    public static void close(double value, double expected, String message) {
        require(Math.abs(value - expected) < 0.00001, message + ": " + value + " != " + expected);
    }

    public static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
