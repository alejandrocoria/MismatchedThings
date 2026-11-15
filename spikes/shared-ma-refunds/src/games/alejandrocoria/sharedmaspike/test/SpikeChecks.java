package games.alejandrocoria.sharedmaspike.test;

import games.alejandrocoria.sharedmaspike.core.SharedMaRefunds;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Disposable product registrations and reuse of real maintained adversarial assertions. */
public final class SpikeChecks {
    private static boolean initialized;

    private SpikeChecks() {
    }

    public static List<String> withoutRefund(List<String> mixins) {
        return mixins.stream().filter(name -> !name.equals("MysticalRefundMixin")).toList();
    }

    public static void initialize() {
        if (initialized) { return; }
        initialized = true;
        for (String product : List.of("mismatchedboots", "mismatchedleggings")) {
            String itemName = product.equals("mismatchedboots") ? "mixed_boots" : "mixed_leggings";
            var item = BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath(product, itemName));
            if (item.isEmpty()) { continue; }
            String adapter = "games.alejandrocoria." + product + ".compatibility.mysticalagriculture.MysticalAugmentsCompatibility";
            try {
                Method reader = Class.forName(adapter).getDeclaredMethod("refunds", ItemStack.class);
                reader.setAccessible(true);
                SharedMaRefunds.register(item.get().value(), (root, damage, owner) ->
                        capture(reader, root, damage, owner));
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Candidate owner registration", failure);
            }
        }
    }

    public static void extraBoots(MinecraftServer server) throws Exception {
        if (BuiltInRegistries.ITEM.get(Identifier.parse("mismatchedboots:mixed_boots")).isPresent()) {
            BootsRefundChecks.run(server);
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "boots-refunds.txt"), BootsRefundChecks.evidence());
        }
    }

    private static SharedMaRefunds.Receipt capture(Method reader, ItemStack root, int damage, LivingEntity owner) {
        if (!(owner instanceof ServerPlayer player) || player.hasInfiniteMaterials() || player.isSpectator()
                || root.getCount() != 1 || !root.isDamageableItem() || root.getMaxDamage() <= 0 || damage < root.getMaxDamage()) {
            return null;
        }
        Inventory inventory = player.getInventory();
        int slot = -1;
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            if (inventory.getItem(index) == root) {
                if (slot >= 0) { return null; }
                slot = index;
            }
        }
        Object purchases = read(reader, root);
        if (slot < 0 || purchases == null) { return null; }
        var id = BuiltInRegistries.ITEM.getKey(root.getItem());
        var dataType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_data"))
                .orElseThrow().value();
        return new ProductReceipt(reader, root, root.getItem(), player, inventory, slot,
                root.getMaxDamage(), root.getComponents(), purchases, dataType, root.get(dataType));
    }

    private static Object read(Method reader, ItemStack root) {
        try { return reader.invoke(null, root); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private static final class ProductReceipt implements SharedMaRefunds.Receipt {
        private final Method reader;
        private final ItemStack root;
        private final Item item;
        private final ServerPlayer player;
        private final Inventory inventory;
        private final int slot;
        private final int maximum;
        private final DataComponentMap live;
        private final Object purchases;
        private final DataComponentType<?> dataType;
        private final Object data;
        private boolean armed;
        private boolean consumed;

        private ProductReceipt(Method reader, ItemStack root, Item item, ServerPlayer player, Inventory inventory,
                               int slot, int maximum, DataComponentMap live, Object purchases, DataComponentType<?> dataType, Object data) {
            this.reader = reader; this.root = root; this.item = item; this.player = player;
            this.inventory = inventory; this.slot = slot; this.maximum = maximum;
            this.live = live; this.purchases = purchases;
            this.dataType = dataType; this.data = data;
        }

        private boolean eligible() {
            return player.getInventory() == inventory && inventory.getItem(slot) == root
                    && !player.hasInfiniteMaterials() && !player.isSpectator();
        }

        private boolean unchanged() {
            var shadow = new ItemStack(item, 1);
            List.copyOf(shadow.getComponents().keySet()).forEach(shadow::remove);
            shadow.applyComponents(live);
            return shadow.getDamageValue() == maximum && shadow.getMaxDamage() == maximum
                    && java.util.Objects.equals(data, shadow.get(dataType)) && purchases.equals(read(reader, shadow));
        }

        public void arm() {
            armed = eligible() && root.getCount() == 1 && root.getItem() == item
                    && root.getDamageValue() == maximum && unchanged();
        }

        public void consumed() {
            consumed = armed && eligible() && root.getCount() == 0 && unchanged();
        }

        public void complete() {
            if (!consumed || !eligible() || root.getCount() != 0 || !unchanged()) { return; }
            consumed = false;
            try {
                var accessor = purchases.getClass().getDeclaredMethod("items");
                accessor.setAccessible(true);
                var items = (List<?>) accessor.invoke(purchases);
                for (Object refund : items) { inventory.placeItemBackInInventory(new ItemStack((Item) refund)); }
            } catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof RuntimeException runtime) { throw runtime; }
                throw new IllegalStateException(failure.getCause());
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }
    }

    public static void verifyTransform(boolean refused) throws Exception {
        var method = ItemStack.class.getDeclaredMethod("mismatchedthings$maRefundReady");
        method.setAccessible(true);
        if (!method.invoke(null).equals(!refused)) { throw new AssertionError("Shared MA permission"); }
        var node = new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(Files.readAllBytes(Path.of(".mixin.out/class/net/minecraft/world/item/ItemStack.class"))).accept(node, 0);
        int handlers = 0;
        for (var merged : node.methods) {
            if (merged.visibleAnnotations == null) { continue; }
            for (var annotation : merged.visibleAnnotations) {
                if (!annotation.desc.endsWith("/MixinMerged;") || annotation.values == null) { continue; }
                int index = annotation.values.indexOf("mixin");
                if (index < 0) { continue; }
                String owner = annotation.values.get(index + 1).toString();
                if (owner.endsWith("mysticalagriculture.MysticalRefundMixin")) { throw new AssertionError("Old product MA hook: " + owner); }
                if (owner.endsWith("SharedMaMixin") && merged.desc.endsWith(")V")) { handlers++; }
            }
        }
        if (handlers != 4) { throw new AssertionError("Expected four single core stages, got " + handlers); }
    }

    public static boolean ready(String name) throws Exception {
        if (name.endsWith("$maRefundReady")) { name = "mismatchedthings$maRefundReady"; }
        var method = ItemStack.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return (boolean) method.invoke(null);
    }
}
