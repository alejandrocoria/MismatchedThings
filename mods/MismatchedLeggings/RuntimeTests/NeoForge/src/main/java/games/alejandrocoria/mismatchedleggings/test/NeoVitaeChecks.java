package games.alejandrocoria.mismatchedleggings.test;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Native recognition/direct-root regression with separate composition and saved-world lifecycle evidence. */
public final class NeoVitaeChecks {
    private static final TagKey<Item> SET = tag("sentient_upgrade_set/sentient");
    private static final TagKey<Item> CHANGE = tag("sentient_upgrade_set");
    private static final TagKey<Item> INFUSION = tag("spiritus_capable");
    private static final BlockPos FORGE = new BlockPos(10, 80, 10);
    private static boolean finished;

    private NeoVitaeChecks() {}

    public static void tick(MinecraftServer server) {
        if (finished || !server.isDedicatedServer() || server.getTickCount() < 20) {
            return;
        }
        finished = true;
        try {
            require(ModList.get().isLoaded("neovitae"), "Missing pinned Neo Vitae fixture");
            if (Boolean.getBoolean("leggings.test.neoVitaeRestart")) {
                NeoVitaeLifecycleChecks.restart(server);
                SkeletonChecks.passed("server");
                return;
            }
            var api = new NativeApi();
            var nativeLeggings = item("sentient_leggings");
            var pair = MixedLeggingsFactory.createPair(nativeLeggings, new ItemStack(Items.IRON_LEGGINGS));
            for (var root : List.of(pair.primary(), pair.inverse())) {
                require(root.is(SET) && root.is(CHANGE) && root.is(INFUSION), "Missing native behavior tag");
                set(server, root);
                direct(server, root, api);
            }

            var plain = MixedLeggingsFactory.createPair(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS));
            require(!plain.primary().is(SET) && !plain.inverse().is(CHANGE), "Ordinary armor gained Sentient membership");
            for (var root : List.of(plain.primary(), plain.inverse())) {
                require(root.is(INFUSION), "Generic armor infusion incorrectly requires a Sentient source");
                direct(server, root, api);
            }
            direct(server, nativeLeggings, api);

            var invalid = new ItemStack(BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID));
            require(!invalid.is(SET) && !invalid.is(CHANGE) && !invalid.is(INFUSION), "Bare invalid root gained source tags");
            NeoVitaeCompositionChecks.verify();
            NeoVitaeLifecycleChecks.save(server);
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "neovitae-server.txt"),
                    "Native full-set and equipment-change gates: both orientations, inventory/removal/near-broken-chest controls; Boots="
                            + ModList.get().isLoaded("mismatchedboots") + "\n"
                            + "Paid native infusion/Blood Mending, generic non-Sentient roots and native leggings control; "
                            + "root-only simulated/committed fill, incompatible type refusal, drain and empty retyping; immutable history\n"
                            + "Composition/lifecycle/reopen evidence is separate; this marker excludes equipped EV repair and client presentation\n");
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            SkeletonChecks.failed("server", failure);
        } finally {
            server.halt(false);
        }
    }

    private static void set(MinecraftServer server, ItemStack root) throws Exception {
        var player = FakePlayerFactory.getMinecraft(server.overworld());
        var helper = Class.forName("com.breakinblocks.neovitae.common.sentient.SentientHelper");
        var fullSet = helper.getMethod("hasFullSet", Player.class);
        var changed = Class.forName("com.breakinblocks.neovitae.common.sentient.SentientEventHandler")
                .getMethod("onEquipmentChange", LivingEquipmentChangeEvent.class);
        try {
            var chest = item("sentient_plate");
            player.setItemSlot(EquipmentSlot.HEAD, item("sentient_helmet"));
            player.setItemSlot(EquipmentSlot.CHEST, chest);
            player.setItemSlot(EquipmentSlot.FEET, item("sentient_boots"));
            player.setItemSlot(EquipmentSlot.LEGS, item("sentient_leggings"));
            require((boolean) fullSet.invoke(null, player), "Native Sentient set control failed");
            player.setItemSlot(EquipmentSlot.LEGS, root);
            require((boolean) fullSet.invoke(null, player), "Native set did not recognize mixed leggings");

            if (ModList.get().isLoaded("mismatchedboots")) {
                var pair = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory")
                        .getMethod("createPair", ItemStack.class, ItemStack.class)
                        .invoke(null, item("sentient_boots"), new ItemStack(Items.IRON_BOOTS));
                for (String orientation : List.of("primary", "inverse")) {
                    var boots = (ItemStack) pair.getClass().getMethod(orientation).invoke(pair);
                    player.setItemSlot(EquipmentSlot.FEET, boots);
                    require((boolean) fullSet.invoke(null, player), "Set requires original boots despite both mixed products");
                }
            }

            // The broad tag gates equipment-change recalculation independently of the required-set tag.
            chest.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            changed.invoke(null, new LivingEquipmentChangeEvent(player, EquipmentSlot.LEGS, ItemStack.EMPTY, root));
            require(!chest.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().isEmpty(),
                    "Native equipment-change handler did not rebuild chest attributes");
            chest.setDamageValue(chest.getMaxDamage() - 1);
            require(!(boolean) fullSet.invoke(null, player), "Mixed armor bypassed native chest integrity requirement");
            chest.setDamageValue(0);
            player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            player.getInventory().setItem(0, root.copy());
            require(!(boolean) fullSet.invoke(null, player), "Unequipped mixed leggings still completed the set");
        } finally {
            player.getInventory().clearContent();
            for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    private static void direct(MinecraftServer server, ItemStack target, NativeApi api) throws Exception {
        var original = target.copy();
        var history = MixedLeggingsStructure.validData(target);
        var gem = item("spiritus_gem_petty");
        double maximum = (double) api.maximum.invoke(null, gem);
        var infused = api.forge(server, List.of(target, gem), 0);
        require(infused.is(target.getItem()) && Double.valueOf(maximum).equals(infused.get(api.capacity))
                        && Double.valueOf(0).equals(infused.get(api.amount)) && api.raw.equals(infused.get(api.type)),
                "Native infusion did not install an empty, gem-sized root container");
        var marked = api.forge(server, List.of(infused, item("tabula_robur"),
                new ItemStack(Items.LAPIS_LAZULI), new ItemStack(Items.NETHER_WART)), 200);
        require(Boolean.TRUE.equals(marked.get(api.blood)) && Double.valueOf(maximum).equals(marked.get(api.capacity)),
                "Native Blood Mending failed or replaced the infusion");

        var empty = marked.copy();
        double quantity = Math.min(16D, maximum / 2);
        require(api.fill(marked, api.raw, quantity, false) == quantity && ItemStack.matches(marked, empty),
                "Simulated native fill mutated the live root");
        require(api.fill(marked, api.raw, quantity, true) == quantity && Double.valueOf(quantity).equals(marked.get(api.amount)),
                "Native fill did not update root charge");
        require(api.fill(marked, api.ruina, quantity, true) == 0 && Double.valueOf(quantity).equals(marked.get(api.amount)),
                "Native fill converted an incompatible positive balance");
        require((double) api.drain.invoke(null, marked, api.raw, quantity, true) == quantity,
                "Native drain failed");
        require(api.fill(marked, api.ruina, quantity, true) == quantity && api.ruina.equals(marked.get(api.type)),
                "Empty native root could not change Spiritus type");
        require(history.equals(MixedLeggingsStructure.validData(marked)) && ItemStack.matches(original, target),
                "Direct native operations changed source history or their input preview");
    }

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("neovitae", path));
    }

    static ItemStack item(String path) {
        var item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("neovitae", path));
        require(item != Items.AIR, "Missing native item: " + path);
        return new ItemStack(item);
    }

    @SuppressWarnings("unchecked")
    private static <T> DataComponentType<T> component(String path) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .getOptional(Identifier.fromNamespaceAndPath("neovitae", path)).orElseThrow();
    }

    /** Same pinned reflection boundary as Boots' lifecycle fixture; matching, progress and payment stay native. */
    static final class NativeApi {
        final DataComponentType<Double> capacity = component("spiritus_max");
        final DataComponentType<Double> amount = component("spiritus_amount");
        final DataComponentType<Object> type = component("spiritus_type");
        final DataComponentType<Boolean> blood = component("blood_mending");
        final Object raw = type.codec().parse(JsonOps.INSTANCE, new JsonPrimitive("raw")).getOrThrow();
        final Object ruina = type.codec().parse(JsonOps.INSTANCE, new JsonPrimitive("ruina")).getOrThrow();
        final Method maximum;
        final Method fill;
        final Method drain;
        final Class<?> forgeClass;
        final Method tick;
        final Method getSlot;
        final Method setSlot;

        NativeApi() throws ReflectiveOperationException {
            var spiritus = Class.forName("com.breakinblocks.neovitae.common.datacomponent.SpiritusType");
            var helper = Class.forName("com.breakinblocks.neovitae.spiritus.SpiritusHelper");
            maximum = helper.getMethod("resolveMaxSpiritus", ItemStack.class);
            fill = helper.getMethod("fillSpiritus", ItemStack.class, spiritus, double.class, boolean.class);
            drain = helper.getMethod("drainSpiritus", ItemStack.class, spiritus, double.class, boolean.class);
            forgeClass = Class.forName("com.breakinblocks.neovitae.common.blockentity.HellfireForgeBlockEntity");
            tick = forgeClass.getMethod("tick", Level.class, BlockPos.class, BlockState.class, forgeClass);
            var inventory = Class.forName(forgeClass.getName() + "$Inv");
            getSlot = inventory.getMethod("getStackInSlot", int.class);
            setSlot = inventory.getMethod("setStackInSlot", int.class, ItemStack.class);
        }

        double fill(ItemStack root, Object aspect, double quantity, boolean execute) throws ReflectiveOperationException {
            return (double) fill.invoke(null, root, aspect, quantity, execute);
        }

        double drain(ItemStack root, Object aspect, double quantity, boolean execute) throws ReflectiveOperationException {
            return (double) drain.invoke(null, root, aspect, quantity, execute);
        }

        ItemStack forge(MinecraftServer server, List<ItemStack> ingredients, double cost) throws Exception {
            var level = server.overworld();
            var block = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("neovitae", "hellfire_forge"));
            require(block != Blocks.AIR, "Missing native forge block");
            level.setBlock(FORGE, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(FORGE, block.defaultBlockState(), 3);
            Object forge = level.getBlockEntity(FORGE);
            require(forgeClass.isInstance(forge), "Missing native forge entity");
            Object inventory = forgeClass.getField("inv").get(forge);
            for (int index = 0; index < ingredients.size(); index++) {
                setSlot.invoke(inventory, index, ingredients.get(index).copy());
            }
            var fuel = item("spiritus_gem_common");
            double full = (double) maximum.invoke(null, fuel);
            require(fill(fuel, raw, full, true) == full, "Could not seed native fuel");
            setSlot.invoke(inventory, 4, fuel);
            for (int step = 0; step <= 100 && stack(inventory, 5).isEmpty(); step++) {
                tick.invoke(null, level, FORGE, level.getBlockState(FORGE), forge);
            }
            var result = stack(inventory, 5).copy();
            require(!result.isEmpty() && Double.valueOf(full - cost).equals(stack(inventory, 4).get(amount)),
                    "Native forge failed to complete or pay expected fuel");
            for (int index = 0; index < 4; index++) {
                require(stack(inventory, index).isEmpty(), "Native forge did not consume its ingredients");
            }
            setSlot.invoke(inventory, 5, ItemStack.EMPTY);
            setSlot.invoke(inventory, 4, ItemStack.EMPTY);
            return result;
        }

        private ItemStack stack(Object inventory, int slot) throws ReflectiveOperationException {
            return (ItemStack) getSlot.invoke(inventory, slot);
        }
    }
}
