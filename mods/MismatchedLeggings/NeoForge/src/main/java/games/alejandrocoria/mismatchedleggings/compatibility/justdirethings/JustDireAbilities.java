package games.alejandrocoria.mismatchedleggings.compatibility.justdirethings;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Select one native callback, never an entire native armor tick or a historical source wallet. */
public final class JustDireAbilities {
    private static volatile Api api;
    private static volatile boolean movementFailed;

    private JustDireAbilities() {}

    public static void initialize(IEventBus bus) {
        if (!ModList.get().isLoaded("justdirethings")) { return; }
        bus.addListener((RegisterCapabilitiesEvent event) -> bind());
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Pre event) -> {
            var player = event.getEntity();
            if (player.level().isClientSide() && player.isLocalPlayer()) { movement(player); }
        });
    }

    private static void bind() {
        try {
            var lookup = MethodHandles.publicLookup();
            var tool = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
            var ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
            var params = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.AbilityParams");
            var action = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability$AbilityAction");
            var abilities = new LinkedHashMap<String, Object>();
            for (String name : List.of("walkspeed", "runspeed", "swimspeed", "phase")) {
                abilities.put(name, ability.getField(name.toUpperCase(Locale.ROOT)).get(null));
            }
            api = new Api(Map.copyOf(abilities),
                    lookup.findGetter(ability, "action", action).asType(MethodType.methodType(Object.class, Object.class)),
                    lookup.findVirtual(action, "execute", MethodType.methodType(boolean.class, Level.class, Player.class, ItemStack.class))
                            .asType(MethodType.methodType(boolean.class, Object.class, Level.class, Player.class, ItemStack.class)),
                    lookup.findVirtual(tool, "hasAbility", MethodType.methodType(boolean.class, ability))
                            .asType(MethodType.methodType(boolean.class, Item.class, Object.class)),
                    lookup.findVirtual(tool, "canUseAbility", MethodType.methodType(boolean.class, ItemStack.class, ability))
                            .asType(MethodType.methodType(boolean.class, Item.class, ItemStack.class, Object.class)),
                    lookup.findVirtual(tool, "getAbilityParams", MethodType.methodType(params, ability))
                            .asType(MethodType.methodType(Object.class, Item.class, Object.class)), params.getField("maxSlider"));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            LeggingsContent.LOG.warn("Could not bind Just Dire Things leggings ability views; passives disabled", failure);
        }
    }

    public static boolean nativeItem(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return stack.getCount() == 1 && id.getNamespace().equals("justdirethings")
                && List.of("ferricore_leggings", "blazegold_leggings", "celestigem_leggings", "eclipsealloy_leggings").contains(id.getPath());
    }

    static boolean powered(ItemStack stack) {
        return nativeItem(stack) && List.of("celestigem_leggings", "eclipsealloy_leggings")
                .contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
    }

    static ItemStack view(ItemStack root, String name) throws Throwable {
        Api binding = api;
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        if (binding == null || data == null) { return ItemStack.EMPTY; }
        Object ability = binding.abilities.get(name);
        ItemStack best = ItemStack.EMPTY;
        int range = Integer.MIN_VALUE;
        for (var source : List.of(data.left().source(), data.right().source())) {
            if (!nativeItem(source) || !(boolean) binding.has.invokeExact(source.getItem(), ability)) { continue; }
            int maximum = binding.max.getInt((Object) binding.params.invokeExact(source.getItem(), ability));
            if (best.isEmpty() || maximum > range) { best = source; range = maximum; }
        }
        if (best.isEmpty()) { return best; }
        var result = new ItemStack(best.getItem());
        result.applyComponents(root.getComponents());
        return result;
    }

    static boolean usable(ItemStack view, String name) throws Throwable {
        Api binding = api;
        return binding != null && !view.isEmpty()
                && (boolean) binding.usable.invokeExact(view.getItem(), view, binding.abilities.get(name));
    }

    public static void movement(Player player) {
        Api binding = api;
        if (binding == null || movementFailed || !JustDireMovementGuard.ready()) { return; }
        var root = player.getItemBySlot(EquipmentSlot.LEGS);
        if (MixedLeggingsStructure.validData(root).isEmpty()) { return; }
        try {
            for (String name : List.of("walkspeed", "runspeed", "swimspeed")) {
                var view = view(root, name);
                if (usable(view, name)) {
                    Object action = (Object) binding.action.invokeExact(binding.abilities.get(name));
                    boolean ignored = (boolean) binding.execute.invokeExact(action, player.level(), player, view);
                }
            }
        } catch (Throwable failure) {
            if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) { throw fatal; }
            if (!movementFailed) {
                movementFailed = true;
                LeggingsContent.LOG.warn("Just Dire Things leggings ability callback failed; passives disabled", failure);
            }
        }
    }

    private record Api(Map<String, Object> abilities, MethodHandle action, MethodHandle execute,
                       MethodHandle has, MethodHandle usable, MethodHandle params, Field max) {}
}
