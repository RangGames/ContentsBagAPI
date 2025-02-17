package rang.games.contentsBagAPI.log;

import org.bukkit.plugin.Plugin;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.logging.Logger;

public class TransactionLogger {
    private final Logger logger;
    private final File logFile;
    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter fileFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public TransactionLogger(Plugin plugin) {
        this.logger = plugin.getLogger();
        File logsDir = new File(plugin.getDataFolder(), "logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        this.logFile = new File(logsDir, "transactions-" + LocalDateTime.now().format(fileFormatter) + ".log");
    }

    public void logItemTransaction(UUID playerUUID, UUID itemUUID, int oldCount, int newCount, String reason) {
        String timestamp = LocalDateTime.now().format(dateFormatter);
        String change = newCount - oldCount > 0 ? "+" + (newCount - oldCount) : String.valueOf(newCount - oldCount);
        String logMessage = String.format("[%s] Player: %s | Item: %s | Change: %s (%d → %d) | Reason: %s",
                timestamp, playerUUID, itemUUID, change, oldCount, newCount, reason);

        logger.info(logMessage);

        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.println(logMessage);
        } catch (Exception e) {
            logger.warning("Failed to write to transaction log file: " + e.getMessage());
        }
    }

    public void info(String format, Object... args) {
        String message;
        if (args != null && args.length > 0) {
            message = String.format(format, args);
        } else {
            message = format;
        }
        logger.info(message);
        logToFile("INFO", message);
    }

    public void warn(String format, Object... args) {
        String message;
        if (args != null && args.length > 0) {
            message = String.format(format, args);
        } else {
            message = format;
        }
        logger.warning(message);
        logToFile("WARN", message);
    }

    public void error(String format, Object... args) {
        String message;
        if (args != null && args.length > 0) {
            message = String.format(format, args);
        } else {
            message = format;
        }
        logger.severe(message);
        logToFile("ERROR", message);
    }

    private void logToFile(String level, String message) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.printf("[%s] [%s] %s%n",
                    LocalDateTime.now().format(dateFormatter),
                    level,
                    message);
        } catch (Exception e) {
            logger.warning("Failed to write to log file: " + e.getMessage());
        }
    }
}