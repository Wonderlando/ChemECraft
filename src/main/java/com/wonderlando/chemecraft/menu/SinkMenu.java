package com.wonderlando.chemecraft.menu;

import com.wonderlando.chemecraft.block.entity.SinkBlockEntity;
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

/** Display-only menu for the sink: carries the synced measured-inflow values; no controls. */
public class SinkMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final Level level;
    private final BlockPos pos;

    public SinkMenu(int containerId, Inventory playerInventory, SinkBlockEntity sink) {
        super(ModMenus.SINK.get(), containerId);
        this.level = sink.getLevel();
        this.pos = sink.getBlockPos();
        this.data = sink.getDisplayData();
        addDataSlots(this.data);
    }

    public SinkMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(ModMenus.SINK.get(), containerId);
        this.level = playerInventory.player.level();
        this.pos = buf.readBlockPos();
        this.data = new SimpleContainerData(SinkBlockEntity.DISPLAY_SLOTS);
        addDataSlots(this.data);
    }

    public int value(int index) {
        return data.get(index);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return level.getBlockEntity(pos) instanceof SinkBlockEntity
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
