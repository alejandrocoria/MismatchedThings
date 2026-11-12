package games.alejandrocoria.jdtspike;

import com.direwolf20.justdirethings.common.items.armors.basearmors.BaseLeggings;
import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool;
import com.direwolf20.justdirethings.setup.Config;
import com.direwolf20.justdirethings.setup.JDTRegistration;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;

public final class Checks {
    private Checks() {
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
        // Production root upgrade inheritance is intentionally not part of this feasibility proof.
        for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
            for (Ability ability : List.of(Ability.WALKSPEED, Ability.RUNSPEED, Ability.SWIMSPEED, Ability.PHASE, Ability.DECOY)) {
                root.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(ability),
                        ToggleableTool.hasUpgrade(first, ability) || ToggleableTool.hasUpgrade(second, ability));
            }
        }
        return List.of(pair.primary(), pair.inverse());
    }

    public static void movement(Player player, boolean client) throws Exception {
        var eclipse = nativeStack("eclipsealloy");
        var ferricore = nativeStack("ferricore");
        var mixed = pair(eclipse, new ItemStack(Items.DIAMOND_LEGGINGS));
        var unequal = pair(ferricore, eclipse);
        var duplicate = pair(eclipse, eclipse.copy());
        var cases = new java.util.ArrayList<ItemStack>();
        cases.add(eclipse);
        cases.add(ferricore);
        cases.addAll(mixed);
        cases.addAll(unequal);
        cases.addAll(duplicate);
        var water = player.blockPosition();
        var oldBlock = player.level().getBlockState(water);
        try {
            player.level().setBlock(water, Blocks.AIR.defaultBlockState(), 3);
            for (ItemStack stack : cases) {
                var snapshot = stack.copy();
                for (Ability ability : Candidate.MOVEMENT) {
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
                    double tier = stack == ferricore ? 1 : 5;
                    double expected = ability == Ability.SWIMSPEED && stack == ferricore ? 0
                            : tier / (ability == Ability.SWIMSPEED ? 50.0 : 25.0);
                    close(player.getDeltaMovement().length(), expected, "Native/candidate " + ability + " on " + stack.getItem());
                    if (ability != Ability.SWIMSPEED) {
                        player.setOnGround(false);
                        player.setDeltaMovement(Vec3.ZERO);
                        execute(player, stack, client);
                        close(player.getDeltaMovement().length(), expected / 4, "Native airborne reduction");
                        player.setOnGround(true);
                    }
                    if (Candidate.mixed(stack)) {
                        Candidate.enabled = false;
                        player.setDeltaMovement(Vec3.ZERO);
                        execute(player, stack, client);
                        close(player.getDeltaMovement().length(), 0, "Unadapted mixed negative");
                        Candidate.enabled = true;
                    }
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
            Candidate.enabled = true;
            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            player.zza = 0;
            player.setSprinting(false);
            player.level().setBlock(water, oldBlock, 3);
            setWater(player, false);
        }
        note(client ? "client-movement.txt" : "server-movement.txt",
                "Native and real mixed roots: walk/run/swim callbacks, empty FE, airborne reduction, both orientations, unequal tiers, duplicates, global/per-ability/config toggles and unchanged state passed\n");
    }

    private static void execute(Player player, ItemStack stack, boolean client) {
        if (client) {
            // The actual native client entry checks the local player before executing armorTick.
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
        } else if (stack.getItem() instanceof BaseLeggings leggings) {
            leggings.inventoryTick(stack, (ServerLevel) player.level(), player, EquipmentSlot.LEGS);
        } else {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
        }
    }

    private static void setWater(Player player, boolean value) throws Exception {
        // Control only the environmental predicate; do not duplicate native movement physics.
        var field = net.minecraft.world.entity.Entity.class.getDeclaredField("wasTouchingWater");
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
        Candidate.enabled = false;
        try {
            close(phase(chargedRoots.getFirst()), 1, "Charged creation did not capture the expected frozen Phase baseline");
            chargedRoots.getFirst().set(JustDireDataComponents.FORGE_ENERGY, 0);
            close(phase(chargedRoots.getFirst()), 1, "Unadapted frozen baseline unexpectedly live");
        } finally {
            Candidate.enabled = true;
        }
        close(phase(chargedRoots.getFirst()), 0, "Candidate failed to remove frozen creation-time Phase");
        var sentinel = ((ToggleableTool) nativeItem.getItem()).getAbilityParams(Ability.PHASE);
        require(sentinel.minSlider == -1 && sentinel.maxSlider == -1, "Native Phase parameters changed");
        collision(player, roots.getFirst());
        note("phase.txt", "0/49/50/49999/50000 FE, global/per-ability/config toggles and restore, both orientations: passed. No 50000 FE activation threshold; Phase sentinel is -1/-1. Native wall/floor/unbreakable collision consumers passed\n");
    }

    private static void collision(Player player, ItemStack root) {
        var instance = player.getAttribute(JDTRegistration.PHASE);
        require(instance != null, "No native Phase player attribute");
        var id = Identifier.fromNamespaceAndPath("leggings_jdt_spike", "consumer");
        var pos = new BlockPos(8, 70, 8);
        player.setPos(8.5, 70, 8.5);
        player.level().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        var box = new AABB(8.1, 70.1, 8.1, 8.9, 71.9, 8.9);
        require(StreamSupport.stream(player.level().getBlockCollisions(player, box).spliterator(), false).count() > 0,
                "Native collision negative control absent");
        instance.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, phase(root),
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        try {
            require(StreamSupport.stream(player.level().getBlockCollisions(player, box).spliterator(), false).count() == 0,
                    "Native Phase does not recognize candidate attribute");
            require(!player.level().collidesWithSuffocatingBlock(player, box), "Native suffocation consumer");
            player.level().setBlock(pos, Blocks.BEDROCK.defaultBlockState(), 3);
            require(StreamSupport.stream(player.level().getBlockCollisions(player, box).spliterator(), false).count() > 0,
                    "Phase incorrectly bypassed unbreakable blocks");
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
        Files.writeString(Path.of(System.getProperty("jdt.spike.report")).resolve(file), text);
    }

    public static void close(double value, double expected, String message) {
        require(Math.abs(value - expected) < 0.00001, message + ": " + value + " != " + expected);
    }

    public static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
