package rang.games.contentsBagAPI.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import rang.games.contentsBagAPI.config.ConfigManager;
import rang.games.contentsBagAPI.log.TransactionLogger;
import rang.games.contentsBagAPI.model.ContentItem;
import rang.games.contentsBagAPI.model.PlayerData;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class DatabaseHandler implements AutoCloseable {
    private final HikariDataSource dataSource;
    private final TransactionLogger logger;
    private final ConfigManager config;

    public DatabaseHandler(ConfigManager config, TransactionLogger logger) {
        this.config = config;
        this.logger = logger;

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s",
                config.getDatabaseHost(),
                config.getDatabasePort(),
                config.getDatabaseName()));
        hikariConfig.setUsername(config.getDatabaseUser());
        hikariConfig.setPassword(config.getDatabasePassword());
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(5);
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(hikariConfig);
        initializeTables();
    }
    public boolean isDataActive(UUID playerUUID) {
        String sql = "SELECT data_status FROM server_status WHERE player_uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String status = rs.getString("data_status");
                return "ACTIVE".equals(status);
            }
            return true;
        } catch (Exception e) {
            logger.error("Failed to check data status for player {}: {}", playerUUID, e.getMessage());
            return false;
        }
    }
    private void initializeTables() {
        String createItemsTable = """
            CREATE TABLE IF NOT EXISTS `items` (
                `UUID` CHAR(38) NOT NULL,
                `Type` INT NOT NULL,
                `Slot` INT NOT NULL,
                `Itemstack` LONGTEXT NOT NULL,
                `Price` DECIMAL(20,2) NOT NULL DEFAULT '0.00',
                `Access` INT NOT NULL,
                `Comment` TEXT NOT NULL,
                PRIMARY KEY (`UUID`),
                INDEX `Type` (`Type`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """;

        String createPlayerDataTable = """
            CREATE TABLE IF NOT EXISTS `player_data` (
                `UUID` CHAR(38) NOT NULL,
                `Product` CHAR(38) NOT NULL,
                `Count` INT NOT NULL,
                `Lastupdate` BIGINT NULL,
                PRIMARY KEY (`UUID`, `Product`),
                INDEX `UUID` (`UUID`),
                INDEX `Product` (`Product`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """;

        String createServerTrackingTable = """
            CREATE TABLE IF NOT EXISTS `server_tracking` (
                `id` BIGINT AUTO_INCREMENT,
                `player_uuid` CHAR(38) NOT NULL,
                `from_server` VARCHAR(64) NOT NULL,
                `to_server` VARCHAR(64) NOT NULL,
                `timestamp` BIGINT NOT NULL,
                `status` VARCHAR(20) NOT NULL,
                PRIMARY KEY (`id`),
                INDEX `idx_player_uuid` (`player_uuid`),
                INDEX `idx_timestamp` (`timestamp`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """;

        String createServerStatusTable = """
            CREATE TABLE IF NOT EXISTS `server_status` (
                `player_uuid` CHAR(38) NOT NULL,
                `current_server` VARCHAR(64) NOT NULL,
                `last_server` VARCHAR(64),
                `last_update` BIGINT NOT NULL,
                `transfer_status` BOOLEAN DEFAULT FALSE,
                `data_status` ENUM('ACTIVE', 'SUSPENDED', 'READONLY') DEFAULT 'ACTIVE',
                PRIMARY KEY (`player_uuid`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createItemsTable);
            stmt.execute(createPlayerDataTable);
            stmt.execute(createServerTrackingTable);
            stmt.execute(createServerStatusTable);
        } catch (Exception e) {
            logger.error("Failed to initialize database tables: {}", e.getMessage());
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    public CompletableFuture<Map<UUID, ContentItem>> loadItems() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM items";
            Map<UUID, ContentItem> items = new HashMap<>();

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("UUID"));
                    ContentItem item = new ContentItem(
                            uuid,
                            rs.getString("Itemstack"),
                            rs.getDouble("Price"),
                            rs.getInt("Type"),
                            rs.getInt("Slot")
                    );
                    items.put(uuid, item);
                }
            } catch (Exception e) {
                logger.error("Failed to load items: {}", e.getMessage());
                throw new CompletionException(e);
            }

            return items;
        });
    }
    public CompletableFuture<Optional<PlayerData>> loadPlayerData(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT Product, Count FROM player_data WHERE UUID = ?";
            String statusSql = "SELECT data_status FROM server_status WHERE player_uuid = ?";

            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement statusStmt = conn.prepareStatement(statusSql)) {
                    statusStmt.setString(1, playerUUID.toString());
                    ResultSet statusRs = statusStmt.executeQuery();

                    if (!statusRs.next()) {
                        return initializeNewPlayer(conn, playerUUID);
                    }
                }

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, playerUUID.toString());
                    ResultSet rs = stmt.executeQuery();

                    PlayerData playerData = new PlayerData(playerUUID);
                    boolean hasData = false;

                    while (rs.next()) {
                        hasData = true;
                        UUID itemUUID = UUID.fromString(rs.getString("Product"));
                        int count = rs.getInt("Count");
                        playerData.setItemCount(itemUUID, count);
                    }

                    if (!hasData) {
                        logger.info("No item data found for player {}, treating as new player", playerUUID);
                    }

                    playerData.clearDirty();
                    return Optional.of(playerData);
                }
            } catch (Exception e) {
                logger.error("Failed to load player data for {}: {}", playerUUID, e.getMessage());
                return Optional.empty();
            }
        });
    }
    public CompletableFuture<Boolean> savePlayerData(PlayerData data) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO player_data (UUID, Product, Count, Lastupdate)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                Count = VALUES(Count),
                Lastupdate = VALUES(Lastupdate)
                """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                conn.setAutoCommit(false);
                long updateTime = System.currentTimeMillis();

                for (Map.Entry<UUID, Integer> entry : data.getItemCounts().entrySet()) {
                    stmt.setString(1, data.getPlayerUUID().toString());
                    stmt.setString(2, entry.getKey().toString());
                    stmt.setInt(3, entry.getValue());
                    stmt.setLong(4, updateTime);
                    stmt.addBatch();
                }

                stmt.executeBatch();
                conn.commit();
                return true;
            } catch (Exception e) {
                logger.error("Failed to save player data for {}: {}",
                        data.getPlayerUUID(), e.getMessage());
                return false;
            }
        });
    }

    private Optional<PlayerData> initializeNewPlayer(Connection conn, UUID playerUUID) throws SQLException {
        logger.info("Initializing new player data for {}", playerUUID);

        String initStatusSql = """
        INSERT INTO server_status 
        (player_uuid, current_server, last_update, transfer_status, data_status)
        VALUES (?, ?, ?, FALSE, 'ACTIVE')
        """;

        try (PreparedStatement stmt = conn.prepareStatement(initStatusSql)) {
            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, config.getServerName());
            stmt.setLong(3, System.currentTimeMillis());
            stmt.executeUpdate();
        }

        PlayerData newPlayerData = new PlayerData(playerUUID);
        newPlayerData.clearDirty();

        String trackingSql = """
        INSERT INTO server_tracking 
        (player_uuid, from_server, to_server, timestamp, status)
        VALUES (?, 'NONE', ?, ?, 'NEW_PLAYER')
        """;

        try (PreparedStatement stmt = conn.prepareStatement(trackingSql)) {
            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, config.getServerName());
            stmt.setLong(3, System.currentTimeMillis());
            stmt.executeUpdate();
        }

        return Optional.of(newPlayerData);
    }
    public CompletableFuture<Boolean> updateServerInfo(UUID playerUUID, String fromServer, String toServer) {
        return CompletableFuture.supplyAsync(() -> {
            if (!config.isApiEnabledServer(toServer)) {
                return handleNonApiServerTransfer(playerUUID, fromServer, toServer);
            }

            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);

                try {
                    String trackingSql = """
                        INSERT INTO server_tracking 
                        (player_uuid, from_server, to_server, timestamp, status)
                        VALUES (?, ?, ?, ?, ?)
                        """;

                    try (PreparedStatement stmt = conn.prepareStatement(trackingSql)) {
                        stmt.setString(1, playerUUID.toString());
                        stmt.setString(2, fromServer);
                        stmt.setString(3, toServer);
                        stmt.setLong(4, System.currentTimeMillis());
                        stmt.setString(5, "TRANSFER_STARTED");
                        stmt.executeUpdate();
                    }

                    String statusSql = """
                        INSERT INTO server_status 
                        (player_uuid, current_server, last_server, last_update, transfer_status, data_status)
                        VALUES (?, ?, ?, ?, TRUE, 'ACTIVE')
                        ON DUPLICATE KEY UPDATE
                        last_server = current_server,
                        current_server = ?,
                        last_update = ?,
                        transfer_status = TRUE,
                        data_status = 'ACTIVE'
                        """;

                    try (PreparedStatement stmt = conn.prepareStatement(statusSql)) {
                        stmt.setString(1, playerUUID.toString());
                        stmt.setString(2, toServer);
                        stmt.setString(3, fromServer);
                        stmt.setLong(4, System.currentTimeMillis());
                        stmt.setString(5, toServer);
                        stmt.setLong(6, System.currentTimeMillis());
                        stmt.executeUpdate();
                    }

                    conn.commit();
                    return true;
                } catch (Exception e) {
                    conn.rollback();
                    logger.error("Failed to update server info: {}", e.getMessage());
                    return false;
                }
            } catch (Exception e) {
                logger.error("Database connection failed: {}", e.getMessage());
                return false;
            }
        });
    }

    private boolean handleNonApiServerTransfer(UUID playerUUID, String fromServer, String toServer) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                String statusSql = """
                    INSERT INTO server_status 
                    (player_uuid, current_server, last_server, last_update, transfer_status, data_status)
                    VALUES (?, ?, ?, ?, FALSE, 'SUSPENDED')
                    ON DUPLICATE KEY UPDATE
                    last_server = current_server,
                    current_server = ?,
                    last_update = ?,
                    transfer_status = FALSE,
                    data_status = 'SUSPENDED'
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(statusSql)) {
                    long currentTime = System.currentTimeMillis();
                    stmt.setString(1, playerUUID.toString());
                    stmt.setString(2, toServer);
                    stmt.setString(3, fromServer);
                    stmt.setLong(4, currentTime);
                    stmt.setString(5, toServer);
                    stmt.setLong(6, currentTime);
                    stmt.executeUpdate();
                }

                String trackingSql = """
                    INSERT INTO server_tracking 
                    (player_uuid, from_server, to_server, timestamp, status)
                    VALUES (?, ?, ?, ?, ?)
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(trackingSql)) {
                    stmt.setString(1, playerUUID.toString());
                    stmt.setString(2, fromServer);
                    stmt.setString(3, toServer);
                    stmt.setLong(4, System.currentTimeMillis());
                    stmt.setString(5, "TRANSFERRED_TO_NON_API_SERVER");
                    stmt.executeUpdate();
                }

                conn.commit();
                logger.warn("Player {} transferred to non-API server: {}", playerUUID, toServer);
                return true;
            } catch (Exception e) {
                conn.rollback();
                logger.error("Failed to handle non-API server transfer: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            logger.error("Database connection failed during non-API server transfer: {}", e.getMessage());
            return false;
        }
    }

    public CompletableFuture<List<ContentItem>> loadItemsByType(int type) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM items WHERE Type = ?";
            List<ContentItem> items = new ArrayList<>();

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, type);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    ContentItem item = new ContentItem(
                            UUID.fromString(rs.getString("UUID")),
                            rs.getString("Itemstack"),
                            rs.getDouble("Price"),
                            rs.getInt("Type"),
                            rs.getInt("Slot")
                    );
                    items.add(item);
                }

                logger.info("Loaded {} items of type {}", items.size(), type);
                return items;
            } catch (Exception e) {
                logger.error("Failed to load items of type {}: {}", type, e.getMessage());
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<Set<Integer>> getItemTypes() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT DISTINCT Type FROM items ORDER BY Type";
            Set<Integer> types = new HashSet<>();

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    types.add(rs.getInt("Type"));
                }

                logger.info("Found {} different item types", types.size());
                return types;
            } catch (Exception e) {
                logger.error("Failed to get item types: {}", e.getMessage());
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<Boolean> hasItemType(int type) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM items WHERE Type = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, type);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
                return false;
            } catch (Exception e) {
                logger.error("Failed to check item type existence: {}", e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Map<Integer, Integer>> getItemCountsByType() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT Type, COUNT(*) as count FROM items GROUP BY Type";
            Map<Integer, Integer> counts = new HashMap<>();

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    counts.put(rs.getInt("Type"), rs.getInt("count"));
                }
                return counts;
            } catch (Exception e) {
                logger.error("Failed to get item counts by type: {}", e.getMessage());
                throw new CompletionException(e);
            }
        });
    }
    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}