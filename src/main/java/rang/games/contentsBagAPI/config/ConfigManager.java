package rang.games.contentsBagAPI.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ConfigManager {
    private final JavaPlugin plugin;
    private final String serverName;
    private final String lobbyServerName;
    private final Set<String> apiEnabledServers;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();

        config.addDefault("server-name", "unknown");
        config.addDefault("lobby-server-name", "lobby");
        config.addDefault("database.host", "localhost");
        config.addDefault("database.port", 3306);
        config.addDefault("database.name", "content_db");
        config.addDefault("database.user", "root");
        config.addDefault("database.password", "");
        config.options().copyDefaults(true);
        plugin.saveConfig();

        this.serverName = config.getString("server-name");
        this.lobbyServerName = config.getString("lobby-server-name");
        this.apiEnabledServers = new HashSet<>(plugin.getConfig().getStringList("api-enabled-servers"));

    }

    public boolean isApiEnabledServer(String serverName) {
        return apiEnabledServers.contains(serverName);
    }

    public Set<String> getApiEnabledServers() {
        return Collections.unmodifiableSet(apiEnabledServers);
    }
    public String getServerName() {
        return serverName;
    }

    public String getLobbyServerName() {
        return lobbyServerName;
    }

    public boolean isLobbyServer() {
        return serverName.equals(lobbyServerName);
    }

    public String getDatabaseHost() {
        return plugin.getConfig().getString("database.host");
    }

    public int getDatabasePort() {
        return plugin.getConfig().getInt("database.port");
    }

    public String getDatabaseName() {
        return plugin.getConfig().getString("database.name");
    }

    public String getDatabaseUser() {
        return plugin.getConfig().getString("database.user");
    }

    public String getDatabasePassword() {
        return plugin.getConfig().getString("database.password");
    }
}