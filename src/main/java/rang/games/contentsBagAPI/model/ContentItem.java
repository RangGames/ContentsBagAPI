package rang.games.contentsBagAPI.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import rang.games.contentsBagAPI.storage.ItemStorage;

import java.lang.reflect.Method;
import java.util.Base64;
import java.util.UUID;
import java.util.Objects;
import java.util.logging.Logger;

public class ContentItem {
    private final UUID uuid;
    private final String serializedItem;
    private final Double price;
    private final Integer type;
    private final Integer slot;
    private final String itemName;
    private final ItemStack itemStack;
    public ContentItem(UUID uuid, String serializedItem, Double price, Integer type, Integer slot) {
        this.uuid = Objects.requireNonNull(uuid, "UUID cannot be null");
        this.serializedItem = Objects.requireNonNull(serializedItem, "Serialized item cannot be null");
        this.price = Objects.requireNonNull(price, "Price cannot be null");
        this.type = Objects.requireNonNull(type, "Type cannot be null");
        this.slot = Objects.requireNonNull(slot, "Slot cannot be null");
        this.itemStack = extractItem(serializedItem);
        this.itemName = this.itemStack.getItemMeta().getDisplayName();

    }
    private ItemStack extractItem(String serializedItem) {
        try {
            Class<?> classesClass = Class.forName("ch.njol.skript.registrations.Classes");
            Class<?> itemStackClass = Class.forName("org.bukkit.inventory.ItemStack");

            Method getExactClassInfoMethod = classesClass.getMethod("getExactClassInfo", Class.class);
            Object classInfo = getExactClassInfoMethod.invoke(null, itemStackClass);

            if (classInfo == null) {
                return new ItemStack(Material.AIR);
            }

            Method deserializeMethod = classesClass.getMethod("deserialize", classInfo.getClass(), byte[].class);
            Object itemStack = deserializeMethod.invoke(
                    null,
                    classInfo,
                    Base64.getDecoder().decode(serializedItem)
            );

            if (itemStack instanceof ItemStack) {
                ItemStack item = (ItemStack) itemStack;
                if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    return item;
                }
            }
            return new ItemStack(Material.AIR);
        } catch (ClassNotFoundException e) {
            return new ItemStack(Material.AIR);
        } catch (Exception e) {
            Logger.getLogger(ContentItem.class.getName()).warning("Failed to extract item name: " + e.getMessage());
            return new ItemStack(Material.AIR);
        }
    }

    public UUID getUUID() {
        return uuid;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }
    public String getItemName() {
        return itemName;
    }
    public String getSerializedItem() {
        return serializedItem;
    }

    public Double getPrice() {
        return price;
    }

    public Integer getType() {
        return type;
    }

    public Integer getSlot() {
        return slot;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContentItem that = (ContentItem) o;
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @Override
    public String toString() {
        return "ContentItem{" +
                "uuid=" + uuid +
                ", type=" + type +
                ", slot=" + slot +
                ", price=" + price +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID uuid;
        private String serializedItem;
        private Double price;
        private Integer type;
        private Integer slot;

        public Builder uuid(UUID uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder serializedItem(String serializedItem) {
            this.serializedItem = serializedItem;
            return this;
        }

        public Builder price(Double price) {
            this.price = price;
            return this;
        }

        public Builder type(Integer type) {
            this.type = type;
            return this;
        }

        public Builder slot(Integer slot) {
            this.slot = slot;
            return this;
        }

        public ContentItem build() {
            return new ContentItem(uuid, serializedItem, price, type, slot);
        }
    }
}