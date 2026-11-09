package games.alejandrocoria.mismatchedleggings.test.awakenedset;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SetChecks {
    private static final String NATIVE = "com.blakebr0.mysticalagriculture.item.armor.EssenceChestplateItem";
    private static final String GRAPH = System.getProperty("leggings.test.awakenedGraph", "both");
    private static final String VARIANT = System.getProperty("leggings.test.awakenedVariant", "normal");
    private static final List<EquipmentSlot> ARMOR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static final StringBuilder REPORT = new StringBuilder();
    private static boolean finished;
    private static int cases;
    private static Method nativePredicate;
    private static final List<Method> orderPredicates = new ArrayList<>();
    private static final List<Method> orderTicks = new ArrayList<>();
    private SetChecks() {}
    public static boolean enabled() { return Boolean.getBoolean("leggings.test.awakenedSet"); }
    public static void tick(MinecraftServer server) {
        if (finished || server.getTickCount() < 20) { return; }
        finished = true;
        var directory = Path.of(System.getProperty("leggings.test.report"));
        try {
            environment();
            var player = player(server);
            if (Boolean.getBoolean("leggings.test.awakenedAbsent")) {
                for (String product : products()) {
                    var roots = pair(product, new ItemStack(product.equals("boots") ? Items.IRON_BOOTS : Items.IRON_LEGGINGS),
                            new ItemStack(product.equals("boots") ? Items.DIAMOND_BOOTS : Items.DIAMOND_LEGGINGS));
                    player.setItemSlot(slot(product), roots.getFirst());
                    roots.getFirst().inventoryTick(player.level(), player, slot(product));
                    require(!roots.getFirst().isEmpty(), "Ordinary absent-native root failed");
                    cases++;
                }
                REPORT.append("Optional MA/Cucumber absence: ordinary composition and worn ticks passed.\n");
            } else {
                nativePredicate = Class.forName(NATIVE).getDeclaredMethod("hasAwakenedSupremiumSet", Player.class);
                nativePredicate.setAccessible(true);
                finalTransform();
                run(player);
                regressions(server);
            }
            Files.writeString(directory.resolve("set.txt"), REPORT.append("Cases: ").append(cases).append('\n').toString());
            Files.writeString(directory.resolve("server.ok"), "passed\n");
        } catch (Throwable failure) {
            var writer = new StringWriter(); failure.printStackTrace(new PrintWriter(writer));
            try { Files.writeString(directory.resolve("server.failure.txt"), writer.toString()); } catch (Exception suppressed) { failure.addSuppressed(suppressed); }
            failure.printStackTrace();
        } finally { server.halt(false); }
    }
    private static List<String> products() {
        return switch (GRAPH) { case "boots" -> List.of("boots"); case "leggings" -> List.of("leggings"); case "both" -> List.of("boots", "leggings"); default -> List.of(); };
    }
    private static String productClass(String product, String suffix) {
        return "games.alejandrocoria.mismatched" + product + "." + suffix;
    }
    private static void environment() throws Exception {
        for (String product : List.of("boots", "leggings")) {
            boolean registered = BuiltInRegistries.ITEM.containsKey(Identifier.parse("mismatched" + product + ":mixed_" + product));
            require(registered == products().contains(product), "Wrong installed graph for " + product);
            if (registered) {
                var type = Class.forName(productClass(product, "compatibility.mysticalagriculture.MysticalAwakenedSetCompatibility"));
                require(type.getProtectionDomain().getCodeSource().getLocation().toString().contains(".jar"), "Loose production classes");
            }
        }
    }
    private static EquipmentSlot slot(String product) { return product.equals("boots") ? EquipmentSlot.FEET : EquipmentSlot.LEGS; }
    private static ItemStack item(String path) {
        var item = BuiltInRegistries.ITEM.getValue(Identifier.parse("mysticalagriculture:" + path));
        require(item != Items.AIR, "Missing native " + path); return new ItemStack(item);
    }
    private static List<ItemStack> pair(String product, ItemStack first, ItemStack second) throws Exception {
        var factory = Class.forName(productClass(product, "composition.Mixed" + (product.equals("boots") ? "Boots" : "Leggings") + "Factory"));
        var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, first, second);
        return List.of((ItemStack) pair.getClass().getMethod("primary").invoke(pair), (ItemStack) pair.getClass().getMethod("inverse").invoke(pair));
    }
    private static ServerPlayer player(MinecraftServer server) throws Exception {
        var profile = new GameProfile(UUID.randomUUID(), "awakened-set");
        var player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
        var loaded = ServerGamePacketListenerImpl.class.getDeclaredMethod("markClientLoaded"); loaded.setAccessible(true); loaded.invoke(player.connection);
        player.setPos(0, 80, 0); player.setNoGravity(true); player.level().getChunkAt(player.blockPosition());
        return player;
    }
    private static void equipNative(ServerPlayer player) {
        player.getInventory().clearContent();
        for (EquipmentSlot slot : ARMOR) {
            player.setItemSlot(slot, item("awakened_supremium_" + switch (slot) {
                case HEAD -> "helmet"; case CHEST -> "chestplate"; case LEGS -> "leggings"; default -> "boots";
            }));
        }
    }
    private record Choice(ItemStack stack, boolean qualifies) {}
    private static List<Choice> choices(String product) throws Exception {
        var result = new ArrayList<Choice>();
        result.add(new Choice(item("awakened_supremium_" + product), true));
        if (!products().contains(product)) { return result; }
        boolean ready = !VARIANT.startsWith(product + "-");
        var nativeItem = item("awakened_supremium_" + product);
        var ordinary = new ItemStack(product.equals("boots") ? Items.DIAMOND_BOOTS : Items.DIAMOND_LEGGINGS);
        for (var stack : pair(product, nativeItem, ordinary)) { result.add(new Choice(stack, ready)); }
        for (var stack : pair(product, ordinary, nativeItem)) { result.add(new Choice(stack, ready)); }
        for (var stack : pair(product, nativeItem, nativeItem)) { result.add(new Choice(stack, ready)); }
        for (var stack : pair(product, item("supremium_" + product), ordinary)) { result.add(new Choice(stack, false)); }
        for (var stack : pair(product, new ItemStack(product.equals("boots") ? Items.IRON_BOOTS : Items.IRON_LEGGINGS), ordinary)) { result.add(new Choice(stack, false)); }
        return result;
    }
    private static void run(ServerPlayer player) throws Exception {
        setConfig(true);
        if (GRAPH.equals("both") && VARIANT.equals("normal")) { bothOrders(); }
        for (var boots : choices("boots")) {
            for (var legs : choices("leggings")) {
                equipNative(player); player.setItemSlot(EquipmentSlot.FEET, boots.stack); player.setItemSlot(EquipmentSlot.LEGS, legs.stack);
                check(player, boots.qualifies && legs.qualifies, true);
            }
        }
        for (String product : products()) {
            var root = pair(product, item("awakened_supremium_" + product),
                    new ItemStack(product.equals("boots") ? Items.DIAMOND_BOOTS : Items.DIAMOND_LEGGINGS)).getFirst();
            for (var invalid : invalid(product, root)) {
                equipNative(player); player.setItemSlot(slot(product), invalid); check(player, false, true);
            }
            for (EquipmentSlot wrong : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.MAINHAND,
                    product.equals("boots") ? EquipmentSlot.LEGS : EquipmentSlot.FEET)) {
                equipNative(player); player.setItemSlot(slot(product), ItemStack.EMPTY); player.setItemSlot(wrong, root);
                check(player, false, true);
            }
            equipNative(player); player.setItemSlot(slot(product), ItemStack.EMPTY); player.getInventory().setItem(10, root);
            player.getInventory().setItem(11, item("awakened_supremium_" + product));
            check(player, false, true);
            root.inventoryTick(player.level(), player, null);
            require(SetControl.activations == 0, "Inventory-only root activated growth");
        }
        // Native and installed mixed sets must retain every original HEAD/CHEST/other-slot requirement.
        for (boolean mixed : List.of(false, true)) {
            for (EquipmentSlot missing : ARMOR) {
                for (boolean incorrect : List.of(false, true)) {
                    equipNative(player);
                    if (mixed) {
                        for (String product : products()) {
                            player.setItemSlot(slot(product), pair(product, item("awakened_supremium_" + product),
                                    new ItemStack(product.equals("boots") ? Items.DIAMOND_BOOTS : Items.DIAMOND_LEGGINGS)).getFirst());
                        }
                    }
                    player.setItemSlot(missing, incorrect ? new ItemStack(switch (missing) {
                        case HEAD -> Items.DIAMOND_HELMET; case CHEST -> Items.DIAMOND_CHESTPLATE;
                        case LEGS -> Items.DIAMOND_LEGGINGS; default -> Items.DIAMOND_BOOTS;
                    }) : ItemStack.EMPTY);
                    check(player, false, true);
                }
            }
        }
        equipNative(player); check(player, true, false);
        if (!products().isEmpty() && products().stream().noneMatch(p -> VARIANT.startsWith(p + "-"))) {
            equipNative(player);
            for (String product : products()) { player.setItemSlot(slot(product), pair(product, item("awakened_supremium_" + product),
                    new ItemStack(product.equals("boots") ? Items.DIAMOND_BOOTS : Items.DIAMOND_LEGGINGS)).getFirst()); }
            check(player, true, false);
        }
        setConfig(true);
        REPORT.append(products().isEmpty()
                ? "Native-only armor control (standalone Things for the harness): full set, every missing/incorrect piece and config-off passed.\n"
                : "Native/mixed eligibility, both outputs/orientations, one/two sources, Supremium/ordinary/invalid/suspended, wrong slots, inventory-only, all missing/incorrect pieces and config-off passed.\n");
        REPORT.append("Original chestplate dispatched growth exactly twice across ticks 1..40; ordinary armor/root ticks never duplicated it. Growth body was observed without cancellation or crop RNG assumptions.\n");
    }
    private static void check(ServerPlayer player, boolean eligible, boolean config) throws Exception {
        setConfig(config);
        require(nativePredicate.invoke(null, player).equals(eligible), "Native/mixed eligibility mismatch");
        for (var predicate : orderPredicates) {
            require(predicate.invoke(null, player).equals(eligible), "Packaged adapter order changed eligibility");
        }
        var snapshots = ARMOR.stream().map(s -> player.getItemBySlot(s).copy()).toList();
        long saved = player.level().getGameTime();
        try {
            for (int route = -1; route < orderTicks.size(); route++) {
                SetControl.activations = 0;
                for (int tick = 1; tick <= 40; tick++) {
                    player.level().getServer().getWorldData().overworldData().setGameTime(tick);
                    for (var slot : ARMOR) {
                        var stack = player.getItemBySlot(slot);
                        if (route >= 0 && slot == EquipmentSlot.CHEST && Class.forName(NATIVE).isInstance(stack.getItem())) {
                            orderTicks.get(route).invoke(null, stack.getItem(), stack, player.level(), player, slot);
                        } else { stack.inventoryTick(player.level(), player, slot); }
                    }
                    require(SetControl.activations == (eligible && config ? tick / 20 : 0), "Native cadence/consumer mismatch at tick " + tick + "/route=" + route);
                }
                // Merely carrying a native chestplate cannot provide a second growth route.
                var carried = item("awakened_supremium_chestplate");
                if (route >= 0) {
                    orderTicks.get(route).invoke(null, carried.getItem(), carried, player.level(), player, EquipmentSlot.MAINHAND);
                } else { carried.inventoryTick(player.level(), player, EquipmentSlot.MAINHAND); }
                require(SetControl.activations == (eligible && config ? 2 : 0), "Carried chestplate activated growth");
            }
            for (int i = 0; i < ARMOR.size(); i++) {
                require(ItemStack.matches(snapshots.get(i), player.getItemBySlot(ARMOR.get(i))), "Growth recognition mutated armor/source history");
            }
        } finally { player.level().getServer().getWorldData().overworldData().setGameTime(saved); }
        cases++;
    }
    @SuppressWarnings("unchecked")
    private static List<ItemStack> invalid(String product, ItemStack root) throws Exception {
        var result = new ArrayList<ItemStack>();
        result.add(new ItemStack(root.getItem()));
        var count = root.copy(); count.setCount(2); result.add(count);
        var noSlot = root.copy(); noSlot.remove(DataComponents.EQUIPPABLE); result.add(noSlot);
        var wrongSlot = root.copy(); wrongSlot.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).build()); result.add(wrongSlot);
        var foreign = new ItemStack(Items.STICK); foreign.applyComponents(root.getComponents()); result.add(foreign);
        String title = product.equals("boots") ? "Boots" : "Leggings";
        var dataType = (DataComponentType<Object>) BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse("mismatched" + product + ":suspended_" + product));
        var suspendedType = Class.forName(productClass(product, "data.Suspended" + title + "Data"));
        var suspended = suspendedType.getMethod("quarantined", CompoundTag.class, CompoundTag.class).invoke(null, new CompoundTag(), new CompoundTag());
        var history = root.copy(); history.set(dataType, suspended); result.add(history);
        return result;
    }
    private static void setConfig(boolean enabled) throws Exception {
        var option = Class.forName("com.blakebr0.mysticalagriculture.config.ModConfigs").getField("AWAKENED_SUPREMIUM_SET_BONUS").get(null);
        option.getClass().getMethod("set", Object.class).invoke(option, enabled);
    }
    private static void regressions(MinecraftServer server) throws Exception {
        if (products().isEmpty()) { return; }
        for (String product : products()) {
            var first = games.alejandrocoria.mismatchedleggings.test.mysticaleffects.NativeAugments.item("awakened_supremium_" + product, "FIRE_RESISTANCE");
            var second = games.alejandrocoria.mismatchedleggings.test.mysticaleffects.NativeAugments.item("inferium_" + product, "LUCK_I");
            var roots = pair(product, first, second);
            for (int orientation = 0; orientation < roots.size(); orientation++) {
                var player = player(server);
                var root = roots.get(orientation);
                var before = root.copy();
                player.setItemSlot(slot(product), root);
                player.setRemainingFireTicks(100);
                player.aiStep();
                require(player.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE) && !player.isOnFire()
                        && player.hasEffect(net.minecraft.world.effect.MobEffects.LUCK), "Set adaptation disabled inherited effects: " + product);
                require(ItemStack.matches(before, root), "Inherited effects changed root history");
                root.remove(DataComponents.UNBREAKABLE); root.setDamageValue(root.getMaxDamage() - 1);
                root.hurtAndBreak(2, player.level(), player, item -> {});
                require(root.isEmpty(), "Ordinary actual break refused");
                String expected = orientation == 0 ? "fire_resistance_augment" : "luck_i_augment";
                int total = 0;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    var stack = player.getInventory().getItem(i);
                    var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (id.getNamespace().equals("mysticalagriculture") && id.getPath().endsWith("_augment")) {
                        require(id.getPath().equals(expected), "Refund credited the effective union instead of the left source");
                        total += stack.getCount();
                    }
                }
                require(total == 1, "Missing or duplicated owner refund: " + product + "/" + orientation);
                cases++;
            }
        }
        REPORT.append("Focused inherited Fire Resistance/Luck and actual-break left-owner refunds passed for both outputs of every installed product, including locally refused set extensions.\n");
    }
    private static void finalTransform() throws Exception {
        for (String product : List.of("boots", "leggings")) {
            boolean ready = false;
            try {
                ready = Boolean.TRUE.equals(Class.forName(NATIVE).getDeclaredMethod("mismatched" + product + "$maAwakenedSetReady").invoke(null));
            } catch (NoSuchMethodException expectedRefusalOrAbsence) { /* No edit was authorized. */ }
            require(ready == (products().contains(product) && !VARIANT.startsWith(product + "-")), "Wrong loaded final local proof for " + product);
        }
        REPORT.append("Packaged graph=").append(GRAPH).append("; variant=").append(VARIANT).append("; final local hooks verified.\n");
    }
    private static void bothOrders() throws Exception {
        for (boolean bootsFirst : List.of(false, true)) {
            var node = new ClassNode();
            try (var input = Class.forName(NATIVE).getResourceAsStream("/" + NATIVE.replace('.', '/') + ".class")) {
                new ClassReader(Objects.requireNonNull(input)).accept(node, 0);
            }
            for (String product : bootsFirst ? List.of("boots", "leggings") : List.of("leggings", "boots")) {
                var shape = Class.forName(productClass(product, "compatibility.mysticalagriculture.MysticalAwakenedSetShape"));
                require(Boolean.TRUE.equals(shape.getMethod("apply", ClassNode.class).invoke(null, node)), "Actual packaged edit refused " + product);
            }
            String old = node.name;
            var tick = node.methods.stream().filter(m -> m.name.equals("inventoryTick")).findFirst().orElseThrow();
            require(java.util.Arrays.stream(tick.instructions.toArray()).noneMatch(n -> n instanceof VarInsnNode v && v.var == 0),
                    "Pinned native tick now depends on its receiver; static test bridge is no longer valid");
            // Preserve every native instruction/local index; expose the unused receiver as explicit argument zero.
            tick.access |= org.objectweb.asm.Opcodes.ACC_STATIC;
            tick.desc = "(Lnet/minecraft/world/item/Item;" + tick.desc.substring(1);
            var growth = node.methods.stream().filter(m -> m.name.equals("handleGrowthTicks")).findFirst().orElseThrow();
            var witness = new InsnList();
            String control = "games/alejandrocoria/mismatchedleggings/test/awakenedset/SetControl";
            witness.add(new FieldInsnNode(org.objectweb.asm.Opcodes.GETSTATIC, control, "activations", "I"));
            witness.add(new InsnNode(org.objectweb.asm.Opcodes.ICONST_1));
            witness.add(new InsnNode(org.objectweb.asm.Opcodes.IADD));
            witness.add(new FieldInsnNode(org.objectweb.asm.Opcodes.PUTSTATIC, control, "activations", "I"));
            growth.instructions.insert(witness);
            growth.maxStack = Math.max(growth.maxStack, 2);
            node.name = "fixture/awakened/" + (bootsFirst ? "BootsFirst" : "LeggingsFirst");
            for (var method : node.methods) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.owner.equals(old)) { call.owner = node.name; }
                    if (instruction instanceof FieldInsnNode field && field.owner.equals(old)) { field.owner = node.name; }
                    if (instruction instanceof FrameNode frame && frame.local != null) {
                        for (int i = 0; i < frame.local.size(); i++) {
                            if (old.equals(frame.local.get(i))) { frame.local.set(i, method == tick && i == 0 ? "net/minecraft/world/item/Item" : node.name); }
                        }
                    }
                    if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                        for (int i = 0; i < dynamic.bsmArgs.length; i++) {
                            if (dynamic.bsmArgs[i] instanceof org.objectweb.asm.Handle handle && handle.getOwner().equals(old)) {
                                dynamic.bsmArgs[i] = new org.objectweb.asm.Handle(handle.getTag(), node.name, handle.getName(), handle.getDesc(), handle.isInterface());
                            }
                        }
                    }
                }
            }
            var writer = new org.objectweb.asm.ClassWriter(0); node.accept(writer);
            var type = new OrderLoader(SetChecks.class.getClassLoader()).define(writer.toByteArray());
            var predicate = type.getDeclaredMethod("hasAwakenedSupremiumSet", Player.class); predicate.setAccessible(true);
            orderPredicates.add(predicate);
            orderTicks.add(type.getDeclaredMethod("inventoryTick", net.minecraft.world.item.Item.class, ItemStack.class,
                    net.minecraft.server.level.ServerLevel.class, net.minecraft.world.entity.Entity.class, EquipmentSlot.class));
        }
        REPORT.append("Both actual packaged FEET/LEGS edit orders defined in isolated JVM classes; all eligibility/cadence/config cases execute their predicates and unchanged native tick/growth bodies through verified receiver-free static bridges.\n");
    }
    private static final class OrderLoader extends ClassLoader {
        private OrderLoader(ClassLoader parent) { super(parent); }
        private Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
    }
    private static void require(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
