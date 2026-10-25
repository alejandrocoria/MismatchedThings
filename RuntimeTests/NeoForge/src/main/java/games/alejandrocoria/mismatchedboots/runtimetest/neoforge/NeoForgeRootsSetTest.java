package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import io.netty.buffer.Unpooled;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.List;
import java.util.function.Supplier;

/** Actual native staff spell outcomes/payment/use and event-bus Wildwood healing, not eligibility helpers. */
final class NeoForgeRootsSetTest {
    private static final String VARIANT = System.getProperty("mismatchedboots.runtimetest.rootsTicks", "");
    private static int cases;

    private NeoForgeRootsSetTest() {
    }

    static int run(ServerPlayer player) throws Exception {
        var sylvan = item("sylvan_boots");
        var wildwood = item("wildwood_boots");
        var s = MixedBootsFactory.createPair(sylvan, new ItemStack(Items.DIAMOND_BOOTS));
        var w = MixedBootsFactory.createPair(wildwood, new ItemStack(Items.DIAMOND_BOOTS));
        var sw = MixedBootsFactory.createPair(sylvan, wildwood);
        var ordinary = MixedBootsFactory.createPair(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS));
        for (boolean crystal : List.of(false, true)) {
            cast(player, crystal, sylvan, true, null, false, true);
            for (var feet : List.of(s.primary(), s.inverse(), sw.primary(), sw.inverse(),
                                    MixedBootsFactory.createPair(sylvan, sylvan).primary())) {
                boolean supported = !VARIANT.equals(crystal ? "crystal" : "staff");
                cast(player, crystal, feet, supported, null, false, true);
            }
            for (EquipmentSlot missing : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS)) {
                cast(player, crystal, s.primary(), false, missing, false, true);
            }
            cast(player, crystal, w.primary(), false, null, false, true);
            cast(player, crystal, ordinary.primary(), false, null, false, true);
            // The independent HEAD fixture accepts a diamond helmet; FEET must still contribute independently.
            cast(player, crystal, s.inverse(), !VARIANT.equals(crystal ? "crystal" : "staff"), null, true, true);
            cast(player, crystal, s.primary(), false, null, false, false);
            for (var invalid : invalid(s.primary())) {
                cast(player, crystal, invalid, false, null, false, true);
            }
        }
        heal(player, wildwood, true, null, 10, 0, 0);
        for (var feet : List.of(w.primary(), w.inverse(), sw.primary(), sw.inverse(),
                                MixedBootsFactory.createPair(wildwood, wildwood).primary())) {
            heal(player, feet, !VARIANT.equals("healing"), null, 10, 0, 0);
        }
        for (EquipmentSlot missing : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS)) {
            heal(player, w.primary(), false, missing, 10, 0, 0);
        }
        heal(player, s.primary(), false, null, 10, 0, 0);
        heal(player, ordinary.primary(), false, null, 10, 0, 0);
        heal(player, w.primary(), false, null, 10, 20, 1);
        heal(player, w.primary(), false, null, 20, 0, 0);
        heal(player, w.primary(), !VARIANT.equals("healing"), null, 10, 20, 0);
        for (var invalid : invalid(w.primary())) {
            heal(player, invalid, false, null, 10, 0, 0);
        }
        if (VARIANT.equals("live")) {
            repairedRootRoundTrip(player, s.primary());
        }
        clear(player);
        return cases;
    }

    private static void repairedRootRoundTrip(ServerPlayer player, ItemStack input) {
        clear(player);
        ItemStack root = input.copy();
        root.setDamageValue(10);
        try (var scope = RootsTickTestControl.begin(0)) {
            root.inventoryTick(player.level(), player, EquipmentSlot.FEET);
        }
        require(root.getDamageValue() == 9, "Round-trip subject did not undergo native root repair");
        ItemStack expected = root.copy();
        player.getInventory().setItem(10, root);
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.level().registryAccess());
        player.getInventory().save(output.list("Inventory", ItemStackWithSlot.CODEC));
        player.getInventory().clearContent();
        player.getInventory().load(TagValueInput.create(ProblemReporter.DISCARDING, player.level().registryAccess(), output.buildResult())
                                                .listOrEmpty("Inventory", ItemStackWithSlot.CODEC));
        require(ItemStack.matches(expected, player.getInventory().getItem(10)), "Inventory save/load lost repaired root or sources");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.level().registryAccess());
        try {
            ClientboundContainerSetSlotPacket.STREAM_CODEC.encode(buffer, new ClientboundContainerSetSlotPacket(0, 1, 10, root));
            require(ItemStack.matches(expected, ClientboundContainerSetSlotPacket.STREAM_CODEC.decode(buffer).getItem()),
                    "Ordinary slot packet lost repaired root or sources");
        } finally {
            buffer.release();
        }
        cases++;
    }

    private static void cast(ServerPlayer player, boolean crystal, ItemStack feet, boolean bonus,
                             EquipmentSlot missing, boolean headFixture, boolean enoughMana) throws Exception {
        equip(player, "sylvan", feet, missing);
        if (headFixture) {
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        }
        var before = feet.copy();
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
        Object mana = mana(player);
        mana.getClass().getMethod("setMaxMana", float.class).invoke(mana, 40F);
        mana.getClass().getMethod("setMana", float.class).invoke(mana, enoughMana ? 40F : 0F);
        ItemStack staff = item(crystal ? "crystal_staff" : "staff");
        Object spell = Class.forName("elucent.rootsclassic.datacomponent.SpellData")
                            .getConstructor(int.class, int.class, int.class, String.class).newInstance(2, 0, 0, "rootsclassic:apple");
        if (crystal) {
            NonNullList<Object> spells = NonNullList.create();
            spells.add(spell);
            Object data = Class.forName("elucent.rootsclassic.datacomponent.SpellDataList")
                               .getConstructor(NonNullList.class).newInstance(spells);
            set(staff, "SPELLS", data);
            set(staff, "SELECTED_SPELL", 1);
        } else {
            set(staff, "SPELL", spell);
            set(staff, "STAFF_USES", Class.forName("elucent.rootsclassic.datacomponent.StaffUses")
                                          .getConstructor(int.class, int.class).newInstance(5, 5));
        }
        player.setItemSlot(EquipmentSlot.MAINHAND, staff);
        staff.getItem().releaseUsing(staff, player.level(), player, 71980);
        var regeneration = player.getEffect(MobEffects.REGENERATION);
        if (enoughMana) {
            require(regeneration != null && regeneration.getAmplifier() == 2 + (crystal ? 1 : 0) + (bonus ? 1 : 0),
                    "Native spell potency differs: " + crystal + "/" + bonus + "/" + feet);
            require(((Number) mana.getClass().getMethod("getMana").invoke(mana)).floatValue() == 24,
                    "Native spell payment changed");
        } else {
            require(regeneration == null && player.hasEffect(MobEffects.POISON), "Spell bypassed native Terra requirement");
        }
        if (!crystal) {
            Object uses = get(staff, "STAFF_USES");
            require((int) uses.getClass().getMethod("uses").invoke(uses) == 4, "Native staff use changed");
        }
        require(ItemStack.matches(before, feet), "Casting changed root/source data");
        cases++;
    }

    private static void heal(ServerPlayer player, ItemStack feet, boolean healed, EquipmentSlot missing,
                             float health, int food, int roll) {
        equip(player, "wildwood", feet, missing);
        player.setHealth(health);
        player.getFoodData().setFoodLevel(food);
        player.tickCount = 1; // Do not conflate periodic Terra or natural food regen with native set healing.
        ItemStack before = feet.copy();
        try (var scope = RootsTickTestControl.begin(roll)) {
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(player));
            require(player.getHealth() == health + (healed ? 1 : 0), "Native Wildwood healing differs: " + feet + "/" + missing);
        }
        require(ItemStack.matches(before, feet), "Healing changed root/source data");
        cases++;
    }

    private static List<ItemStack> invalid(ItemStack root) {
        ItemStack count = root.copy();
        count.setCount(2);
        ItemStack bare = root.copy();
        bare.remove(ModContent.mixedBootsDataType().orElseThrow());
        ItemStack spoof = new ItemStack(Items.STICK);
        spoof.applyComponents(root.getComponents());
        ItemStack slot = root.copy();
        slot.set(DataComponents.EQUIPPABLE,
                 net.minecraft.world.item.equipment.Equippable.builder(EquipmentSlot.HEAD).build());
        return List.of(count, bare, spoof, slot);
    }

    private static void equip(ServerPlayer player, String kind, ItemStack feet, EquipmentSlot missing) {
        clear(player);
        player.setItemSlot(EquipmentSlot.HEAD, item(kind + "_" + (kind.equals("sylvan") ? "hood" : "mask")));
        player.setItemSlot(EquipmentSlot.CHEST, item(kind + "_" + (kind.equals("sylvan") ? "robe" : "plate")));
        player.setItemSlot(EquipmentSlot.LEGS, item(kind + "_" + (kind.equals("sylvan") ? "tunic" : "leggings")));
        player.setItemSlot(EquipmentSlot.FEET, feet);
        if (missing != null) {
            player.setItemSlot(missing, ItemStack.EMPTY);
        }
    }

    private static void clear(ServerPlayer player) {
        player.removeAllEffects();
        player.getInventory().clearContent();
        for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    private static ItemStack item(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("rootsclassic", name)).orElseThrow());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object mana(ServerPlayer player) throws Exception {
        return player.getData((Supplier) Class.forName("elucent.rootsclassic.attachment.RootsAttachments").getField("MANA").get(null));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void set(ItemStack stack, String name, Object value) throws Exception {
        var type = (DataComponentType) ((Supplier) Class.forName("elucent.rootsclassic.registry.RootsComponents").getField(name).get(null)).get();
        stack.set(type, value);
    }

    private static Object get(ItemStack stack, String name) throws Exception {
        var supplier = (Supplier<?>) Class.forName("elucent.rootsclassic.registry.RootsComponents").getField(name).get(null);
        return stack.get((DataComponentType<?>) supplier.get());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
