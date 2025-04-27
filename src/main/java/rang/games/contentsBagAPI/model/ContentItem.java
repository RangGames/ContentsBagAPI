package rang.games.contentsBagAPI.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

public class ContentItem {
    private final UUID uuid;
    private final String serializedItem;
    private final Double price;
    private final Integer type;
    private final Integer slot;
    private final ItemStack itemStack;
    private final String itemName;
    private final String itemTypeName;

    public ContentItem(UUID uuid, String serializedItem, Double price, Integer type, Integer slot) {
        this.uuid = Objects.requireNonNull(uuid, "UUID cannot be null");
        this.serializedItem = Objects.requireNonNull(serializedItem, "Serialized item cannot be null");
        this.price = Objects.requireNonNull(price, "Price cannot be null");
        this.type = Objects.requireNonNull(type, "Type cannot be null");
        this.slot = Objects.requireNonNull(slot, "Slot cannot be null");

        this.itemStack = extractItem(serializedItem);
        this.itemName = resolveItemName(this.itemStack);
        this.itemTypeName = resolveItemTypeName(this.itemStack);
    }

    private ItemStack extractItem(String serializedItem) {
        try {
            Class<?> classesClass = Class.forName("ch.njol.skript.registrations.Classes");
            Class<?> itemStackClass = Class.forName("org.bukkit.inventory.ItemStack");
            Method getExactClassInfoMethod = classesClass.getMethod("getExactClassInfo", Class.class);
            Object classInfo = getExactClassInfoMethod.invoke(null, itemStackClass);

            if (classInfo == null) return new ItemStack(Material.AIR);

            Method deserializeMethod = classesClass.getMethod("deserialize", classInfo.getClass(), byte[].class);
            Object item = deserializeMethod.invoke(null, classInfo, Base64.getDecoder().decode(serializedItem));

            if (item instanceof ItemStack) {
                return (ItemStack) item;
            }
        } catch (Exception e) {
            Logger.getLogger(ContentItem.class.getName()).warning("Failed to deserialize item: " + e.getMessage());
        }
        return new ItemStack(Material.AIR);
    }

    private String resolveItemName(ItemStack item) {
        try {
            Class<?> helperClass = Class.forName("com.meowj.langutils.lang.LanguageHelper");
            Method method = helperClass.getMethod("getItemDisplayName", ItemStack.class, String.class);
            Object name = method.invoke(null, item, "ko_kr");
            if (name instanceof String) return (String) name;
        } catch (Exception e) {
            Logger.getLogger(ContentItem.class.getName()).warning("Failed to resolve item name: " + e.getMessage());
        }
        return "UNKNOWN_ITEM";
    }

    private String resolveItemTypeName(ItemStack item) {
        try {
            if (item.getType() == null) return "AIR";
            Class<?> helperClass = Class.forName("com.meowj.langutils.lang.LanguageHelper");
            Method method = helperClass.getMethod("getItemDisplayName", ItemStack.class, String.class);
            Object name = method.invoke(null, new ItemStack(item.getType()), "ko_kr");
            if (name instanceof String) return (String) name;
        } catch (Exception e) {
            Logger.getLogger(ContentItem.class.getName()).warning("Failed to resolve item type name: " + e.getMessage());
        }
        return "UNKNOWN_TYPE";
    }

    public UUID getUUID() { return uuid; }
    public String getSerializedItem() { return serializedItem; }
    public Double getPrice() { return price; }
    public Integer getType() { return type; }
    public Integer getSlot() { return slot; }
    public ItemStack getItemStack() { return itemStack; }
    public String getItemName() { return itemName; }
    public String getItemTypeName() { return itemTypeName; }

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
                ", itemName='" + itemName + '\'' +
                ", itemTypeName='" + itemTypeName + '\'' +
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

        public Builder uuid(UUID uuid) { this.uuid = uuid; return this; }
        public Builder serializedItem(String serializedItem) { this.serializedItem = serializedItem; return this; }
        public Builder price(Double price) { this.price = price; return this; }
        public Builder type(Integer type) { this.type = type; return this; }
        public Builder slot(Integer slot) { this.slot = slot; return this; }

        public ContentItem build() { return new ContentItem(uuid, serializedItem, price, type, slot); }
    }
}