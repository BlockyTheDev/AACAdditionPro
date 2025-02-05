package de.photon.anticheataddition.modules.checks.inventory;

import de.photon.anticheataddition.modules.ViolationModule;
import de.photon.anticheataddition.user.User;
import de.photon.anticheataddition.user.data.TimeKey;
import de.photon.anticheataddition.util.inventory.InventoryUtil;
import de.photon.anticheataddition.util.mathematics.Polynomial;
import de.photon.anticheataddition.util.minecraft.tps.TPSProvider;
import de.photon.anticheataddition.util.violationlevels.Flag;
import de.photon.anticheataddition.util.violationlevels.ViolationLevelManagement;
import de.photon.anticheataddition.util.violationlevels.ViolationManagement;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

public final class InventoryPerfectExit extends ViolationModule implements Listener
{
    public static final InventoryPerfectExit INSTANCE = new InventoryPerfectExit();

    private static final Polynomial VL_CALCULATOR = new Polynomial(-0.2857, 40);

    private final double minTps = loadDouble(".min_tps", 18.5);

    private InventoryPerfectExit()
    {
        super("Inventory.parts.PerfectExit");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClose(final InventoryCloseEvent event)
    {
        final var user = User.getUser(event.getPlayer().getUniqueId());
        if (User.isUserInvalid(user, this) ||
            // Creative-clear might trigger this.
            !user.inAdventureOrSurvivalMode() ||
            // Minimum TPS before the check is activated as of a huge amount of fps
            !TPSProvider.INSTANCE.atLeastTPS(minTps) ||
            // The inventory has been completely cleared
            !InventoryUtil.isInventoryEmpty(event.getInventory())) return;

        final long passedTime = user.getTimeMap().at(TimeKey.INVENTORY_CLICK_ON_ITEM).passedTime();
        if (user.getData().counter.inventoryPerfectExitFails.conditionallyIncDec(passedTime <= 70)) {
            this.getManagement().flag(Flag.of(user)
                                          .setAddedVl(VL_CALCULATOR.apply(passedTime).intValue())
                                          .setDebug(() -> "Inventory-Debug | Player: " + user.getPlayer().getName() + " exits inventories in a bot-like way (D: " + passedTime + ')'));
        }
    }

    @Override
    protected ViolationManagement createViolationManagement()
    {
        return ViolationLevelManagement.builder(this)
                                       .emptyThresholdManagement()
                                       .withDecay(400, 1).build();
    }
}
