package com.wonderlando.chemecraft.registry;

import com.wonderlando.chemecraft.ChemECraft;
import com.wonderlando.chemecraft.menu.ReactorMenu;
import com.wonderlando.chemecraft.menu.ReservoirMenu;
import com.wonderlando.chemecraft.menu.SinkMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Menu types (GUI containers) registered by ChemECraft. */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ChemECraft.MODID);

    // One menu, shared by the CSTR and the batch reactor (same GUI; the screen reads the synced display slots).
    public static final DeferredHolder<MenuType<?>, MenuType<ReactorMenu>> REACTOR =
            MENUS.register("reactor", () -> IMenuTypeExtension.create(ReactorMenu::new));

    // The testing reservoir's config GUI.
    public static final DeferredHolder<MenuType<?>, MenuType<ReservoirMenu>> RESERVOIR =
            MENUS.register("reservoir", () -> IMenuTypeExtension.create(ReservoirMenu::new));

    // The testing sink's live readout GUI.
    public static final DeferredHolder<MenuType<?>, MenuType<SinkMenu>> SINK =
            MENUS.register("sink", () -> IMenuTypeExtension.create(SinkMenu::new));

    private ModMenus() {}

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
