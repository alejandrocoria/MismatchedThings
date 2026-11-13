package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import games.alejandrocoria.mismatchedthings.wearspike.SharedWearRouter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Real product wallets, identical registration API; fixture reflection avoids a sibling compile dependency. */
public final class SharedWearChecks {
    private static final Map<EquipmentSlot, Integer> calls = new EnumMap<>(EquipmentSlot.class);
    private static final StringBuilder rows = new StringBuilder("case\tslot\tFE\twear\n");
    private static EquipmentSlot disabled;
    private static ServerPlayer observed;
    private static String mutation = "none";
    private static boolean finished;
    private static boolean ready;

    private SharedWearChecks() {}

    public static void register() {
        if (ModList.get().isLoaded("mismatchedboots")) {
            participant(EquipmentSlot.FEET, "games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireArmorWearCompatibility");
        }
        if (ModList.get().isLoaded("mismatchedleggings")) {
            participant(EquipmentSlot.LEGS, SharedLeggingsPayment.class.getName());
        }
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ArmorHurtEvent event) -> {
            if (event.getEntity() == observed) {
                if (mutation.equals("cancel")) {
                    event.setCanceled(true);
                } else if (!mutation.equals("none")) {
                    event.setNewDamage(EquipmentSlot.LEGS, Float.parseFloat(mutation));
                    event.setNewDamage(EquipmentSlot.FEET, Float.parseFloat(mutation));
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static void participant(EquipmentSlot slot, String type) {
        try {
            var method = Class.forName(type).getMethod("route", boolean.class, ItemStack.class, int.class,
                    net.minecraft.world.entity.LivingEntity.class, EquipmentSlot.class,
                    com.llamalad7.mixinextras.injector.wrapoperation.Operation.class, float.class);
            SharedWearRouter.register(slot, (allowed, root, amount, entity, actualSlot, ordinary, damage) -> {
                calls.merge(slot, 1, Integer::sum);
                try {
                    method.invoke(null, allowed && disabled != slot, root, amount, entity, actualSlot, ordinary, damage);
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Product wear callback failed", failure);
                }
            });
            try {
                SharedWearRouter.register(slot, (allowed, root, amount, entity, actualSlot, ordinary, damage) -> {
                    throw new AssertionError("Rejected duplicate replaced the live handler");
                });
                throw new AssertionError("Duplicate registration accepted");
            } catch (IllegalStateException expected) {
                require(expected.getMessage().startsWith("Duplicate armor"), "Unexpected registration failure");
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Missing selected product handler", failure);
        }
    }

    private static void tick(MinecraftServer server) {
        if (finished || server.getTickCount() < 20) { return; }
        finished = true;
        try {
            require(ModList.get().getMods().stream().filter(mod -> mod.getModId().equals("mismatchedthings")).count() == 1,
                    "Expected one selected candidate Things");
            var origin = SharedWearRouter.class.getProtectionDomain().getCodeSource().getLocation().toString();
            require(origin.contains("candidate-things.jar") || origin.contains("mismatchedthings"), "Core router did not load from candidate core: " + origin);
            if (Boolean.getBoolean("sharedwear.absent")) {
                require(!ModList.get().isLoaded("justdirethings"), "Native mod leaked into absence profile");
                rows.append("JDT absent; core and independent products loaded\n");
            } else {
                server.setDifficulty(Difficulty.NORMAL, true);
                CommonHooks.onArmorHurt(server.overworld().damageSources().generic(), new EquipmentSlot[0], 2, player(server));
                var getter = CommonHooks.class.getDeclaredMethod("mismatchedthings$wearReady");
                getter.setAccessible(true);
                ready = (boolean) getter.invoke(null);
                require(ready != Boolean.getBoolean("sharedwear.refused"), "Wrong shared final guard state");
                require(java.util.Arrays.stream(CommonHooks.class.getDeclaredMethods()).noneMatch(method -> method.getName().equals("mismatchedboots$justDireArmorWearReady")),
                        "Candidate Boots retained the retired per-product consumer");
                rows.append("core guard=").append(ready).append("; duplicate registration refused\n");
                if (ready) { guardNegatives(); }
                for (int charge : List.of(1000, 199, 0)) {
                    hit(server, "hybrid " + charge, charge, false, false);
                    hit(server, "inverse hybrid " + charge, charge, false, true);
                    hit(server, "two providers " + charge, charge, true, false);
                }
                mutation = "cancel";
                hit(server, "cancelled", 1000, false, false);
                mutation = "0.75";
                hit(server, "fraction below one", 1000, false, false);
                mutation = "1.75";
                hit(server, "integer quantization", 1000, false, false);
                mutation = "none";
                for (var slot : List.of(EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                    if (installed(slot)) {
                        disabled = slot;
                        hit(server, "local refusal " + slot, 1000, false, false);
                        disabled = null;
                    }
                }
                direct(server);
            }
            Files.writeString(report("shared-wear.tsv"), rows.toString());
            Files.writeString(report("shared-wear.ok"), "passed\n");
        } catch (Throwable failure) {
            try { Files.writeString(report("shared-wear.failure.txt"), failure + "\n" + java.util.Arrays.toString(failure.getStackTrace())); }
            catch (Exception writeFailure) { failure.addSuppressed(writeFailure); }
            failure.printStackTrace();
        } finally {
            server.halt(false);
        }
    }

    private static void hit(MinecraftServer server, String name, int charge, boolean two, boolean inverse) throws Exception {
        var player = player(server);
        var legs = installed(EquipmentSlot.LEGS) ? mixed(EquipmentSlot.LEGS, charge, two, inverse) : new ItemStack(Items.DIAMOND_LEGGINGS);
        var boots = installed(EquipmentSlot.FEET) ? mixed(EquipmentSlot.FEET, charge, two, inverse) : new ItemStack(Items.DIAMOND_BOOTS);
        player.setItemSlot(EquipmentSlot.LEGS, legs);
        player.setItemSlot(EquipmentSlot.FEET, boots);
        player.doTick();
        var before = Map.of(EquipmentSlot.LEGS, legs.copy(), EquipmentSlot.FEET, boots.copy());
        calls.clear();
        observed = player;
        try {
            require(player.hurtServer(player.level(), player.damageSources().mobAttack(new Zombie(EntityType.ZOMBIE, player.level())), 8), "Incoming hit rejected");
        } finally { observed = null; }
        int amount = mutation.equals("cancel") || mutation.equals("0.75") ? 0 : mutation.equals("1.75") ? 1 : 2;
        for (var slot : List.of(EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            var root = player.getItemBySlot(slot);
            boolean present = installed(slot);
            boolean converts = present && ready && disabled != slot && (two || charge >= amount * 100);
            int energy = present ? converts ? Math.max(0, charge - amount * 100) : charge : -1;
            int wear = converts ? 0 : amount;
            require(root.getDamageValue() == wear && root.getOrDefault(JustDireDataComponents.FORGE_ENERGY, -1) == energy,
                    name + '/' + slot + ": wrong wallet or wear: " + root);
            var expected = before.get(slot).copy();
            expected.setDamageValue(wear);
            if (present) { expected.set(JustDireDataComponents.FORGE_ENERGY, energy); }
            require(ItemStack.matches(expected, root), name + '/' + slot + ": unrelated state changed");
            require(calls.getOrDefault(slot, 0) == (ready && present && !mutation.equals("cancel") ? 1 : 0), "Duplicate/missing slot dispatch: " + name + '/' + slot);
            rows.append(name).append('\t').append(slot).append('\t').append(energy).append('\t').append(wear).append('\n');
        }
    }

    private static void direct(MinecraftServer server) throws Exception {
        var player = player(server);
        for (var slot : List.of(EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            if (installed(slot)) {
                var root = mixed(slot, 1000, false, false);
                player.setItemSlot(slot, root);
                root.hurtAndBreak(2, player, slot);
                require(root.getDamageValue() == 2 && root.getOrDefault(JustDireDataComponents.FORGE_ENERGY, -1) == 1000, "Direct wear converted by shared interceptor");
            }
        }
        rows.append("direct requests retain ordinary wear\n");
    }

    private static void guardNegatives() throws Exception {
        var bytes = Files.readAllBytes(Path.of(".mixin.out/class/net/neoforged/neoforge/common/CommonHooks.class"));
        var proof = Class.forName("games.alejandrocoria.mismatchedthings.wearspike.SharedWearShape").getDeclaredMethod("publish", ClassNode.class);
        proof.setAccessible(true);
        require((boolean) proof.invoke(null, unpublishedClone(bytes)), "Shared final export fails its independent proof");
        var wrongSlot = unpublishedClone(bytes);
        var handler = wrongSlot.methods.stream().filter(method -> method.name.endsWith("mismatchedthings$wear")).findFirst().orElseThrow();
        int replacements = 0;
        for (var instruction : handler.instructions) {
            if (instruction instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.ALOAD && variable.var == 3) {
                handler.instructions.set(variable, new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/entity/EquipmentSlot", "FEET",
                        "Lnet/minecraft/world/entity/EquipmentSlot;"));
                replacements++;
            }
        }
        require(replacements == 1 && !(boolean) proof.invoke(null, wrongSlot), "Wrong routed slot retained shared permission");
        var foreign = unpublishedClone(bytes);
        handler = foreign.methods.stream().filter(method -> method.name.endsWith("mismatchedthings$wear")).findFirst().orElseThrow();
        handler.visibleAnnotations.clear();
        require(!(boolean) proof.invoke(null, foreign), "Foreign hook retained shared permission");
        rows.append("final shared export: wrong slot and foreign ownership refused\n");
    }

    private static ClassNode unpublishedClone(byte[] bytes) {
        var node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        var getter = node.methods.stream().filter(method -> method.name.equals("mismatchedthings$wearReady")).findFirst().orElseThrow();
        for (var instruction : getter.instructions) {
            if (instruction.getOpcode() == Opcodes.ICONST_1) {
                getter.instructions.set(instruction, new InsnNode(Opcodes.ICONST_0));
            }
        }
        return node;
    }

    private static ItemStack mixed(EquipmentSlot slot, int charge, boolean two, boolean inverse) throws Exception {
        String suffix = slot == EquipmentSlot.LEGS ? "leggings" : "boots";
        String product = slot == EquipmentSlot.LEGS ? "mismatchedleggings" : "mismatchedboots";
        String factory = slot == EquipmentSlot.LEGS ? "MixedLeggingsFactory" : "MixedBootsFactory";
        var first = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("justdirethings:celestigem_" + suffix)));
        var second = two ? new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("justdirethings:eclipsealloy_" + suffix)))
                : new ItemStack(slot == EquipmentSlot.LEGS ? Items.DIAMOND_LEGGINGS : Items.DIAMOND_BOOTS);
        var pair = Class.forName("games.alejandrocoria." + product + ".composition." + factory)
                .getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, first, second);
        var root = (ItemStack) pair.getClass().getMethod(inverse ? "inverse" : "primary").invoke(pair);
        root.set(JustDireDataComponents.FORGE_ENERGY, charge);
        return root;
    }

    private static boolean installed(EquipmentSlot slot) {
        return ModList.get().isLoaded(slot == EquipmentSlot.LEGS ? "mismatchedleggings" : "mismatchedboots");
    }

    private static ServerPlayer player(MinecraftServer server) {
        var player = RepairFixtures.player(server);
        player.connection.markClientLoaded();
        player.setNoGravity(true);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static Path report(String name) { return Path.of(System.getProperty("sharedwear.report"), name); }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
