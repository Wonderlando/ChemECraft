package com.wonderlando.chemecraft.registry;

import com.wonderlando.chemecraft.ChemECraft;
import com.wonderlando.chemecraft.menu.BatchReactorMenu;

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

    public static final DeferredHolder<MenuType<?>, MenuType<BatchReactorMenu>> BATCH_REACTOR =
            MENUS.register("batch_reactor", () -> IMenuTypeExtension.create(BatchReactorMenu::new));

    private ModMenus() {}

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
