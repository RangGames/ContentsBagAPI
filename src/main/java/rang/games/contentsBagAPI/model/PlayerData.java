package rang.games.contentsBagAPI.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;

public class PlayerData {
    private final UUID playerUUID;
    private final Map<UUID, Integer> itemCounts;
    private boolean dirty;
    private long lastUpdate;

    public PlayerData(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.itemCounts = new ConcurrentHashMap<>();
        this.dirty = false;
        this.lastUpdate = System.currentTimeMillis();
    }

    /**
     * 특정 아이템의 수량을 조회합니다.
     */
    public Integer getItemCount(UUID contentItemUUID) {
        return itemCounts.getOrDefault(contentItemUUID, 0);
    }

    /**
     * 특정 아이템의 수량을 설정합니다.
     */
    public void setItemCount(UUID contentItemUUID, int count) {
        int oldCount = getItemCount(contentItemUUID);
        if (oldCount != count) {
            if (count > 0) {
                itemCounts.put(contentItemUUID, count);
            } else {
                itemCounts.remove(contentItemUUID);
            }
            dirty = true;
            lastUpdate = System.currentTimeMillis();
        }
    }

    /**
     * 플레이어 UUID를 반환합니다.
     */
    public UUID getPlayerUUID() {
        return playerUUID;
    }

    /**
     * 데이터 변경 여부를 반환합니다.
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * 데이터 변경 여부를 초기화합니다.
     */
    public void clearDirty() {
        dirty = false;
    }

    /**
     * 마지막 업데이트 시간을 반환합니다.
     */
    public long getLastUpdate() {
        return lastUpdate;
    }

    /**
     * 모든 아이템 수량을 반환합니다.
     */
    public Map<UUID, Integer> getItemCounts() {
        return Collections.unmodifiableMap(new HashMap<>(itemCounts));
    }

    /**
     * 특정 아이템이 존재하는지 확인합니다.
     */
    public boolean hasItem(UUID contentItemUUID) {
        return itemCounts.containsKey(contentItemUUID);
    }

    /**
     * 보유한 아이템의 총 종류 수를 반환합니다.
     */
    public int getUniqueItemCount() {
        return itemCounts.size();
    }

    /**
     * 모든 아이템 수량을 제거합니다.
     */
    public void clearAllItems() {
        if (!itemCounts.isEmpty()) {
            itemCounts.clear();
            dirty = true;
            lastUpdate = System.currentTimeMillis();
        }
    }

    @Override
    public String toString() {
        return "PlayerData{" +
                "playerUUID=" + playerUUID +
                ", itemCount=" + itemCounts.size() +
                ", dirty=" + dirty +
                ", lastUpdate=" + lastUpdate +
                '}';
    }
}