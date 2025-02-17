package rang.games.contentsBagAPI.storage;

import rang.games.contentsBagAPI.config.ConfigManager;
import rang.games.contentsBagAPI.log.TransactionLogger;
import rang.games.contentsBagAPI.model.PlayerData;

import javax.xml.crypto.Data;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.Set;
import java.util.stream.Collectors;

public class Storage {
    private final Map<UUID, PlayerData> playerData = new ConcurrentHashMap<>();
    private final Set<UUID> loadingPlayers = ConcurrentHashMap.newKeySet();
    private final DatabaseHandler databaseHandler;
    private final TransactionLogger logger;
    private final ConfigManager config;
    private final ItemStorage itemStorage;
    private final ScheduledExecutorService scheduler;

    public Storage(ConfigManager config, TransactionLogger logger) {
        this.config = config;
        this.logger = logger;
        this.databaseHandler = new DatabaseHandler(config, logger);
        this.itemStorage = new ItemStorage(databaseHandler, logger);
        this.scheduler = Executors.newScheduledThreadPool(1);
        startAutoSave();
    }
    public ConfigManager getConfigManager() {
        return config;
    }
    public DatabaseHandler getDatabaseHandler() {
        return databaseHandler;
    }
    public TransactionLogger getLogger() {
        return logger;
    }
    private void startAutoSave() {
        scheduler.scheduleAtFixedRate(() -> {
            playerData.values().stream()
                    .filter(PlayerData::isDirty)
                    .forEach(data -> savePlayerData(data.getPlayerUUID()));
        }, 5, 5, TimeUnit.MINUTES);
    }

    public CompletableFuture<Boolean> setItemCount(UUID playerUUID, UUID itemUUID, int count, String reason) {
        if (!databaseHandler.isDataActive(playerUUID)) {
            logger.warn("Cannot modify data for player {} - data is not active", playerUUID);
            return CompletableFuture.completedFuture(false);
        }
        if (count < 0 || isPlayerLoading(playerUUID)) {
            return CompletableFuture.completedFuture(false);
        }

        PlayerData data = playerData.get(playerUUID);
        if (data == null) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {

                if (!databaseHandler.isDataActive(playerUUID)) {
                    logger.warn("Cannot modify data for player {} - data is not active", playerUUID);
                    return false;
                }

                int oldCount = data.getItemCount(itemUUID);
                data.setItemCount(itemUUID, count);

                logger.logItemTransaction(playerUUID, itemUUID, oldCount, count, reason);

                if (Math.abs(count - oldCount) > 1000) {
                    return databaseHandler.savePlayerData(data).get();
                }

                return true;
            } catch (Exception e) {
                logger.error("Failed to set item count for player {}: {}", playerUUID, e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> addItemCount(UUID playerUUID, UUID itemUUID, int amount, String reason) {
        if (!databaseHandler.isDataActive(playerUUID)) {
            logger.warn("Cannot modify data for player {} - data is not active", playerUUID);
            return CompletableFuture.completedFuture(false);
        }
        if (amount <= 0 || isPlayerLoading(playerUUID)) {
            return CompletableFuture.completedFuture(false);
        }

        PlayerData data = playerData.get(playerUUID);
        if (data == null) {
            return CompletableFuture.completedFuture(false);
        }

        int currentCount = data.getItemCount(itemUUID);
        return setItemCount(playerUUID, itemUUID, currentCount + amount, reason + " (Add)");
    }


    public CompletableFuture<Boolean> removeItemCount(UUID playerUUID, UUID itemUUID, int amount, String reason) {
        if (!databaseHandler.isDataActive(playerUUID)) {
            logger.warn("Cannot modify data for player {} - data is not active", playerUUID);
            return CompletableFuture.completedFuture(false);
        }
        if (amount <= 0 || isPlayerLoading(playerUUID)) {
            return CompletableFuture.completedFuture(false);
        }

        PlayerData data = playerData.get(playerUUID);
        if (data == null) {
            return CompletableFuture.completedFuture(false);
        }

        int currentCount = data.getItemCount(itemUUID);
        if (currentCount < amount) {
            return CompletableFuture.completedFuture(false);
        }

        return setItemCount(playerUUID, itemUUID, currentCount - amount, reason + " (Remove)");
    }


    public CompletableFuture<Boolean> loadPlayerData(UUID playerUUID) {

        return databaseHandler.loadPlayerData(playerUUID)
                .thenApply(optionalData -> {
                    optionalData.ifPresent(data -> playerData.put(playerUUID, data));
                    return optionalData.isPresent();
                })
                .exceptionally(e -> {
                    logger.error("Failed to load player data for {}: {}", playerUUID, e.getMessage());
                    return false;
                });
    }

    public CompletableFuture<Boolean> savePlayerData(UUID playerUUID) {
        PlayerData data = playerData.get(playerUUID);
        if (data == null || !data.isDirty()) {
            return CompletableFuture.completedFuture(true);
        }

        return databaseHandler.savePlayerData(data)
                .thenApply(success -> {
                    if (success) {
                        data.clearDirty();
                    }
                    return success;
                })
                .exceptionally(e -> {
                    logger.error("Failed to save player data for {}: {}", playerUUID, e.getMessage());
                    return false;
                });
    }

    public CompletableFuture<Boolean> saveAndRemovePlayerData(UUID playerUUID) {
        return savePlayerData(playerUUID)
                .thenApply(success -> {
                    if (success) {
                        playerData.remove(playerUUID);
                    }
                    return success;
                });
    }
    public CompletableFuture<Boolean> setDataModifiable(UUID playerUUID, boolean enabled) {
        String status = enabled ? "ACTIVE" : "READONLY";
        return databaseHandler.updateDataStatus(playerUUID, status)
                .thenApply(success -> {
                    if (success && !enabled) {
                        PlayerData data = playerData.get(playerUUID);
                        if (data != null && data.isDirty()) {
                            return savePlayerData(playerUUID).join();
                        }
                    }
                    return success;
                });
    }
    public CompletableFuture<Boolean> handleServerTransfer(UUID playerUUID, String targetServer) {
        PlayerData data = playerData.get(playerUUID);
        if (data == null || isPlayerLoading(playerUUID)) {
            return CompletableFuture.completedFuture(false);
        }
        return savePlayerData(playerUUID)
                .thenCompose(saved -> {
                    if (!saved) {
                        logger.error("Failed to save player data before server transfer: {}", playerUUID);
                        return CompletableFuture.completedFuture(false);
                    }
                    return databaseHandler.updateServerInfo(playerUUID, config.getServerName(), targetServer);
                })
                .thenApply(success -> {
                    if (success) {
                        playerData.remove(playerUUID);
                    }
                    return success;
                });
    }

    public void handleForcedLobbyReturn(UUID playerUUID) {
        PlayerData data = playerData.get(playerUUID);
        if (data == null) return;

        if (data.isDirty()) {
            savePlayerData(playerUUID)
                    .thenAccept(success -> {
                        if (success) {
                            logger.info("Successfully saved data for forced lobby return: {}", playerUUID);
                        } else {
                            logger.error("Failed to save data for forced lobby return: {}", playerUUID);
                        }
                    });
        }
        playerData.remove(playerUUID);
    }

    public CompletableFuture<Void> saveAllPlayerData() {
        List<CompletableFuture<Boolean>> futures = playerData.keySet().stream()
                .map(this::savePlayerData)
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    public void setPlayerLoading(UUID uuid, boolean loading) {
        if (loading) {
            loadingPlayers.add(uuid);
        } else {
            loadingPlayers.remove(uuid);
        }
    }

    public boolean isPlayerLoading(UUID uuid) {
        return loadingPlayers.contains(uuid);
    }

    public PlayerData getPlayerData(UUID uuid) {
        return playerData.get(uuid);
    }

    public ItemStorage getItemStorage() {
        return itemStorage;
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.MINUTES)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}