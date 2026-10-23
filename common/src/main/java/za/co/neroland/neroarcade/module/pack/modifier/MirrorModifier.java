package za.co.neroland.neroarcade.module.pack.modifier;

import java.util.List;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.Intent;
import za.co.neroland.neroarcade.module.hook.ModifierModule;
import za.co.neroland.neroarcade.module.hook.ModuleContext;

/**
 * {@code neroarcade:modifier/mirror} — every seat's controls are flipped. No flags.
 *
 * <ul>
 *   <li>{@code axis} — {@code horizontal} negates left/right movement and aim, {@code vertical} up/down,
 *   {@code both} both.</li>
 * </ul>
 *
 * <p>Applied in {@code transformIntent} after CONTROL and MULTIPLAYER, so the runtime opponent is mirrored too.
 * Buttons A/B are never touched. Odd combinations: none — a one-button control has no axes to flip. Stateless;
 * pure.
 */
public final class MirrorModifier implements ModifierModule {

    public static final MirrorModifier INSTANCE = new MirrorModifier();

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "modifier/mirror");

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.enumeration("axis", "horizontal", "horizontal", "vertical", "both"));

    private MirrorModifier() {
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public List<PropertySpec> properties() {
        return PROPERTIES;
    }

    @Override
    public Set<ModuleFlag> flags() {
        return Set.of();
    }

    @Override
    public Intent transformIntent(ModuleContext ctx, int seat, Intent in) {
        String axis = ctx.props().getEnum("axis");
        boolean flipX = !axis.equals("vertical");
        boolean flipY = !axis.equals("horizontal");
        return new Intent(flipX ? -in.moveX() : in.moveX(), flipY ? -in.moveY() : in.moveY(),
                flipX ? -in.aimX() : in.aimX(), flipY ? -in.aimY() : in.aimY(),
                in.a(), in.b(), in.aPressed(), in.bPressed());
    }

    /** "Mirrored: Horizontal". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {Component.translatable(PROPERTIES.get(0).constantLangKey(this, props.getEnum("axis")))};
    }
}
