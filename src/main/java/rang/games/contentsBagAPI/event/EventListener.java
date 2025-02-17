package rang.games.contentsBagAPI.event;


import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import rang.games.allPlayersUtil.event.NetworkJoinEvent;
import rang.games.allPlayersUtil.event.NetworkQuitEvent;
import rang.games.allPlayersUtil.event.ServerSwitchEvent;
import rang.games.contentsBagAPI.api.ContentAPI;
import rang.games.contentsBagAPI.config.ConfigManager;
import rang.games.contentsBagAPI.log.TransactionLogger;
import rang.games.contentsBagAPI.storage.Storage;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class EventListener implements Listener {
    private final Storage storage;
    private final TransactionLogger logger;
    private final ConfigManager config;
    private final Set<UUID> pendingLobbyLoads = ConcurrentHashMap.newKeySet();

    public EventListener(Storage storage, TransactionLogger logger, ConfigManager config) {
        this.storage = storage;
        this.logger = logger;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        String playerName = event.getPlayer().getName();

        storage.setPlayerLoading(playerUUID, true);

        storage.loadPlayerData(playerUUID)
                .thenCompose(success -> {
                    if (success) {
                        logger.info("Successfully loaded data for player {}", playerName);
                        return ContentAPI.getInstance().setContentBagModifiable(playerUUID, true);
                    } else {
                        logger.error("Failed to load data for player {}", playerName);
                        return CompletableFuture.completedFuture(false);
                    }
                })
                .thenAccept(result -> {
                    storage.setPlayerLoading(playerUUID, false);
                    if (!result) {
                        logger.error("Failed to setup player data for {}", playerName);
                    }
                })
                .exceptionally(e -> {
                    storage.setPlayerLoading(playerUUID, false);
                    logger.error("Error during player join process for {}: {}",
                            playerName, e.getMessage());
                    return null;
                });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNetworkQuit(NetworkQuitEvent event) {
        UUID playerUUID = UUID.fromString(event.getPlayerUuid());
        String playerName = event.getPlayerName();
        String serverName = event.getServerName();

        logger.info("Player {} disconnecting from network (last server: {})",
                playerName, serverName);
        if (!serverName.equalsIgnoreCase(config.getServerName())) return;
        storage.saveAndRemovePlayerData(playerUUID)
                .thenAccept(success -> {
                    if (success) {
                        logger.info("Successfully saved and removed data for player {}", playerName);
                    } else {
                        logger.error("Failed to save data for player {}", playerName);
                    }
                });
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        if (event.getTo().getBlockX() == event.getFrom().getBlockX()
                && event.getTo().getBlockY() == event.getFrom().getBlockY()
                && event.getTo().getBlockZ() == event.getFrom().getBlockZ()) {
            return;
        }

        if (!storage.getDatabaseHandler().isDataActive(playerUUID)) {
            event.setCancelled(true);
        }
    }
}
