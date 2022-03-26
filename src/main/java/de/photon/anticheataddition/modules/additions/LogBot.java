package de.photon.anticheataddition.modules.additions;

import de.photon.anticheataddition.AntiCheatAddition;
import de.photon.anticheataddition.modules.Module;
import de.photon.anticheataddition.util.messaging.DebugSender;
import lombok.Value;
import org.bukkit.Bukkit;

import java.io.File;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LogBot extends Module
{
    private final Set<LogDeletionTime> logDeletionTimes = Stream.of(new LogDeletionTime("plugins/AntiCheatAddition/logs", ".AntiCheatAddition"),
                                                                    new LogDeletionTime("logs", ".Server"))
                                                                // Actually active.
                                                                .filter(logDeletionTime -> logDeletionTime.timeToDelete > 0)
                                                                .collect(Collectors.toUnmodifiableSet());

    private int taskNumber;

    public LogBot()
    {
        super("LogBot");
    }

    @Override
    public void enable()
    {
        // Start a daily executed task to clean up the logs.
        taskNumber = Bukkit.getScheduler().scheduleSyncRepeatingTask(AntiCheatAddition.getInstance(), () -> {
            final long currentTime = System.currentTimeMillis();
            for (LogDeletionTime logDeletionTime : logDeletionTimes) logDeletionTime.handleLog(currentTime);
        }, 1, TimeUnit.DAYS.toSeconds(1) * 20);
    }

    @Override
    public void disable()
    {
        Bukkit.getScheduler().cancelTask(taskNumber);
    }

    @Value
    private class LogDeletionTime
    {
        File logFolder;
        long timeToDelete;

        public LogDeletionTime(String filePath, String configPath)
        {
            this.logFolder = new File(filePath);
            this.timeToDelete = TimeUnit.DAYS.toMillis(loadLong(configPath, 10));
        }

        public void handleLog(final long currentTime)
        {
            // The folder exists.
            if (!logFolder.exists()) {
                DebugSender.getInstance().sendDebug("Could not find log folder " + logFolder.getName(), true, true);
                return;
            }

            final File[] files = logFolder.listFiles();
            if (files == null) return;

            for (File file : files) {
                final String fileName = file.getName();
                // Be sure it is a log file of AntiCheatAddition (.log) or a log file of the server (.log.gz)
                if ((fileName.endsWith(".log") || fileName.endsWith(".log.gz")) && currentTime - file.lastModified() > timeToDelete) {
                    final boolean result = file.delete();
                    DebugSender.getInstance().sendDebug((result ? "Deleted " : "Could not delete old file ") + fileName, true, !result);
                }
            }
        }
    }
}