package com.wonderlando.chemecraft.menu;

import com.wonderlando.chemecraft.block.entity.ReservoirBlockEntity;
import com.wonderlando.chemecraft.reaction.Species;
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
 * Display + control menu for the reservoir. Carries the synced display values and turns GUI button clicks
 * into composition / flow-rate adjustments on the block entity. Button ids are {@code BTN_*}; the screen
 * sends them via {@code handleInventoryButtonClick}.
 */
public class ReservoirMenu extends AbstractContainerMenu {
    // Per-species concentration steppers are addressed generically by ordinal: decrement = BASE + ordinal*2,
    // increment = BASE + ordinal*2 + 1. A new Species automatically gets a pair of button ids — no new constants.
    public static final int SPECIES_BTN_BASE = 200;
    public static final int BTN_RATE_DEC = 60, BTN_RATE_INC = 61;
    public static final int BTN_UNIT_CYCLE = 70;
    public static final int BTN_EMIT_TOGGLE = 100;

    /** Button id to decrement the given species' concentration. (+1 increments.) */
    public static int speciesButton(Species species, boolean increment) {
        return SPECIES_BTN_BASE + species.ordinal() * 2 + (increment ? 1 : 0);
    }

    private final ContainerData data;
    private final Level level;
    private final BlockPos pos;

    public ReservoirMenu(int containerId, Inventory playerInventory, ReservoirBlockEntity reservoir) {
        super(ModMenus.RESERVOIR.get(), containerId);
        this.level = reservoir.getLevel();
        this.pos = reservoir.getBlockPos();
        this.data = reservoir.getDisplayData();
        addDataSlots(this.data);
    }

    public ReservoirMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(ModMenus.RESERVOIR.get(), containerId);
        this.level = playerInventory.player.level();
        this.pos = buf.readBlockPos();
        this.data = new SimpleContainerData(ReservoirBlockEntity.DISPLAY_SLOTS);
        addDataSlots(this.data);
    }

    public int value(int index) {
        return data.get(index);
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return level.getBlockEntity(pos) instanceof ReservoirBlockEntity
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(level.getBlockEntity(pos) instanceof ReservoirBlockEntity r)) {
            return false;
        }
        double c = ReservoirBlockEntity.CONC_STEP;
        // Generic per-species concentration steppers (decode ordinal + direction from the id).
        int speciesSpan = Species.values().length * 2;
        if (id >= SPECIES_BTN_BASE && id < SPECIES_BTN_BASE + speciesSpan) {
            int rel = id - SPECIES_BTN_BASE;
            Species species = Species.values()[rel / 2];
            r.addConcentration(species, (rel % 2 == 0) ? -c : c);
            return true;
        }
        switch (id) {
            case BTN_RATE_DEC     -> r.stepRate(-1);
            case BTN_RATE_INC     -> r.stepRate(+1);
            case BTN_UNIT_CYCLE   -> r.cycleRateUnit();
            case BTN_EMIT_TOGGLE  -> r.toggleRelease();
            default -> { return false; }
        }
        return true;
    }
}
