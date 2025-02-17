package rang.games.contentsBagAPI.api;

import rang.games.contentsBagAPI.model.ContentItem;
import rang.games.contentsBagAPI.model.PlayerData;
import rang.games.contentsBagAPI.storage.Storage;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.Collections;

public class ContentAPI {
    private static ContentAPI instance;
    private final Storage storage;

    private ContentAPI(Storage storage) {
        this.storage = storage;
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
        return storage.getItemStorage().getItemsByType(type);
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