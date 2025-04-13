package rang.games.contentsBagAPI.storage;

import rang.games.contentsBagAPI.log.TransactionLogger;
import rang.games.contentsBagAPI.model.ContentItem;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.Collections;

public class ItemStorage {
    private final Map<Integer, Map<UUID, ContentItem>> itemsByType = new ConcurrentHashMap<>();
    private final Map<UUID, ContentItem> itemsById = new ConcurrentHashMap<>();
    private final DatabaseHandler databaseHandler;
    private final TransactionLogger logger;
    private volatile boolean initialLoadComplete = false;

    public ItemStorage(DatabaseHandler databaseHandler, TransactionLogger logger) {
        this.databaseHandler = databaseHandler;
        this.logger = logger;
    }

    public CompletableFuture<Boolean> loadItems() {
        return databaseHandler.loadItems()
                .thenApply(items -> {
                    itemsById.clear();
                    itemsByType.clear();

                    items.values().forEach(item -> {
                        itemsById.put(item.getUUID(), item);
                        itemsByType.computeIfAbsent(item.getType(), k -> new ConcurrentHashMap<>())
                                .put(item.getUUID(), item);
                    });

                    initialLoadComplete = true;
                    logger.info("Loaded {} items across {} types",
                            items.size(), itemsByType.size());
                    return true;
                })
                .exceptionally(e -> {
                    logger.error("Failed to load items: {}", e.getMessage());
                    return false;
                });
    }

    public ContentItem getItem(UUID uuid) {
        return itemsById.get(uuid);
    }

    public Map<UUID, ContentItem> getItemsByType(int type) {
        return Collections.unmodifiableMap(
                itemsByType.getOrDefault(type, Collections.emptyMap())
        );
    }

    public Map<UUID, ContentItem> getAllItems() {
        return Collections.unmodifiableMap(itemsById);
    }

    public boolean isInitialLoadComplete() {
        return initialLoadComplete;
    }

    public int getItemTypeCount() {
        return itemsByType.size();
    }

    public int getTotalItemCount() {
        return itemsById.size();
    }

    public boolean hasItem(UUID uuid) {
        return itemsById.containsKey(uuid);
    }

    public boolean hasItemType(int type) {
        return itemsByType.containsKey(type);
    }

    public Map<Integer, Integer> getItemCountsByType() {
        Map<Integer, Integer> counts = new ConcurrentHashMap<>();
        itemsByType.forEach((type, items) -> counts.put(type, items.size()));
        return Collections.unmodifiableMap(counts);
    }

    /**
     * 아이템 타입별 캐시를 무효화하고 다시 로드합니다.
     * @param type 무효화할 아이템 타입
     * @return 성공 여부
     */
    public CompletableFuture<Boolean> invalidateAndReloadType(int type) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<UUID, ContentItem> oldItems = itemsByType.get(type);
                if (oldItems != null) {
                    oldItems.keySet().forEach(itemsById::remove);
                }
                itemsByType.remove(type);

                return databaseHandler.loadItemsByType(type)
                        .thenApply(newItems -> {
                            Map<UUID, ContentItem> typeItems = new ConcurrentHashMap<>();
                            newItems.forEach(item -> {
                                itemsById.put(item.getUUID(), item);
                                typeItems.put(item.getUUID(), item);
                            });
                            itemsByType.put(type, typeItems);
                            logger.info("Reloaded {} items for type {}", typeItems.size(), type);
                            return true;
                        })
                        .exceptionally(e -> {
                            logger.error("Failed to reload items for type {}: {}", type, e.getMessage());
                            return false;
                        })
                        .get();
            } catch (Exception e) {
                logger.error("Error during type invalidation: {}", e.getMessage());
                return false;
            }
        });
    }

    /**
     * 모든 아이템 캐시를 무효화하고 다시 로드합니다.
     */
    public CompletableFuture<Boolean> invalidateAndReloadAll() {
        initialLoadComplete = false;
        return loadItems();
    }
}