package za.co.neroland.neroarcade.module.hook;

import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertyValues;

/**
 * THEME: palette, sprites and sounds, as ids. Never called per tick and never read by the simulation.
 */
public interface ThemeModule extends ArcadeModule {

    @Override
    default ModuleType type() {
        return ModuleType.THEME;
    }

    ThemeSpec spec(PropertyValues props);
}
