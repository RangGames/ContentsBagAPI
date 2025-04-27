package rang.games.contentsBagAPI;

import org.bukkit.plugin.java.JavaPlugin;
import rang.games.contentsBagAPI.api.ContentAPI;
import rang.games.contentsBagAPI.config.ConfigManager;
import rang.games.contentsBagAPI.event.EventListener;
import rang.games.contentsBagAPI.log.TransactionLogger;
import rang.games.contentsBagAPI.storage.Storage;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;


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
                        // Load data for all online players
                        getServer().getOnlinePlayers().forEach(player -> {
                            UUID playerUUID = player.getUniqueId();
                            String playerName = player.getName();

                            storage.setPlayerLoading(playerUUID, true);
                            storage.loadPlayerData(playerUUID)
                                    .thenCompose(loaded -> {
                                        if (loaded) {
                                            logger.info("Successfully loaded data for online player {}", playerName);
                                            return ContentAPI.getInstance().setContentBagModifiable(playerUUID, true);
                                        } else {
                                            logger.error("Failed to load data for online player {}", playerName);
                                            return CompletableFuture.completedFuture(false);
                                        }
                                    })
                                    .thenAccept(result -> {
                                        storage.setPlayerLoading(playerUUID, false);
                                        if (!result) {
                                            logger.error("Failed to setup player data for online player {}", playerName);
                                        }
                                    })
                                    .exceptionally(e -> {
                                        storage.setPlayerLoading(playerUUID, false);
                                        logger.error("Error loading data for online player {}: {}",
                                                playerName, e.getMessage());
                                        return null;
                                    });
                        });
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