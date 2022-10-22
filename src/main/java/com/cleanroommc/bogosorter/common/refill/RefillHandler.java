package com.cleanroommc.bogosorter.common.refill;

import com.cleanroommc.bogosorter.BogoSorter;
import com.cleanroommc.bogosorter.common.OreDictHelper;
import com.cleanroommc.bogosorter.common.config.PlayerConfig;
import com.cleanroommc.bogosorter.common.network.NetworkHandler;
import com.cleanroommc.bogosorter.common.network.NetworkUtils;
import com.cleanroommc.bogosorter.common.network.SRefillSound;
import com.cleanroommc.bogosorter.mixin.ContainerAccessor;
import gregtech.api.items.IToolItem;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.function.BiPredicate;

@Mod.EventBusSubscriber(modid = BogoSorter.ID)
public class RefillHandler {

    private static final int[][] INVENTORY_PROXIMITY_MAP = {
            {1, 2, 3, 4, 5, 6, 7, 8, 27, 18, 9, 28, 19, 10, 29, 20, 11, 30, 21, 12, 31, 22, 13, 32, 23, 14, 33, 24, 15, 34, 25, 16, 35, 26, 17},
            {0, 2, 3, 4, 5, 6, 7, 8, 28, 19, 10, 27, 18, 9, 29, 20, 11, 30, 21, 12, 31, 22, 13, 32, 23, 14, 33, 24, 15, 34, 25, 16, 35, 26, 17},
            {1, 3, 0, 4, 5, 6, 7, 8, 29, 20, 11, 28, 19, 10, 30, 21, 12, 27, 18, 9, 31, 22, 13, 32, 23, 14, 33, 24, 15, 34, 25, 16, 35, 26, 17},
            {2, 4, 1, 5, 0, 6, 7, 8, 30, 21, 12, 29, 20, 11, 31, 22, 13, 28, 19, 10, 32, 23, 14, 27, 18, 9, 33, 24, 15, 34, 25, 16, 35, 26, 17},
            {3, 5, 2, 6, 1, 7, 0, 8, 31, 22, 13, 30, 21, 12, 32, 23, 14, 29, 20, 11, 33, 24, 15, 28, 19, 10, 34, 25, 16, 27, 18, 9, 35, 26, 17},
            {4, 6, 3, 7, 2, 8, 1, 0, 32, 23, 14, 31, 22, 13, 33, 24, 15, 30, 21, 12, 34, 25, 16, 29, 20, 11, 35, 26, 17, 28, 19, 10, 27, 18, 9},
            {5, 7, 4, 8, 3, 2, 1, 0, 33, 24, 15, 32, 23, 14, 34, 25, 16, 31, 22, 13, 35, 26, 17, 30, 21, 12, 29, 20, 11, 28, 19, 10, 27, 18, 9},
            {6, 8, 5, 4, 3, 2, 1, 0, 34, 25, 16, 33, 24, 15, 35, 26, 17, 32, 23, 14, 31, 22, 13, 30, 21, 12, 29, 20, 11, 28, 19, 10, 27, 18, 9},
            {7, 6, 5, 4, 3, 2, 1, 0, 35, 26, 17, 34, 25, 16, 33, 24, 15, 32, 23, 14, 31, 22, 13, 30, 21, 12, 29, 20, 11, 28, 19, 10, 27, 18, 9},
    };

    /*@SubscribeEvent
    public static void onDestroyItem(PlayerDestroyItemEvent event) {
        if (!PlayerConfig.get(event.getEntityPlayer()).enableAutoRefill) return;

        if (shouldHandleRefill(event.getEntityPlayer(), event.getOriginal())) {
            int index = event.getHand() == EnumHand.MAIN_HAND ? event.getEntityPlayer().inventory.currentItem : 40;
            new RefillHandler(index, event.getOriginal(), event.getEntityPlayer()).handleRefill();
        }
    }*/

    public static void onDestroyItem(EntityPlayer player, EnumHand hand, ItemStack original) {
        if (!PlayerConfig.get(player).enableAutoRefill) return;

        if (shouldHandleRefill(player, original)) {
            int index = hand == EnumHand.MAIN_HAND ? player.inventory.currentItem : 40;
            new RefillHandler(index, original, player).handleRefill();
        }
    }

    public static boolean shouldHandleRefill(EntityPlayer player, ItemStack brokenItem) {
        Container container = player.openContainer;
        return !NetworkUtils.isClient(player) && (container == null || container == player.inventoryContainer) && !brokenItem.isEmpty();
    }

    private BiPredicate<ItemStack, ItemStack> similarItemMatcher = (stack, stack2) -> stack.getItem() == stack2.getItem() && stack.getMetadata() == stack2.getMetadata();
    private BiPredicate<ItemStack, ItemStack> exactItemMatcher = RefillHandler::matchTags;
    private final int hotbarIndex;
    private final IntList slots;
    private final ItemStack brokenItem;
    private final EntityPlayer player;
    private final InventoryPlayer inventory;
    private final PlayerConfig playerConfig;
    private final boolean swapItems;
    private boolean isDamageable = false;

    public RefillHandler(int hotbarIndex, ItemStack brokenItem, EntityPlayer player, boolean swapItems) {
        this.hotbarIndex = hotbarIndex;
        this.slots = new IntArrayList(INVENTORY_PROXIMITY_MAP[hotbarIndex == 40 ? player.inventory.currentItem : hotbarIndex]);
        this.brokenItem = brokenItem;
        this.player = player;
        this.inventory = player.inventory;
        this.playerConfig = PlayerConfig.get(player);
        this.swapItems = swapItems;
    }

    public RefillHandler(int hotbarIndex, ItemStack brokenItem, EntityPlayer player) {
        this(hotbarIndex, brokenItem, player, false);
    }

    public boolean handleRefill() {

        if (brokenItem.getItem() instanceof ItemBlock) {
            return findItem(false);
        } else if (brokenItem.isItemStackDamageable()) {
            similarItemMatcher = (stack, stack2) -> stack.getItem() == stack2.getItem();
            isDamageable = true;
            return findNormalDamageable();
        } else if ((BogoSorter.isGTCELoaded() || BogoSorter.isGTCEuLoaded()) && brokenItem.getItem() instanceof IToolItem) {
            exactItemMatcher = (stack, stack2) -> {
                if (stack.hasTagCompound() != stack2.hasTagCompound()) return false;
                if (!stack.hasTagCompound()) return true;
                return OreDictHelper.getGtToolMaterial(stack).equals(OreDictHelper.getGtToolMaterial(stack2));
            };
            isDamageable = true;
            return findNormalDamageable();
        } else {
            return findItem(true);
        }
    }

    private boolean findItem(boolean exactOnly) {
        ItemStack firstItemMatch = null;
        int firstItemMatchSlot = -1;
        IntListIterator slotsIterator = slots.iterator();
        while (slotsIterator.hasNext()) {
            int slot = slotsIterator.next();
            ItemStack found = inventory.mainInventory.get(slot);
            if (found.isEmpty() || (isDamageable && DamageHelper.getDurability(found) <= playerConfig.autoRefillDamageThreshold)) {
                slotsIterator.remove();
                continue;
            }
            if (similarItemMatcher.test(brokenItem, found)) {
                if (exactItemMatcher.test(brokenItem, found)) {
                    refillItem(found, slot);
                    return true;
                }
                if (firstItemMatch == null) {
                    firstItemMatch = found;
                    firstItemMatchSlot = slot;
                }
            }
        }
        if (firstItemMatch != null && !exactOnly) {
            refillItem(firstItemMatch, firstItemMatchSlot);
            return true;
        }
        return false;
    }

    private boolean findNormalDamageable() {
        if (findItem(false)) {
            return true;
        }
        if (slots.isEmpty()) return false;

        Set<String> brokenToolClasses = brokenItem.getItem().getToolClasses(brokenItem);
        if (brokenToolClasses.isEmpty())
            return false;

        // try match tool type
        for (int slot : slots) {
            ItemStack found = inventory.mainInventory.get(slot);
            Set<String> toolTypes = found.getItem().getToolClasses(found);
            if (brokenToolClasses.equals(toolTypes)) {
                refillItem(found, slot);
                return true;
            }
        }

        int brokenTools = brokenToolClasses.size();
        for (int slot : slots) {
            ItemStack found = inventory.mainInventory.get(slot);
            if (found.isEmpty()) continue;
            Set<String> toolTypes = found.getItem().getToolClasses(found);
            int tools = toolTypes.size();
            if (tools == 0 || tools == brokenTools) continue;
            if (tools > brokenTools) {
                if (toolTypes.containsAll(brokenToolClasses)) {
                    refillItem(found, slot);
                    return true;
                }
            } else {
                if (brokenToolClasses.containsAll(toolTypes)) {
                    refillItem(found, slot);
                    return true;
                }
            }
        }

        return false;
    }

    private void refillItem(ItemStack refill, int refillIndex) {
        refill = refill.copy();
        setAndSyncSlot(hotbarIndex, refill);
        setAndSyncSlot(refillIndex, swapItems ? brokenItem.copy() : ItemStack.EMPTY);
        /*if (hotbarIndex == 40) {
            // offhand
            //inventory.offHandInventory.set(0, refill);
            inventory.mainInventory.set(refillIndex, swapItems ? brokenItem.copy() : ItemStack.EMPTY);
            // if the replacing item is equal to the replaced item, it will not be synced
            // this makes sure that the sync is triggered
            player.inventoryContainer.inventoryItemStacks.set(40, refill);
            //player.inventoryContainer.inventoryItemStacks.set(40, ItemStack.EMPTY);
        } else {
            inventory.mainInventory.set(hotbarIndex, refill);
            inventory.mainInventory.set(refillIndex, swapItems ? brokenItem.copy() : ItemStack.EMPTY);
            // if the replacing item is equal to the replaced item, it will not be synced
            // this makes sure that the sync is triggered
            player.inventoryContainer.inventoryItemStacks.set(hotbarIndex, refill);
            //player.inventoryContainer.inventoryItemStacks.set(hotbarIndex + 36, ItemStack.EMPTY);
        }*/

        // the sound should be played for this player
        NetworkHandler.sendToPlayer(new SRefillSound(), (EntityPlayerMP) player);
    }

    private void setAndSyncSlot(int index, ItemStack item) {
        if (index < 0 || index > 40) return;
        if (index < 36) {
            inventory.mainInventory.set(index, item);
        } else if (index < 40) {
            inventory.armorInventory.set(index - 36, item);
        } else {
            inventory.offHandInventory.set(0, item);
        }
        player.inventoryContainer.inventoryItemStacks.set(index, item);
        for (IContainerListener listener : ((ContainerAccessor) player.inventoryContainer).getListeners()) {
            listener.sendSlotContents(player.inventoryContainer, index, item);
        }
    }

    private static boolean matchTags(ItemStack stackA, ItemStack stackB) {
        if (stackA.getTagCompound() == null && stackB.getTagCompound() != null) {
            return false;
        } else {
            return (stackA.getTagCompound() == null || stackA.getTagCompound().equals(stackB.getTagCompound())) && stackA.areCapsCompatible(stackB);
        }
    }
}
