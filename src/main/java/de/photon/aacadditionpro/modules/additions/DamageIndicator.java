package de.photon.aacadditionpro.modules.additions;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import de.photon.aacadditionpro.ServerVersion;
import de.photon.aacadditionpro.exception.UnknownMinecraftException;
import de.photon.aacadditionpro.modules.Module;
import de.photon.aacadditionpro.modules.ModuleLoader;
import de.photon.aacadditionpro.modules.ModulePacketAdapter;
import de.photon.aacadditionpro.user.User;
import de.photon.aacadditionpro.util.config.LoadFromConfiguration;
import de.photon.aacadditionpro.util.packetwrappers.server.WrapperPlayServerEntityMetadata;
import de.photon.aacadditionpro.util.packetwrappers.server.WrapperPlayServerNamedEntitySpawn;
import de.photon.aacadditionpro.util.world.EntityUtil;
import lombok.val;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Animals;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Wither;

import java.util.List;
import java.util.Objects;

public class DamageIndicator extends Module
{
    @LoadFromConfiguration(configPath = ".spoof.players")
    private boolean spoofPlayers;
    @LoadFromConfiguration(configPath = ".spoof.animals")
    private boolean spoofAnimals;
    @LoadFromConfiguration(configPath = ".spoof.monsters")
    private boolean spoofMonsters;

    public DamageIndicator()
    {
        super("DamageIndicator");
    }

    @Override
    protected ModuleLoader createModuleLoader()
    {
        val adapter = new DamageIndicatorPacketAdapter(this);
        return ModuleLoader.builder(this)
                           .addPacketListeners(adapter)
                           .build();
    }

    private class DamageIndicatorPacketAdapter extends ModulePacketAdapter
    {
        public DamageIndicatorPacketAdapter(Module module)
        {
            super(module, ListenerPriority.HIGH, PacketType.Play.Server.ENTITY_METADATA, PacketType.Play.Server.NAMED_ENTITY_SPAWN);
        }

        @Override
        public void onPacketSending(PacketEvent event)
        {
            val user = User.safeGetUserFromPacketEvent(event);
            if (User.isUserInvalid(user, this.getModule())) return;

            val entity = event.getPacket().getEntityModifier(event.getPlayer().getWorld()).read(0);

            // Should spoof?
            // Clientside entities will be null in the world's entity list.
            if (entity != null &&
                // Not the player himself.
                // Offline mode servers have name-based UUIDs, so that should be no problem.
                event.getPlayer().getEntityId() != entity.getEntityId() &&
                // Bossbar problems
                // Cannot use Boss interface as that doesn't exist on 1.8.8
                !(entity instanceof EnderDragon) &&
                !(entity instanceof Wither) &&
                // Entity must be living to have health; all categories extend LivingEntity.
                ((entity instanceof HumanEntity && spoofPlayers) ||
                 (entity instanceof Monster && spoofMonsters) ||
                 (entity instanceof Animals && spoofAnimals)))
            {
                // Index of the health value in ENTITY_METADATA
                final int index;

                // Passenger problems
                switch (ServerVersion.getActiveServerVersion()) {
                    case MC18:
                        // index 6 in 1.8
                        index = 6;
                        break;
                    case MC112:
                    case MC113:
                        // index 7 in 1.11+
                        index = 7;
                        break;
                    case MC114:
                    case MC115:
                    case MC116:
                        // index 8 in 1.14.4+
                        index = 8;
                        break;
                    default:
                        throw new UnknownMinecraftException();
                }

                // No passengers.
                if (!EntityUtil.getPassengers(entity).isEmpty()) return;

                // Clone the packet to prevent a serversided connection of the health.
                event.setPacket(event.getPacket().deepClone());
                List<WrappedWatchableObject> read = null;

                final float spoofedHealth;
                switch (ServerVersion.getActiveServerVersion()) {
                    case MC18:
                        spoofedHealth = Float.NaN;

                        // Only set it on 1.8.8, otherwise it will just be at the max health.
                        // This packetwrapper doesn't currently work with 1.15+.
                        if (event.getPacket().getType() == PacketType.Play.Server.NAMED_ENTITY_SPAWN) {
                            val spawnWrapper = new WrapperPlayServerNamedEntitySpawn(event.getPacket());
                            read = spawnWrapper.getMetadata().getWatchableObjects();
                        }
                        break;
                    case MC112:
                    case MC113:
                    case MC114:
                    case MC115:
                    case MC116:
                        spoofedHealth = (float) Objects.requireNonNull(((LivingEntity) entity).getAttribute(Attribute.GENERIC_MAX_HEALTH), "Tried to get max health of an entity without health.").getValue();
                        break;
                    default:
                        throw new UnknownMinecraftException();
                }

                if (event.getPacket().getType() == PacketType.Play.Server.ENTITY_METADATA) {
                    val metadataWrapper = new WrapperPlayServerEntityMetadata(event.getPacket());
                    read = metadataWrapper.getMetadata();
                }

                if (read != null) {
                    for (WrappedWatchableObject watch : read) {
                        if ((watch.getIndex() == index) && ((Float) watch.getValue() > 0.0F)) {
                            watch.setValue(spoofedHealth);
                        }
                    }
                }
            }
        }
    }
}