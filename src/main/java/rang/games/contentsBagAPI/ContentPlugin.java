package rang.games.contentsBagAPI;

import org.bukkit.plugin.java.JavaPlugin;
import rang.games.contentsBagAPI.api.ContentAPI;
import rang.games.contentsBagAPI.config.ConfigManager;
import rang.games.contentsBagAPI.event.EventListener;
import rang.games.contentsBagAPI.log.TransactionLogger;
import rang.games.contentsBagAPI.storage.Storage;


public class ContentPlugin extends JavaPlugin {
    private Storage storage;
    private TransactionLogger logger;
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        this.logger = new TransactionLogger(this);
        this.storage = new Storage(configManager, logger);

        ContentAPI.init(storage);

        getServer().getPluginManager().registerEvents(
                new EventListener(storage, logger, configManager),
                this
        );

        storage.getItemStorage().loadItems()
                .thenAccept(success -> {
                    if (success) {
                        logger.info("Successfully loaded all items");
                    } else {
                        logger.error("Failed to load items");
                    }
                });
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.saveAllPlayerData()
                    .thenRun(() -> logger.info("All player data saved"))
                    .exceptionally(e -> {
                        logger.error("Failed to save all player data: {}", e.getMessage());
                        return null;
                    });
            storage.shutdown();
        }
    }
}