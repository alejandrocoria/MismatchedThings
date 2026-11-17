package games.alejandrocoria.mismatchedleggings.test.silentgear;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.data.MixedLeggingsData;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import games.alejandrocoria.mismatchedleggings.test.WorkbenchChecks;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.fml.ModList;
import net.silentchaos512.gear.api.item.GearItem;
import net.silentchaos512.gear.api.traits.TraitInstance;
import net.silentchaos512.gear.api.util.DataResource;
import net.silentchaos512.gear.gear.part.PartInstance;
import net.silentchaos512.gear.setup.SgDataComponents;
import net.silentchaos512.gear.util.GearData;
import net.silentchaos512.gear.util.TraitHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Real native materials and paid roots; the candidate changes only Leggings' cure dispatcher. */
public final class AtmCompatChecks {
    private static final BlockPos STORAGE = new BlockPos(0, 2, 0);
    private static boolean finished;

    private AtmCompatChecks() {}

    public static void tick(MinecraftServer server) {
        if (finished || server.getTickCount() < 25) { return; }
        finished = true;
        var report = new StringBuilder();
        try {
            for (String mod : List.of("mismatchedboots", "mismatchedleggings", "mismatchedthings", "atmcompat", "allthemodium", "silentgear")) {
                require(ModList.get().isLoaded(mod), "Missing actual publication: " + mod);
            }
            var player = FoundationChecks.player(server);
            if (System.getProperty("atm.spike.stage").equals("reopen")) {
                reopen(server, player, report);
            } else {
                write(server, player, report);
            }
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "materials.txt"), report);
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            SkeletonChecks.failed("server", failure);
        } finally {
            server.halt(false);
        }
    }

    private static void write(MinecraftServer server, ServerPlayer player, StringBuilder report) throws Exception {
        var stored = new ArrayList<ItemStack>();
        for (String material : List.of("allthemodium", "vibranium", "unobtainium")) {
            var plate = BuiltInRegistries.ITEM.getValue(Identifier.parse("allthemodium:silent_" + material + "_plate"));
            require(plate != null && plate != Items.AIR, "Missing real registered Silent plate: " + material);
            for (var slot : List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS)) {
                var source = armor(material, slot);
                require(source.has(SgDataComponents.GEAR_PROPERTIES.get()) && source.has(SgDataComponents.GEAR_CONSTRUCTION.get()),
                        "Native construction did not populate actual parts/properties");
                var traits = TraitHelper.getTraits(source).stream().map(t -> t.getTraitId().toString()).toList();
                require(traits.containsAll(List.of("silentgear:brilliant", "silentgear:malleable", "silentgear:crushing")),
                        "Wrong native material traits: " + material + ' ' + traits);
                require(traits.contains("silentgear:cure_nausea") == (slot == EquipmentSlot.LEGS && !material.equals("allthemodium")),
                        "Native Nausea assignment changed");
                require(traits.contains("silentgear:cure_levitation") == (slot == EquipmentSlot.LEGS && material.equals("unobtainium")),
                        "Native Levitation assignment changed");
                var original = source.copy();
                var vanilla = new ItemStack(slot == EquipmentSlot.FEET ? Items.DIAMOND_BOOTS : Items.DIAMOND_LEGGINGS);
                var roots = paid(player, source, vanilla, slot);
                double nativeArmor = worn(player, source, slot, Attributes.ARMOR);
                double nativeToughness = worn(player, source, slot, Attributes.ARMOR_TOUGHNESS);
                double vanillaArmor = worn(player, vanilla, slot, Attributes.ARMOR);
                double vanillaToughness = worn(player, vanilla, slot, Attributes.ARMOR_TOUGHNESS);
                for (var root : roots) {
                    require(root.getMaxDamage() == ((long) source.getMaxDamage() + vanilla.getMaxDamage()) / 2,
                            "Material durability did not use the ordinary average");
                    close(worn(player, root, slot, Attributes.ARMOR), (nativeArmor + vanillaArmor) / 2, "Effective armor");
                    close(worn(player, root, slot, Attributes.ARMOR_TOUGHNESS), (nativeToughness + vanillaToughness) / 2, "Effective toughness");
                    require(PiglinAi.isWearingSafeArmor(player), "Actual SG Brilliant missing on worn mixed " + slot);
                    var provenance = sources(root);
                    root.setDamageValue(root.getMaxDamage() / 2);
                    player.tickCount = 21;
                    root.inventoryTick(player.level(), player, slot);
                    var projected = original.copy();
                    projected.setDamageValue((int) ((long) root.getDamageValue() * original.getMaxDamage() / root.getMaxDamage()));
                    GearData.recalculateGearData(projected, player);
                    close(worn(player, root, slot, Attributes.ARMOR), (worn(player, projected, slot, Attributes.ARMOR) + vanillaArmor) / 2,
                            "Native Crushing live projection");
                    var after = sources(root);
                    require(ItemStack.matches(provenance.getFirst(), after.getFirst()) && ItemStack.matches(provenance.getLast(), after.getLast()),
                            "Live material projection changed source provenance");
                    roundtrip(server, root);
                }
                require(ItemStack.matches(source, original), "Paid composition/query changed the input");
                if (slot == EquipmentSlot.LEGS) {
                    boolean nausea = !material.equals("allthemodium");
                    boolean levitation = material.equals("unobtainium");
                    cures(player, source.copy(), nausea, nausea, levitation);
                    boolean candidate = !System.getProperty("atm.spike.mode").equals("baseline");
                    for (var root : roots) {
                        cures(player, root, nausea, candidate && nausea, candidate && levitation);
                        for (var selected : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
                            cures(player, root, selected, nausea, candidate && nausea, candidate && levitation);
                        }
                        cures(player, root, null, false, false, false);
                    }
                    report.append(material).append(": native Wither/Nausea/Levitation=").append(nausea).append('/').append(nausea).append('/').append(levitation)
                            .append("; mixed=").append(nausea).append('/').append(candidate && nausea).append('/').append(candidate && levitation)
                            .append("; both mixed orientations measured in worn/held/inactive slots\n");
                }
                stored.add(original);
                stored.addAll(roots);
                report.append(material).append('/').append(slot).append(": paid pair; native traits; average attributes/durability; live Crushing; Brilliant; immutable sources; codec\n");
            }
        }
        // Ordinary SG and native ATM remain independent positive controls, not material substitutes.
        worn(player, armor("gold", EquipmentSlot.LEGS), EquipmentSlot.LEGS, Attributes.ARMOR);
        require(PiglinAi.isWearingSafeArmor(player), "Ordinary SG gold Brilliant regressed");
        var nativeAtm = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("allthemodium:allthemodium_leggings")));
        worn(player, nativeAtm, EquipmentSlot.LEGS, Attributes.ARMOR);
        require(PiglinAi.isWearingSafeArmor(player), "Native ATM piglin control regressed");
        if (System.getProperty("atm.spike.mode").equals("refusal")) {
            refusal(player, stored.get(rootsStart(2)), report);
        }
        clear(player);
        var level = server.overworld();
        level.setChunkForced(0, 0, true);
        level.setBlockAndUpdate(STORAGE, Blocks.BARREL.defaultBlockState());
        var barrel = (BarrelBlockEntity) level.getBlockEntity(STORAGE);
        var expected = new JsonArray();
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (int index = 0; index < stored.size(); index++) {
            barrel.setItem(index, stored.get(index));
            expected.add(ItemStack.CODEC.encodeStart(ops, stored.get(index)).getOrThrow());
        }
        barrel.setChanged();
        Files.writeString(Path.of(System.getProperty("atm.spike.expected")), expected.toString());
        report.append("Saved 18 real native/mixed stacks in the world barrel for a separate game JVM\n");
    }

    private static int rootsStart(int material) {
        return material * 6 + 4; // Native boots + pair, then native leggings + first paid root.
    }

    private static void reopen(MinecraftServer server, ServerPlayer player, StringBuilder report) throws Exception {
        server.overworld().getChunk(0, 0);
        var barrel = (BarrelBlockEntity) server.overworld().getBlockEntity(STORAGE);
        require(barrel != null, "Native saved container was not restored");
        var expected = JsonParser.parseString(Files.readString(Path.of(System.getProperty("atm.spike.expected")))).getAsJsonArray();
        require(expected.size() == 18, "Incomplete expected material set");
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (int index = 0; index < expected.size(); index++) {
            var stack = barrel.getItem(index);
            require(ItemStack.matches(stack, ItemStack.CODEC.parse(ops, expected.get(index)).getOrThrow()),
                    "Native container changed complete material/root state at " + index);
            var slot = index % 6 < 3 ? EquipmentSlot.FEET : EquipmentSlot.LEGS;
            worn(player, stack, slot, Attributes.ARMOR);
            require(PiglinAi.isWearingSafeArmor(player), "Restored material Brilliant missing");
            roundtrip(server, stack);
        }
        report.append("Separate-JVM native container load restored all 18 full component stacks and Brilliant\n");
    }

    private static ItemStack armor(String material, EquipmentSlot slot) throws Exception {
        if (slot == EquipmentSlot.LEGS) { return FoundationChecks.armor(material); }
        return ((GearItem) BuiltInRegistries.ITEM.getValue(Identifier.parse("silentgear:boots"))).construct(
                List.of(PartInstance.from(FoundationChecks.part("boot_plates", material))));
    }

    private static List<ItemStack> paid(ServerPlayer player, ItemStack source, ItemStack vanilla, EquipmentSlot slot) {
        if (slot == EquipmentSlot.LEGS) { return FoundationChecks.paid(player, source, vanilla); }
        var pair = MixedBootsFactory.createPair(source, vanilla);
        var roots = List.of(pair.primary(), pair.inverse());
        for (int selected : List.of(2, 3)) {
            clear(player);
            var menu = new WorkbenchMenu(402, player.getInventory());
            menu.getSlot(0).set(source.copy());
            menu.getSlot(1).set(vanilla.copy());
            menu.slotsChanged(menu.getSlot(0).container);
            require(ItemStack.matches(menu.getSlot(2).getItem(), roots.getFirst()) && ItemStack.matches(menu.getSlot(3).getItem(), roots.getLast()),
                    "Boot material preview differs from its factory");
            menu.clicked(selected, 0, ContainerInput.PICKUP, player);
            require(ItemStack.matches(menu.getCarried(), roots.get(selected - 2)) && menu.getSlot(0).getItem().isEmpty()
                    && menu.getSlot(1).getItem().isEmpty(), "Boot material paid extraction/conservation failed");
            menu.setCarried(ItemStack.EMPTY);
            menu.removed(player);
            require(WorkbenchChecks.inventory(player).size() == 1
                            && WorkbenchChecks.contains(player, roots.get(selected == 2 ? 1 : 0)),
                    "Boot material closing lost or duplicated its paid companion");
        }
        clear(player);
        return roots;
    }

    private static List<ItemStack> sources(ItemStack root) {
        for (var component : root.getComponents()) {
            if (component.value() instanceof MixedBootsData data) { return List.of(data.left().source(), data.right().source()); }
            if (component.value() instanceof MixedLeggingsData data) { return List.of(data.left().source(), data.right().source()); }
        }
        throw new AssertionError("Missing actual mixed provenance");
    }

    private static void roundtrip(MinecraftServer server, ItemStack stack) {
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        require(ItemStack.matches(stack, ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, stack).getOrThrow()).getOrThrow()),
                "Actual material/root item codec lost data");
    }

    private static double worn(ServerPlayer player, ItemStack stack, EquipmentSlot slot, Holder<Attribute> attribute) throws Exception {
        clear(player);
        player.setItemSlot(slot, stack);
        var detect = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates");
        detect.setAccessible(true);
        detect.invoke(player);
        // Sum installed modifiers, not the clamped displayed value: these endgame materials exceed vanilla caps.
        return player.getAttribute(attribute).getModifiers().stream()
                .filter(modifier -> modifier.operation() == AttributeModifier.Operation.ADD_VALUE).mapToDouble(AttributeModifier::amount).sum();
    }

    private static void clear(ServerPlayer player) {
        for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
        player.getInventory().clearContent();
        player.removeAllEffects();
    }

    private static void cures(ServerPlayer player, ItemStack stack, boolean wither, boolean nausea, boolean levitation) {
        cures(player, stack, EquipmentSlot.LEGS, wither, nausea, levitation);
    }

    private static void cures(ServerPlayer player, ItemStack stack, EquipmentSlot slot, boolean wither, boolean nausea, boolean levitation) {
        clear(player);
        for (var effect : List.of(MobEffects.WITHER, MobEffects.NAUSEA, MobEffects.LEVITATION, MobEffects.POISON, MobEffects.LUCK)) {
            require(player.addEffect(new MobEffectInstance(effect, 100)), "Potion positive setup failed");
        }
        if (slot == null) { player.getInventory().setItem(8, stack); }
        else { player.setItemSlot(slot, stack); }
        var before = stack.copy();
        player.tickCount = 11;
        stack.inventoryTick(player.level(), player, slot);
        require(player.hasEffect(MobEffects.WITHER) != wither && player.hasEffect(MobEffects.NAUSEA) != nausea
                        && player.hasEffect(MobEffects.LEVITATION) != levitation && player.hasEffect(MobEffects.POISON)
                        && player.hasEffect(MobEffects.LUCK), "Native/mixed material cure mismatch in " + slot);
        require(ItemStack.matches(before, stack), "Cure changed complete native/root state");
    }

    private static void refusal(ServerPlayer player, ItemStack root, StringBuilder report) throws Exception {
        var definition = TraitInstance.of(DataResource.trait("silentgear:cure_nausea"), 1).getTrait();
        Object cancellation = definition.getEffects().getFirst();
        var field = cancellation.getClass().getDeclaredField("effects");
        field.setAccessible(true);
        Object original = field.get(cancellation);
        try {
            field.set(cancellation, List.of(MobEffects.LUCK));
            cures(player, root, true, false, true);
            cures(player, root, true, false, true);
        } finally { field.set(cancellation, original); }
        cures(player, root, true, false, true);
        report.append("Changed Nausea target refused once; Wither/Levitation and unrelated Luck remain independent\n");
    }

    private static void close(double actual, double expected, String label) {
        require(Math.abs(actual - expected) < 1E-6, label + ": " + actual + " != " + expected);
    }
}
