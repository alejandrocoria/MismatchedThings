package games.alejandrocoria.mismatchedleggings.test;

import com.autovw.advancednetherite.api.impl.IAdvancedHooks;
import com.autovw.advancednetherite.common.AdvancedUtil;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsData;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Loaded only by the pinned optional-mod profile; ordinary fixtures remain absent-safe. */
public final class AdvancedNetheriteChecks {
    public static final Identifier HOOK_ID = Identifier.parse("leggings_skeleton_test:advanced_hook_leggings");
    public static final ResourceKey<Item> HOOK_KEY = ResourceKey.create(Registries.ITEM, HOOK_ID);

    private AdvancedNetheriteChecks() {
    }

    public static Item hookItem(Item.Properties properties) {
        return new HookItem(properties.stacksTo(1).durability(16)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.LEGS).build()));
    }

    static ItemStack nativeLeggings(String tier) {
        var id = Identifier.fromNamespaceAndPath("advancednetherite", "netherite_" + tier + "_leggings");
        var item = BuiltInRegistries.ITEM.getValue(id);
        require(item != null && BuiltInRegistries.ITEM.getKey(item).equals(id), "Native leggings missing: " + id);
        return new ItemStack(item);
    }

    static void verify(MinecraftServer server) throws Exception {
        var player = RepairFixtures.player(server);
        for (String tier : List.of("iron", "gold", "emerald", "diamond")) {
            var source = nativeLeggings(tier);
            boolean piglins = tier.equals("gold") || tier.equals("diamond");
            boolean endermen = tier.equals("emerald") || tier.equals("diamond");
            boolean phantoms = tier.equals("iron") || tier.equals("diamond");
            answers(player, source, piglins, endermen, phantoms);
            for (var root : pair(source, new ItemStack(Items.IRON_LEGGINGS))) {
                require(root.getItem() instanceof IAdvancedHooks, "Optional item hook was not installed");
                answers(player, root, piglins, endermen, phantoms);
                upgrade(server, root, tier);
            }
        }
        for (var root : pair(nativeLeggings("emerald"), nativeLeggings("iron"))) {
            answers(player, root, false, true, true);
        }
        for (var root : pair(nativeLeggings("diamond"), nativeLeggings("diamond"))) {
            answers(player, root, true, true, true);
        }
        for (var root : pair(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS))) {
            answers(player, root, false, false, false);
        }
        for (int damage = 0; damage < 4; damage++) {
            var source = new ItemStack(BuiltInRegistries.ITEM.getValue(HOOK_ID));
            source.setDamageValue(damage);
            for (var root : pair(source, new ItemStack(Items.IRON_LEGGINGS))) {
                answers(player, root, false, (damage & 1) != 0, (damage & 2) != 0);
            }
        }
        invalid(player);
        reload(server, player);
    }

    private static List<ItemStack> pair(ItemStack left, ItemStack right) {
        var pair = MixedLeggingsFactory.createPair(left, right);
        return List.of(pair.primary(), pair.inverse());
    }

    private static void answers(ServerPlayer player, ItemStack stack, boolean piglins, boolean endermen, boolean phantoms) {
        var before = stack.copy();
        var data = MixedLeggingsStructure.validData(stack).orElse(null);
        // ItemStack.copy shares immutable component values: snapshot each source independently.
        var leftBefore = data == null ? ItemStack.EMPTY : data.left().source().copy();
        var rightBefore = data == null ? ItemStack.EMPTY : data.right().source().copy();
        for (var slot : EquipmentSlotGroup.ARMOR) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        player.setItemSlot(EquipmentSlot.LEGS, stack);
        require(PiglinAi.isWearingSafeArmor(player) == piglins, "Wrong equipped piglin answer: " + stack);
        require(AdvancedUtil.isWearingEndermanPassiveArmor(player) == endermen, "Wrong equipped enderman answer: " + stack);
        require(AdvancedUtil.isWearingPhantomPassiveArmor(player) == phantoms, "Wrong equipped phantom answer: " + stack);
        if (stack.getItem() instanceof IAdvancedHooks hooks) {
            require(hooks.pacifyEndermen(stack) == endermen && hooks.pacifyPhantoms(stack) == phantoms,
                    "Direct hook disagrees with native dispatch");
            for (String tag : List.of("pacify_piglins_armor", "pacify_endermen_armor", "pacify_phantoms_armor")) {
                require(!stack.is(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("advancednetherite", tag))),
                        "Projected a foreign tag instead of the supported hook: " + tag);
            }
        }
        require(ItemStack.matches(before, stack), "Native queries mutated root/source provenance");
        require(data == null || ItemStack.matches(leftBefore, data.left().source())
                && ItemStack.matches(rightBefore, data.right().source()), "Hook consumer mutated stored source snapshots");
    }

    private static void invalid(ServerPlayer player) {
        var root = pair(nativeLeggings("diamond"), new ItemStack(Items.IRON_LEGGINGS)).getFirst();
        var bare = new ItemStack(root.getItem());
        var malformed = root.copyWithCount(2);
        var suspended = root.copy();
        suspended.set(LeggingsContent.createSuspendedDataType(),
                SuspendedLeggingsData.of(new CompoundTag(), new CompoundTag(), new CompoundTag()));
        var unrelated = new ItemStack(Items.IRON_LEGGINGS);
        unrelated.set(LeggingsContent.mixedLeggingsDataType(), MixedLeggingsStructure.validData(root).orElseThrow());
        var hooks = (IAdvancedHooks) root.getItem();
        for (var invalid : List.of(bare, malformed, suspended, unrelated)) {
            require(!hooks.pacifyEndermen(invalid) && !hooks.pacifyPhantoms(invalid), "Invalid input granted optional perks");
            answers(player, invalid, false, false, false);
        }
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        player.getInventory().add(root);
        require(!AdvancedUtil.isWearingEndermanPassiveArmor(player)
                && !AdvancedUtil.isWearingPhantomPassiveArmor(player) && !PiglinAi.isWearingSafeArmor(player),
                "Inventory-only leggings granted equipped perks");
        player.getInventory().clearContent();
    }

    private static void upgrade(MinecraftServer server, ItemStack root, String tier) {
        if (tier.equals("diamond")) {
            return;
        }
        String next = tier.equals("iron") ? "gold" : tier.equals("gold") ? "emerald" : "diamond";
        var player = RepairFixtures.player(server);
        var before = root.copy();
        var menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
        menu.getSlot(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 2));
        menu.getSlot(1).set(root.copy());
        var addition = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("advancednetherite", "netherite_" + next + "_ingot"));
        menu.getSlot(2).set(new ItemStack(addition, 2));
        menu.createResult();
        var preview = menu.getSlot(3).getItem().copy();
        var data = MixedLeggingsStructure.validData(preview).orElseThrow();
        require(data.left().source().is(nativeLeggings(next).getItem()) || data.right().source().is(nativeLeggings(next).getItem()),
                "Native tier upgrade did not transform its source");
        var taken = menu.getSlot(3).safeTake(1, 1, player);
        require(ItemStack.matches(preview, taken) && !taken.isEmpty() && !menu.getSlot(1).hasItem()
                && menu.getSlot(0).getItem().getCount() == 1 && menu.getSlot(2).getItem().getCount() == 1,
                "Native tier upgrade payment/take failed");
        require(ItemStack.matches(root, before), "Smithing mutated the original root");
    }

    private static void reload(MinecraftServer server, ServerPlayer player) throws Exception {
        var roots = pair(nativeLeggings("diamond"), new ItemStack(Items.IRON_LEGGINGS));
        var originals = roots.stream().map(ItemStack::copy).toList();
        var selected = List.copyOf(server.getPackRepository().getSelectedIds());
        var pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("advanced-netherite-tags");
        var tags = pack.resolve("data/advancednetherite/tags/item");
        Files.createDirectories(tags);
        var format = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
        Files.writeString(pack.resolve("pack.mcmeta"), """
                {"pack":{"description":"Advanced Netherite tag regression","min_format":[%d,%d],"max_format":[%d,%d]}}
                """.formatted(format.major(), format.minor(), format.major(), format.minor()));
        for (String name : List.of("pacify_piglins_armor", "pacify_endermen_armor", "pacify_phantoms_armor")) {
            Files.writeString(tags.resolve(name + ".json"), "{\"replace\":true,\"values\":[]}");
        }
        server.getPackRepository().reload();
        var changed = new ArrayList<>(selected);
        changed.add("file/advanced-netherite-tags");
        try {
            server.reloadResources(changed).join();
            answers(player, nativeLeggings("diamond"), false, false, false);
            for (var root : roots) {
                answers(player, root, false, false, false);
            }
        } finally {
            server.reloadResources(selected).join();
        }
        for (int i = 0; i < roots.size(); i++) {
            answers(player, roots.get(i), true, true, true);
            require(ItemStack.matches(originals.get(i), roots.get(i)), "Reload rewrote a previously crafted pair");
        }
    }

    private static final class HookItem extends Item implements IAdvancedHooks {
        private HookItem(Properties properties) {
            super(properties);
        }

        @Override
        public boolean pacifyEndermen(ItemStack stack) {
            boolean result = (stack.getDamageValue() & 1) != 0;
            stack.setDamageValue(0); // Deliberately hostile consumer: source provenance must remain untouched.
            return result;
        }

        @Override
        public boolean pacifyPhantoms(ItemStack stack) {
            boolean result = (stack.getDamageValue() & 2) != 0;
            stack.setDamageValue(0);
            return result;
        }
    }
}
