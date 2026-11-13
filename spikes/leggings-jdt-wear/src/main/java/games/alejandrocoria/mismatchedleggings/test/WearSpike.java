package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Actual native hits and production wallets; the candidate and baseline are separate JVM selections. */
public final class WearSpike {
    public static boolean partialReceipt;
    public static ServerPlayer replacementPlayer;
    private static ServerPlayer observed;
    private static String mutation = "none";
    private static boolean finished;
    private static boolean active;
    private static boolean bootsActive;
    private static final StringBuilder rows = new StringBuilder("case\tFE\taddedWear\n");

    private WearSpike() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ArmorHurtEvent event) -> {
            if (event.getEntity() != observed) {
                return;
            }
            if (mutation.equals("cancel")) {
                event.setCanceled(true);
            } else if (!mutation.equals("none")) {
                event.setNewDamage(EquipmentSlot.LEGS, Float.parseFloat(mutation));
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static void tick(MinecraftServer server) {
        if (finished || !server.isDedicatedServer() || server.getTickCount() < 20) {
            return;
        }
        finished = true;
        try {
            server.setDifficulty(Difficulty.NORMAL, true);
            String graph = System.getProperty("leggings.test.wearBoots");
            String variant = System.getProperty("leggings.test.wearVariant");
            boolean hasBoots = !graph.equals("alone");
            CommonHooks.onArmorHurt(server.overworld().damageSources().generic(), new EquipmentSlot[0], 2, player(server));
            if (hasBoots) {
                Class.forName("games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireArmorWearCompatibility");
                bootsActive = ready(CommonHooks.class, "mismatchedboots$justDireArmorWearReady");
            }
            active = variant.equals("baseline") ? false : hasBoots && !variant.equals("naive")
                    ? WearSpikePlugin.commonReady && WearSpikePlugin.bridgeReady
                    : ready(CommonHooks.class, "leggingswearspike$wearReady");
            if (variant.equals("naive") && hasBoots) {
                require(!active || !bootsActive, "Naive conditional wrappers unexpectedly preserve both guards; investigate instead of assuming rejection");
            } else {
                require(active == variant.equals("candidate"), "Wrong candidate guard state: " + active);
                require(!hasBoots || bootsActive, "Leggings spike disabled installed Boots wear");
            }
            rows.append("guards: leggings=").append(active).append(", boots=").append(bootsActive).append('\n');
            if (hasBoots && active && !variant.equals("naive")) {
                bridgeNegatives();
            }
            for (String tier : List.of("celestigem", "eclipsealloy")) {
                for (int charge : List.of(1000, 199, 0)) {
                    checkHit(server, "native " + tier + '/' + charge, nativeLeggings(tier, charge), Math.max(0, charge - 200), 0);
                    for (boolean inverse : List.of(false, true)) {
                        boolean paid = active && charge >= 200;
                        checkHit(server, "hybrid " + tier + '/' + charge + '/' + inverse, mixed(tier, false, charge, inverse),
                                paid ? charge - 200 : charge, paid ? 0 : 2);
                    }
                }
            }
            for (int charge : List.of(1000, 199, 0)) {
                for (boolean inverse : List.of(false, true)) {
                    checkHit(server, "two providers " + charge + '/' + inverse, mixed("celestigem", true, charge, inverse),
                            active ? Math.max(0, charge - 200) : charge, active ? 0 : 2);
                }
            }
            checkHit(server, "vanilla", new ItemStack(Items.DIAMOND_LEGGINGS), -1, 2);
            var root = mixed("eclipsealloy", false, 1000, false);
            root.set(JustDireDataComponents.TOOL_ENABLED, false);
            checkHit(server, "global off", root, active ? 800 : 1000, active ? 0 : 2);
            root = mixed("eclipsealloy", false, 1000, true);
            root.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 3);
            if (active) {
                checkHit(server, "linear Unbreaking III", root, 860, 0);
            }
            direct(server);
            mutations(server);
            if (active) {
                transactions(server);
            }
            if (hasBoots) {
                coexistence(server);
            }
            Files.writeString(report("wear-spike.tsv"), rows.toString());
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            SkeletonChecks.failed("server", failure);
        } finally {
            partialReceipt = false;
            replacementPlayer = null;
            observed = null;
            server.halt(false);
        }
    }

    private static void direct(MinecraftServer server) {
        var player = player(server);
        var root = mixed("eclipsealloy", false, 1000, false);
        player.setItemSlot(EquipmentSlot.LEGS, root);
        root.hurtAndBreak(2, player, EquipmentSlot.LEGS);
        state("direct mixed request", root, 1000, 2);
        var nativeStack = nativeLeggings("eclipsealloy", 1000);
        player.setItemSlot(EquipmentSlot.LEGS, nativeStack);
        nativeStack.hurtAndBreak(2, player, EquipmentSlot.LEGS);
        state("direct native request", nativeStack, 998, 0);
    }

    private static void bridgeNegatives() throws Exception {
        var bytes = Files.readAllBytes(Path.of(".mixin.out/class/games/alejandrocoria/mismatchedboots/compatibility/justdirethings/JustDireArmorWearCompatibility.class"));
        var intact = bridgeClass(bytes);
        require(WearSpikeShape.bridgePublish(intact), "Actual final bridge export cannot be independently verified");
        var broken = bridgeClass(bytes);
        var handler = broken.methods.stream().filter(method -> method.name.contains("leggingswearspike$bridge")).findFirst().orElseThrow();
        boolean changed = false;
        for (var instruction : handler.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.endsWith("/WearSpikeCandidate") && call.name.equals("route")) {
                // ready, root, amount, entity, slot, Operation, originalDamage
                var slot = call.getPrevious().getPrevious().getPrevious();
                handler.instructions.set(slot, new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/entity/EquipmentSlot", "FEET",
                        "Lnet/minecraft/world/entity/EquipmentSlot;"));
                changed = true;
            }
        }
        require(changed && !WearSpikeShape.bridgePublish(broken), "Wrong routed slot retained bridge permission");
        broken = bridgeClass(bytes);
        handler = broken.methods.stream().filter(method -> method.name.contains("leggingswearspike$bridge")).findFirst().orElseThrow();
        handler.visibleAnnotations.clear();
        require(!WearSpikeShape.bridgePublish(broken), "Foreign hook retained bridge permission");
        rows.append("final bridge negatives: wrong slot and foreign ownership refused\n");
    }

    private static ClassNode bridgeClass(byte[] bytes) {
        var node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        var getter = node.methods.stream().filter(method -> method.name.equals(WearSpikeShape.GETTER)).findFirst().orElseThrow();
        for (var instruction : getter.instructions) {
            if (instruction.getOpcode() == Opcodes.ICONST_1) {
                getter.instructions.set(instruction, new InsnNode(Opcodes.ICONST_0));
            }
        }
        return node;
    }

    private static void mutations(MinecraftServer server) {
        try {
            mutation = "cancel";
            checkHit(server, "cancelled wear", mixed("eclipsealloy", false, 1000, false), 1000, 0);
            mutation = "0.75";
            checkHit(server, "post-event 0.75", mixed("eclipsealloy", false, 1000, false), 1000, 0);
            mutation = "1.75";
            checkHit(server, "post-event 1.75", mixed("eclipsealloy", false, 1000, false), active ? 900 : 1000, active ? 0 : 1);
        } finally {
            mutation = "none";
        }
    }

    private static void transactions(MinecraftServer server) {
        try {
            partialReceipt = true;
            checkHit(server, "partial receipt rollback", mixed("eclipsealloy", false, 1000, false), 1000, 2);
        } finally {
            partialReceipt = false;
        }
        var player = player(server);
        var root = mixed("eclipsealloy", false, 1000, false);
        player.setItemSlot(EquipmentSlot.LEGS, root);
        replacementPlayer = player;
        try {
            CommonHooks.onArmorHurt(player.damageSources().generic(), new EquipmentSlot[]{EquipmentSlot.LEGS}, 2, player);
            require(player.getItemBySlot(EquipmentSlot.LEGS) != root, "Stale equipment probe did not replace the actual slot");
            state("stale detached root", root, 1000, 0);
            state("replacement root", player.getItemBySlot(EquipmentSlot.LEGS), 1000, 0);
        } finally {
            replacementPlayer = null;
        }
    }

    private static void coexistence(MinecraftServer server) throws Exception {
        var nativeBoots = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("justdirethings:eclipsealloy_boots")));
        var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
        var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class)
                .invoke(null, nativeBoots, new ItemStack(Items.DIAMOND_BOOTS));
        var boots = (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
        boots.set(JustDireDataComponents.FORGE_ENERGY, 2000);
        var legs = mixed("celestigem", false, 1000, true);
        var player = player(server);
        player.setItemSlot(EquipmentSlot.LEGS, legs);
        player.setItemSlot(EquipmentSlot.FEET, boots);
        player.doTick();
        require(player.hurtServer(player.level(), player.damageSources().mobAttack(new Zombie(EntityType.ZOMBIE, player.level())), 8),
                "Two-slot incoming hit was rejected");
        state("simultaneous LEGS", legs, active ? 800 : 1000, active ? 0 : 2);
        state("simultaneous FEET", boots, bootsActive ? 1800 : 2000, bootsActive ? 0 : 2);
    }

    private static void checkHit(MinecraftServer server, String name, ItemStack root, int energy, int wear) {
        var player = player(server);
        player.setItemSlot(EquipmentSlot.LEGS, root);
        player.doTick();
        var before = root.copy();
        observed = player;
        try {
            require(player.hurtServer(player.level(), player.damageSources().mobAttack(new Zombie(EntityType.ZOMBIE, player.level())), 8),
                    "Real incoming hit rejected: " + name);
        } finally {
            observed = null;
        }
        state(name, root, energy, wear);
        var expected = before.copy();
        expected.setDamageValue(wear);
        if (energy >= 0) {
            expected.set(JustDireDataComponents.FORGE_ENERGY, energy);
        }
        require(ItemStack.matches(expected, root), "Incoming wear changed unrelated root/history data: " + name);
    }

    private static void state(String name, ItemStack root, int energy, int wear) {
        require(root.getDamageValue() == wear && root.getOrDefault(JustDireDataComponents.FORGE_ENERGY, -1) == energy,
                name + ": FE=" + root.getOrDefault(JustDireDataComponents.FORGE_ENERGY, -1) + ", wear=" + root.getDamageValue());
        rows.append(name).append('\t').append(energy).append('\t').append(wear).append('\n');
    }

    private static ItemStack mixed(String tier, boolean two, int charge, boolean inverse) {
        var pair = MixedLeggingsFactory.createPair(nativeLeggings(tier, 0),
                two ? nativeLeggings("eclipsealloy", 0) : new ItemStack(Items.DIAMOND_LEGGINGS));
        var root = inverse ? pair.inverse() : pair.primary();
        root.set(JustDireDataComponents.FORGE_ENERGY, charge);
        return root;
    }

    private static ItemStack nativeLeggings(String tier, int charge) {
        var root = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("justdirethings:" + tier + "_leggings")));
        root.set(JustDireDataComponents.FORGE_ENERGY, charge);
        return root;
    }

    private static ServerPlayer player(MinecraftServer server) {
        var player = RepairFixtures.player(server);
        player.connection.markClientLoaded();
        player.setNoGravity(true);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static boolean ready(Class<?> owner, String method) throws Exception {
        try {
            var getter = owner.getDeclaredMethod(method);
            getter.setAccessible(true);
            return (boolean) getter.invoke(null);
        } catch (NoSuchMethodException absent) {
            return false;
        }
    }

    private static Path report(String name) {
        return Path.of(System.getProperty("leggings.test.report"), name);
    }
}
