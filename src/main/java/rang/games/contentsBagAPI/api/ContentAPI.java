package rang.games.contentsBagAPI.api;

import rang.games.contentsBagAPI.log.TransactionLogger;
import rang.games.contentsBagAPI.model.ContentItem;
import rang.games.contentsBagAPI.model.PlayerData;
import rang.games.contentsBagAPI.storage.Storage;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ContentAPI {
    private static ContentAPI instance;
    private final Storage storage;
    private final TransactionLogger logger;

    private ContentAPI(Storage storage) {
        this.storage = storage;
        this.logger = storage.getLogger();

    }

    public static void init(Storage storage) {
        if (instance == null) {
            instance = new ContentAPI(storage);
        }
    }

    public static ContentAPI getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ContentAPI has not been initialized");
        }
        return instance;
    }
    public CompletableFuture<Boolean> recoverFromFailedTransfer(UUID playerUUID) {
        if (storage.isPlayerLoading(playerUUID)) {
            logger.error("Cannot recover player {} - data is still loading", playerUUID);
            return CompletableFuture.completedFuture(false);
        }

        return storage.validateTransferStatus(playerUUID)
                .thenCompose(isTransferring -> {
                    if (!isTransferring) {
                        logger.warn("Player {} is not in transfer state, recovery not needed", playerUUID);
                        return CompletableFuture.completedFuture(false);
                    }
                    return setContentBagModifiable(playerUUID, true)
                            .thenCompose(success -> {
                                if (!success) {
                                    logger.error("Failed to reactivate content bag for player {}", playerUUID);
                                    return CompletableFuture.completedFuture(false);
                                }

                                return storage.loadPlayerData(playerUUID)
                                        .thenApply(loaded -> {
                                            if (!loaded) {
                                                logger.error("Failed to reload data for player {}", playerUUID);
                                                return false;
                                            }
                                            logger.info("Successfully recovered from failed transfer for player {}", playerUUID);
                                            return true;
                                        });
                            });
                })
                .exceptionally(e -> {
                    logger.error("Error during transfer recovery for player {}: {}",
                            playerUUID, e.getMessage());
                    return false;
                });
    }
    public CompletableFuture<Boolean> prepareAndTransferServer(UUID playerUUID, String targetServer) {
        return saveDirtyState(playerUUID)
                .thenCompose(saved -> {
                    if (!saved) {
                        logger.error("Failed to save dirty state for player {}", playerUUID);
                        return CompletableFuture.completedFuture(false);
                    }
                    return setContentBagModifiable(playerUUID, false);
                })
                .thenCompose(disabled -> {
                    if (!disabled) {
                        logger.error("Failed to disable content bag for player {}", playerUUID);
                        return CompletableFuture.completedFuture(false);
                    }
                    return storage.handleServerTransfer(playerUUID, targetServer);
                })
                .exceptionally(e -> {
                    logger.error("Server transfer preparation failed for player {}: {}",
                            playerUUID, e.getMessage());
                    setContentBagModifiable(playerUUID, true);
                    return false;
                });
    }
    /**
     * 플레이어의 특정 아이템 수량을 조회합니다.
     * @param playerUUID 플레이어 UUID
     * @param contentItemUUID 콘텐츠 아이템 UUID
     * @return 아이템 수량 (플레이어나 아이템이 없는 경우 0 반환)
     */
    public int getItemCount(UUID playerUUID, UUID contentItemUUID) {
        if (storage.isPlayerLoading(playerUUID)) {
            return 0;
        }

        PlayerData playerData = storage.getPlayerData(playerUUID);
        if (playerData == null) {
            return 0;
        }

        return playerData.getItemCount(contentItemUUID);
    }

    /**
     * 플레이어의 특정 아이템 수량을 설정합니다.
     */
    public CompletableFuture<Boolean> setItemCount(UUID playerUUID, UUID contentItemUUID, int count, String reason) {
        if (count < 0 || storage.isPlayerLoading(playerUUID)) {
            return CompletableFuture.completedFuture(false);
        }

        if (!storage.getItemStorage().hasItem(contentItemUUID)) {
            return CompletableFuture.completedFuture(false);
        }

        return storage.setItemCount(playerUUID, contentItemUUID, count, reason);
    }

    /**
     * API를 사용하지 않는 서버로 이동할 때 호출되는 메소드
     */
    public CompletableFuture<Boolean> prepareNonApiServerTransfer(UUID playerUUID, String targetServer) {
        return saveDirtyState(playerUUID)
                .thenCompose(saved -> {
                    if (!saved) {
                        logger.error("Failed to save data for non-API server transfer: {}", playerUUID);
                        return CompletableFuture.completedFuture(false);
                    }
                    return storage.handleServerTransfer(playerUUID, targetServer);
                });
    }

    public CompletableFuture<Boolean> saveDirtyState(UUID playerUUID) {
        PlayerData playerData = storage.getPlayerData(playerUUID);
        if (playerData == null || !playerData.isDirty() || storage.isPlayerLoading(playerUUID)) {
            return CompletableFuture.completedFuture(true);
        }

        return storage.savePlayerData(playerUUID);
    }

    public CompletableFuture<Boolean> setContentBagModifiable(UUID playerUUID, boolean enabled) {
        return storage.setDataModifiable(playerUUID, enabled);
    }

    /**
     * 플레이어의 특정 아이템 수량을 증가시킵니다.
     */
    public CompletableFuture<Boolean> addItemCount(UUID playerUUID, UUID contentItemUUID, int amount, String reason) {
        if (amount <= 0 || storage.isPlayerLoading(playerUUID)) {
            return CompletableFuture.completedFuture(false);
        }

        PlayerData playerData = storage.getPlayerData(playerUUID);
        if (playerData == null) {
            return CompletableFuture.completedFuture(false);
        }

        int currentCount = playerData.getItemCount(contentItemUUID);
        return setItemCount(playerUUID, contentItemUUID, currentCount + amount,
                String.format("%s (Add: %d)", reason, amount));
    }
    public Storage getStorage() {
        return storage;
    }

    /**
     * 플레이어의 특정 아이템 수량을 감소시킵니다.
     */
    public CompletableFuture<Boolean> removeItemCount(UUID playerUUID, UUID contentItemUUID, int amount, String reason) {
        if (amount <= 0 || storage.isPlayerLoading(playerUUID)) {
            return CompletableFuture.completedFuture(false);
        }

        PlayerData playerData = storage.getPlayerData(playerUUID);
        if (playerData == null) {
            return CompletableFuture.completedFuture(false);
        }

        int currentCount = playerData.getItemCount(contentItemUUID);
        if (currentCount < amount) {
            return CompletableFuture.completedFuture(false);
        }

        return setItemCount(playerUUID, contentItemUUID, currentCount - amount,
                String.format("%s (Remove: %d)", reason, amount));
    }

    /**
     * 특정 타입의 모든 콘텐츠 아이템을 조회합니다.
     */
    public Map<UUID, ContentItem> getItemsByType(int type) {
        Map<UUID, ContentItem> originalMap = storage.getItemStorage().getItemsByType(type);

        return originalMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.comparing(ContentItem::getSlot)))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    public Map<UUID, ContentItem> getItemsByType(int type, int offset, int limit) {
        Map<UUID, ContentItem> originalMap = storage.getItemStorage().getItemsByType(type);

        return originalMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.comparing(ContentItem::getSlot)))
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    /**
     * 특정 콘텐츠 아이템을 조회합니다.
     */
    public ContentItem getContentItem(UUID itemUUID) {
        return storage.getItemStorage().getItem(itemUUID);
    }

    /**
     * 플레이어가 보유한 모든 아이템의 수량을 조회합니다.
     */
    public Map<UUID, Integer> getAllItemCounts(UUID playerUUID) {
        PlayerData playerData = storage.getPlayerData(playerUUID);
        if (playerData == null || storage.isPlayerLoading(playerUUID)) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(playerData.getItemCounts());
    }

    /**
     * 데이터 로딩 상태를 확인합니다.
     */
    public boolean isPlayerDataLoading(UUID playerUUID) {
        return storage.isPlayerLoading(playerUUID);
    }
}