package za.co.neroland.neroarcade.forge;

import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.client.hud.ArcadeHud;

/**
 * Forge registration of the seated-player HUD layer, kept apart from {@link ForgeClientSetup} on purpose.
 *
 * <p>{@code AddGuiOverlayLayersEvent} does not exist on every Forge build of a supported major (on 1.21.1 it
 * arrived in Forge 52.1.2). A listener lambda typed on it fails with {@code NoClassDefFoundError} the moment its
 * call site is linked, which used to take down mod construction (MC-NEROARCADE-1). Only this class names the
 * event, and {@link ForgeClientSetup} touches it only after checking the event class is present, so a Forge
 * build without it loses the HUD layer instead of crashing. The manifest already floors Forge at the compiled
 * build; this is the second line of defence.
 */
final class ForgeHudOverlays {

    /** Fully-qualified name of the event this class needs; checked before the class is first touched. */
    static final String EVENT_CLASS = "net.minecraftforge.client.event.AddGuiOverlayLayersEvent";

    private ForgeHudOverlays() {
    }

    /** Adds the arcade HUD layer. Call only when {@link #EVENT_CLASS} is loadable. */
    static void register(FMLJavaModLoadingContext context) {
        //? if >=1.21.9 {
        AddGuiOverlayLayersEvent.BUS.addListener(event -> {
            event.getLayeredDraw().add(Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID, "arcade_hud"),
                    (g, delta) -> ArcadeHud.render(g));
        });
        //?} else if >=1.21.6 {
        /*AddGuiOverlayLayersEvent.getBus(context.getModBusGroup()).addListener(event -> {
            event.getLayeredDraw().add(Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID, "arcade_hud"),
                    (g, delta) -> ArcadeHud.render(g));
        });
        *///?} else {
        /*context.getModEventBus().addListener((AddGuiOverlayLayersEvent event) ->
                event.getLayeredDraw().add(Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID, "arcade_hud"),
                        (g, delta) -> ArcadeHud.render(g)));
        *///?}
    }
}
