package games.alejandrocoria.mismatchedleggings.test;

import com.stal111.forbidden_arcanus.common.item.modifier.ModifierHelper;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Native tooltip text/texture-event observations, not a custom UI or visual acceptance. */
public final class ForbiddenModifierClientSpike {
    private ForbiddenModifierClientSpike() {}

    public static void verify(Minecraft client) throws Exception {
        var samples = new ArrayList<ItemStack>();
        for (String kind : List.of("eternal", "soulbound")) {
            var source = ForbiddenModifierSpike.source(client.level.registryAccess(), kind, true);
            samples.add(source);
            var pair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.IRON_LEGGINGS), CompositionRules.defaults());
            samples.add(pair.primary());
            samples.add(pair.inverse());
            samples.add(ForbiddenModifierSpike.applyRoot(client.level.registryAccess(),
                    ForbiddenModifierSpike.pair(new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.IRON_LEGGINGS)), kind));
        }
        var report = new StringBuilder("NATIVE CLIENT TEXT / TEXTURE EVENT (run before and after real reload)\n");
        for (var stack : samples) {
            var before = stack.copy();
            report.append(ForbiddenModifierSpike.state(stack)).append('\n');
            var texture = new RenderTooltipEvent.Texture(stack, null, 0, 0, client.font, List.of(), null);
            NeoForge.EVENT_BUS.post(texture);
            var nativeModifier = ModifierHelper.getModifier(stack);
            require(nativeModifier.isPresent() ? nativeModifier.orElseThrow().displaySettings().texture().equals(texture.getTexture())
                            : texture.getTexture() == null,
                    "Native modifier tooltip texture event did not follow live root state");
            report.append("  native tooltip texture=").append(texture.getTexture()).append('\n');
            for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                var lines = stack.getTooltipLines(Item.TooltipContext.of(client.level), client.player, flag);
                report.append("  ").append(flag).append(": ").append(lines.stream().map(line -> line.getString()).toList()).append('\n');
            }
            require(ItemStack.matches(before, stack), "Native tooltip/texture event changed live item state");
        }
        Files.writeString(ForbiddenModifierSpike.report("client"), report, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
