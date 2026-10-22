package dev.chaze.balance.registry;

import dev.chaze.balance.BalanceMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.item.VerticallyAttachableBlockItem;
import net.minecraft.util.math.Direction;

/** A single inventory item places either the standing or wall-mounted torch. */
public final class ModItems {
    // Vanilla's helper also populates Block.asItem() for both torch variants.
    public static final Item VIGIL_TORCH = Items.register(
            BalanceMod.id("vigil_torch"), new VerticallyAttachableBlockItem(
                    ModBlocks.VIGIL_TORCH, ModBlocks.WALL_VIGIL_TORCH,
                    new Item.Settings(), Direction.DOWN));

    private ModItems() {
    }

    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL)
                .register(entries -> entries.add(VIGIL_TORCH));
    }
}
