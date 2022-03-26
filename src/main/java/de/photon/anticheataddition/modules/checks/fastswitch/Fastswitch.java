package de.photon.anticheataddition.modules.checks.fastswitch;

import com.comphenix.protocol.PacketType;
import de.photon.anticheataddition.modules.ModuleLoader;
import de.photon.anticheataddition.modules.ViolationModule;
import de.photon.anticheataddition.protocol.PacketAdapterBuilder;
import de.photon.anticheataddition.user.User;
import de.photon.anticheataddition.user.data.TimestampKey;
import de.photon.anticheataddition.util.inventory.InventoryUtil;
import de.photon.anticheataddition.util.mathematics.MathUtil;
import de.photon.anticheataddition.util.minecraft.ping.PingProvider;
import de.photon.anticheataddition.util.minecraft.tps.TPSProvider;
import de.photon.anticheataddition.util.violationlevels.Flag;
import de.photon.anticheataddition.util.violationlevels.ViolationLevelManagement;
import de.photon.anticheataddition.util.violationlevels.ViolationManagement;
import lombok.val;

public class Fastswitch extends ViolationModule
{
    private final int cancelVl = loadInt(".cancel_vl", 50);
    private final int maxPing = loadInt(".max_ping", 400);
    private final int switchMilliseconds = loadInt(".switch_milliseconds", 50);

    public Fastswitch()
    {
        super("Fastswitch");
    }

    /**
     * Used to acknowledge if somebody can be legit.
     * I.e. that players can scroll very fast, but then the neighbor slot is always the one that gets called next.
     */
    private static boolean canBeLegit(final int oldSlot, final int newHeldItemSlot)
    {
        return (oldSlot == 0 && newHeldItemSlot == 8) ||
               (oldSlot == 8 && newHeldItemSlot == 0) ||
               MathUtil.absDiff(oldSlot, newHeldItemSlot) <= 1;
    }

    @Override
    protected ModuleLoader createModuleLoader()
    {
        val packetAdapter = PacketAdapterBuilder
                .of(PacketType.Play.Client.HELD_ITEM_SLOT)
                .onReceiving(event -> {
                    val user = User.safeGetUserFromPacketEvent(event);
                    if (User.isUserInvalid(user, this)) return;

                    // Tps are high enough
                    if (TPSProvider.INSTANCE.atLeastTPS(19) &&
                        event.getPacket().getBytes().readSafely(0) != null &&
                        // Prevent the detection of scrolling
                        !canBeLegit(user.getPlayer().getInventory().getHeldItemSlot(), event.getPacket().getBytes().readSafely(0)))
                    {
                        // Already switched in the given timeframe
                        if (user.getTimestampMap().at(TimestampKey.FASTSWITCH_HOTBAR_SWITCH).recentlyUpdated(switchMilliseconds) &&
                            // The ping is valid and in the borders that are set in the config
                            PingProvider.INSTANCE.maxPingHandling(user.getPlayer(), maxPing))
                        {
                            getManagement().flag(Flag.of(user)
                                                     .setAddedVl(25)
                                                     .setCancelAction(cancelVl, () -> event.setCancelled(true))
                                                     .setEventNotCancelledAction(() -> InventoryUtil.syncUpdateInventory(user.getPlayer())));
                        }

                        user.getTimestampMap().at(TimestampKey.FASTSWITCH_HOTBAR_SWITCH).update();
                    }
                }).build();

        return ModuleLoader.builder(this)
                           .addPacketListeners(packetAdapter)
                           .build();
    }

    @Override
    protected ViolationManagement createViolationManagement()
    {
        return ViolationLevelManagement.builder(this).loadThresholdsToManagement().withDecay(120, 25).build();
    }
}