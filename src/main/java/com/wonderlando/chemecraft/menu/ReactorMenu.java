package com.wonderlando.chemecraft.menu;

import com.wonderlando.chemecraft.block.entity.TankReactorBlockEntity;
import com.wonderlando.chemecraft.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Display-only menu for the batch reactor. It carries no item slots — just a {@link ContainerData} of
 * encoded display values that the menu auto-syncs to the client each tick. The screen decodes them.
 */
public class ReactorMenu extends AbstractContainerMenu {
    /** clickMenuButton id for the "empty the reactor" action (kept out of the reaction-index range). */
    public static final int BUTTON_EMPTY = 100;
    /** clickMenuButton id for the "release contents through the outlet" toggle. */
    public static final int BUTTON_RELEASE = 101;

    private final ContainerData data;
    private final Level level;
    private final BlockPos pos;

    /** Server-side: backed by the live block entity. */
    public ReactorMenu(int containerId, Inventory playerInventory, TankReactorBlockEntity reactor) {
        super(ModMenus.REACTOR.get(), containerId);
        this.level = reactor.getLevel();
        this.pos = reactor.getBlockPos();
        this.data = reactor.getDisplayData();
        addDataSlots(this.data);
    }

    /** Client-side: built from the buffer written when the menu was opened. */
    public ReactorMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(ModMenus.REACTOR.get(), containerId);
        this.level = playerInventory.player.level();
        this.pos = buf.readBlockPos();
        this.data = new SimpleContainerData(TankReactorBlockEntity.DISPLAY_SLOTS);
        addDataSlots(this.data);
    }

    /** A synced display value (see TankReactorBlockEntity display-slot indices). */
    public int value(int index) {
        return data.get(index);
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // no item slots
    }

    @Override
    public boolean stillValid(Player player) {
        return level.getBlockEntity(pos) instanceof TankReactorBlockEntity
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    /** A GUI button was clicked: select the reaction with that index on the reactor (server-side). */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.getBlockEntity(pos) instanceof TankReactorBlockEntity reactor) {
            if (id == BUTTON_EMPTY) {
                reactor.empty();
            } else if (id == BUTTON_RELEASE) {
                reactor.toggleRelease();
            } else {
                reactor.selectReaction(id);
            }
            return true;
        }
        return false;
    }
}
