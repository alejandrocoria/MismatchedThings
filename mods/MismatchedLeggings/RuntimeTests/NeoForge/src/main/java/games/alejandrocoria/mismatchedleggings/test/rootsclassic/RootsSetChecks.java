package games.alejandrocoria.mismatchedleggings.test.rootsclassic;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsData;
import games.alejandrocoria.mismatchedleggings.test.RootsWorkstationChecks;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import io.netty.buffer.Unpooled;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
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
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.List;
import java.util.function.Supplier;

/** Actual native staff spell outcomes/payment/use and event-bus Wildwood healing, not eligibility helpers. */
final class RootsSetChecks {
    private static final String VARIANT = System.getProperty("leggings.test.rootsTicks", "");
    private static int cases;

    private RootsSetChecks() {
    }

    static int run(ServerPlayer player) throws Exception {
        var sylvan = item("sylvan_tunic");
        var wildwood = item("wildwood_leggings");
        var s = MixedLeggingsFactory.createPair(sylvan, new ItemStack(Items.DIAMOND_LEGGINGS));
        var w = MixedLeggingsFactory.createPair(wildwood, new ItemStack(Items.DIAMOND_LEGGINGS));
        var sw = MixedLeggingsFactory.createPair(sylvan, wildwood);
        var ordinary = MixedLeggingsFactory.createPair(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS));
        for (boolean crystal : List.of(false, true)) {
            cast(player, crystal, sylvan, true, null, false, true);
            for (var legs : List.of(s.primary(), s.inverse(), sw.primary(), sw.inverse(),
                                    MixedLeggingsFactory.createPair(sylvan, sylvan).primary())) {
                boolean supported = !VARIANT.equals(crystal ? "crystal" : "staff");
                cast(player, crystal, legs, supported, null, false, true);
            }
            for (EquipmentSlot missing : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.FEET)) {
                cast(player, crystal, s.primary(), false, missing, false, true);
            }
            cast(player, crystal, w.primary(), false, null, false, true);
            cast(player, crystal, ordinary.primary(), false, null, false, true);
            // The independent HEAD fixture accepts a diamond helmet; LEGS must still contribute independently.
            cast(player, crystal, s.inverse(), !VARIANT.equals(crystal ? "crystal" : "staff"), null, true, true);
            cast(player, crystal, s.primary(), false, null, false, false);
            for (var invalid : invalid(s.primary())) {
                cast(player, crystal, invalid, false, null, false, true);
            }
        }
        heal(player, wildwood, true, null, 10, 0, 0);
        for (var legs : List.of(w.primary(), w.inverse(), sw.primary(), sw.inverse(),
                                MixedLeggingsFactory.createPair(wildwood, wildwood).primary())) {
            heal(player, legs, !VARIANT.equals("healing"), null, 10, 0, 0);
        }
        for (EquipmentSlot missing : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.FEET)) {
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
        if (VARIANT.equals("normal")) {
            repairedRootRoundTrip(player, s.primary());
            manaRoundTrip(player);
            RootsWorkstationChecks.verify(player.level().getServer());
        }
        clear(player);
        return cases;
    }

    private static void repairedRootRoundTrip(ServerPlayer player, ItemStack input) {
        clear(player);
        ItemStack root = input.copy();
        root.setDamageValue(10);
        try (var scope = RootsTickTestControl.begin(0)) {
            root.inventoryTick(player.level(), player, EquipmentSlot.LEGS);
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void manaRoundTrip(ServerPlayer player) throws Exception {
        Object mana = mana(player);
        mana.getClass().getMethod("setMaxMana", float.class).invoke(mana, 40F);
        mana.getClass().getMethod("setMana", float.class).invoke(mana, 17.5F);
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.level().registryAccess());
        IAttachmentSerializer serializer = (IAttachmentSerializer) mana;
        require(serializer.write(mana, output), "Native Terra serializer declined saved data");
        var saved = TagValueInput.create(ProblemReporter.DISCARDING, player.level().registryAccess(), output.buildResult());
        var restored = serializer.read(player, saved);
        require(((Number) restored.getClass().getMethod("getMana").invoke(restored)).floatValue() == 17.5F
                        && ((Number) restored.getClass().getMethod("getMaxMana").invoke(restored)).floatValue() == 40F,
                "Native Terra attachment lost its current value/capacity across serialization");
        cases++;
    }

    private static void cast(ServerPlayer player, boolean crystal, ItemStack legs, boolean bonus,
                             EquipmentSlot missing, boolean headFixture, boolean enoughMana) throws Exception {
        equip(player, "sylvan", legs, missing);
        if (headFixture) {
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        }

        var before = legs.copy();
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
                    "Native spell potency differs: " + crystal + "/" + bonus + "/" + legs);
            require(((Number) mana.getClass().getMethod("getMana").invoke(mana)).floatValue() == 24,
                    "Native spell payment changed");
        } else {
            require(regeneration == null && player.hasEffect(MobEffects.POISON), "Spell bypassed native Terra requirement");
        }
        if (!crystal) {
            Object uses = get(staff, "STAFF_USES");
            require((int) uses.getClass().getMethod("uses").invoke(uses) == 4, "Native staff use changed");
        }
        require(ItemStack.matches(before, legs), "Casting changed root/source data");
        cases++;
    }

    private static void heal(ServerPlayer player, ItemStack legs, boolean healed, EquipmentSlot missing,
                             float health, int food, int roll) {
        equip(player, "wildwood", legs, missing);
        player.setHealth(health);
        player.getFoodData().setFoodLevel(food);
        player.tickCount = 1; // Do not conflate periodic Terra or natural food regen with native set healing.
        ItemStack before = legs.copy();
        try (var scope = RootsTickTestControl.begin(roll)) {
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(player));
            require(player.getHealth() == health + (healed ? 1 : 0), "Native Wildwood healing differs: " + legs + "/" + missing);
        }
        require(ItemStack.matches(before, legs), "Healing changed root/source data");
        cases++;
    }

    private static List<ItemStack> invalid(ItemStack root) {
        ItemStack count = root.copy();
        count.setCount(2);
        ItemStack bare = root.copy();
        bare.remove(LeggingsContent.mixedLeggingsDataType());
        ItemStack spoof = new ItemStack(Items.STICK);
        spoof.applyComponents(root.getComponents());
        ItemStack slot = root.copy();
        slot.set(DataComponents.EQUIPPABLE,
                 net.minecraft.world.item.equipment.Equippable.builder(EquipmentSlot.HEAD).build());
        var suspended = root.copy();
        suspended.set(LeggingsContent.createSuspendedDataType(),
                SuspendedLeggingsData.of(new CompoundTag(), new CompoundTag(), new CompoundTag()));
        return List.of(count, bare, spoof, slot, suspended);
    }

    private static void equip(ServerPlayer player, String kind, ItemStack legs, EquipmentSlot missing) {
        clear(player);
        player.setItemSlot(EquipmentSlot.HEAD, item(kind + "_" + (kind.equals("sylvan") ? "hood" : "mask")));
        player.setItemSlot(EquipmentSlot.CHEST, item(kind + "_" + (kind.equals("sylvan") ? "robe" : "plate")));
        var boots = item(kind + "_boots");
        if (Boolean.getBoolean("leggings.test.boots")) {
            boots = BootsCoexistence.pair(boots, new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
        }
        player.setItemSlot(EquipmentSlot.FEET, boots);
        player.setItemSlot(EquipmentSlot.LEGS, legs);
        if (missing != null) {
            player.setItemSlot(missing, ItemStack.EMPTY);
        }
    }

    private static void clear(ServerPlayer player) {
        player.removeAllEffects();
        player.getInventory().clearContent();
        for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.FEET, EquipmentSlot.LEGS,
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
