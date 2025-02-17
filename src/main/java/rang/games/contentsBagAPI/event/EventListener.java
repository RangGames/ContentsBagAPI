package rang.games.contentsBagAPI.event;


import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import rang.games.allPlayersUtil.event.NetworkJoinEvent;
import rang.games.allPlayersUtil.event.NetworkQuitEvent;
import rang.games.allPlayersUtil.event.ServerSwitchEvent;
import rang.games.contentsBagAPI.config.ConfigManager;
import rang.games.contentsBagAPI.log.TransactionLogger;
import rang.games.contentsBagAPI.storage.Storage;

import java.util.Set;
import java.util.UUID;
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
    public void onNetworkJoin(NetworkJoinEvent event) {
        UUID playerUUID = UUID.fromString(event.getPlayerUuid());
        String playerName = event.getPlayerName();

        if (config.isLobbyServer()) {
            logger.info("Player {} joining network through lobby", playerName);
            pendingLobbyLoads.add(playerUUID);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        String playerName = event.getPlayer().getName();

        if (config.isLobbyServer() && pendingLobbyLoads.remove(playerUUID)) {
            storage.setPlayerLoading(playerUUID, true);

            storage.loadPlayerData(playerUUID)
                    .thenAccept(success -> {
                        storage.setPlayerLoading(playerUUID, false);
                        if (success) {
                            logger.info("Successfully loaded data for player {}", playerName);
                        } else {
                            logger.error("Failed to load data for player {}", playerName);
                        }
                    });
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onServerSwitch(ServerSwitchEvent event) {
        UUID playerUUID = UUID.fromString(event.getPlayerUuid());
        String playerName = event.getPlayerName();
        String fromServer = event.getFromServer();
        String toServer = event.getToServer();

        if (!fromServer.equals(config.getLobbyServerName()) &&
                toServer.equals(config.getLobbyServerName())) {
            logger.warn("Abnormal server switch detected for player {} from {} to lobby",
                    playerName, fromServer);
            storage.handleForcedLobbyReturn(playerUUID);
            return;
        }

        storage.handleServerTransfer(playerUUID, toServer)
                .thenAccept(success -> {
                    if (success) {
                        logger.info("Server transfer successful for player {} ({} -> {})",
                                playerName, fromServer, toServer);
                    } else {
                        logger.error("Server transfer failed for player {} ({} -> {})",
                                playerName, fromServer, toServer);
                    }
                });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNetworkQuit(NetworkQuitEvent event) {
        UUID playerUUID = UUID.fromString(event.getPlayerUuid());
        String playerName = event.getPlayerName();
        String serverName = event.getServerName();

        logger.info("Player {} disconnecting from network (last server: {})",
                playerName, serverName);

        storage.saveAndRemovePlayerData(playerUUID)
                .thenAccept(success -> {
                    if (success) {
                        logger.info("Successfully saved and removed data for player {}", playerName);
                    } else {
                        logger.error("Failed to save data for player {}", playerName);
                    }
                });
    }
}
