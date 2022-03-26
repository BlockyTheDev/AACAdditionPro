package de.photon.anticheataddition.util.violationlevels.threshold;

import com.google.common.base.Preconditions;
import de.photon.anticheataddition.AntiCheatAddition;
import de.photon.anticheataddition.util.execute.Placeholders;
import de.photon.anticheataddition.util.messaging.DebugSender;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@EqualsAndHashCode(doNotUseGetters = true, onlyExplicitlyIncluded = true)
public class Threshold implements Comparable<Threshold>
{
    @Getter @EqualsAndHashCode.Include private final int vl;
    @NotNull private final List<String> commandList;

    public Threshold(int vl, List<String> commandList)
    {
        Preconditions.checkNotNull(commandList, "Tried to define Threshold with null commands.");
        Preconditions.checkArgument(vl > 0, "Tried to define Threshold with vl smaller or equal to 0.");
        Preconditions.checkArgument(!commandList.isEmpty(), "Tried to define Threshold without commands.");

        this.vl = vl;
        this.commandList = List.copyOf(commandList);
    }

    /**
     * This executes the commands of this {@link Threshold}.
     */
    public void executeCommandList(Player player)
    {
        Bukkit.getScheduler().runTask(
                AntiCheatAddition.getInstance(),
                () -> {
                    for (String rawCommand : this.commandList) {
                        final String command = Placeholders.replacePlaceholders(rawCommand, player);

                        // Try catch to prevent console errors if a command couldn't be executed, e.g. if the player has left.
                        try {
                            Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), command);
                            DebugSender.getInstance().sendDebug(ChatColor.GOLD + "Executed command: " + command);
                        } catch (final Exception e) {
                            DebugSender.getInstance().sendDebug("Could not execute command /" + command, true, true);
                        }
                    }
                });
    }

    @Override
    public int compareTo(Threshold o)
    {
        return Integer.compare(vl, o.vl);
    }
}