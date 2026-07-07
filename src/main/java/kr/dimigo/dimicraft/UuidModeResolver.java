package kr.dimigo.dimicraft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class UuidModeResolver {
    private static final int ONLINE_UUID_VERSION = 4;
    private static final int OFFLINE_UUID_VERSION = 3;

    private final Main plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<UUID, String> nameByUuid = new LinkedHashMap<>();
    private final Map<String, UUID> onlineUuidByName = new LinkedHashMap<>();

    UuidModeResolver(Main plugin) {
        this.plugin = plugin;
    }

    enum TargetMode {
        ONLINE,
        OFFLINE
    }

    record KnownPlayer(UUID uuid, String name) {
    }

    TargetMode targetMode() {
        String configured = plugin.settings().uuidMigrationOnlineMode();
        String mode = configured == null ? "auto" : configured.trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "true", "online" -> TargetMode.ONLINE;
            case "false", "offline" -> TargetMode.OFFLINE;
            default -> Bukkit.getOnlineMode() ? TargetMode.ONLINE : TargetMode.OFFLINE;
        };
    }

    String modeName(TargetMode mode) {
        return mode == TargetMode.ONLINE ? "온라인 모드 UUID" : "오프라인 모드 UUID";
    }

    List<KnownPlayer> collectKnownPlayers() {
        loadUserCache();

        Map<UUID, KnownPlayer> players = new LinkedHashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            addKnownPlayer(players, player.getUniqueId(), player.getName());
        }

        for (World world : Bukkit.getWorlds()) {
            collectPlayerData(players, new File(world.getWorldFolder(), "players/data"));
            collectPlayerData(players, new File(world.getWorldFolder(), "playerdata"));
        }

        File[] worldFolders = Bukkit.getWorldContainer().listFiles(File::isDirectory);
        if (worldFolders != null) {
            for (File worldFolder : worldFolders) {
                if (!new File(worldFolder, "level.dat").isFile()) {
                    continue;
                }
                collectPlayerData(players, new File(worldFolder, "players/data"));
                collectPlayerData(players, new File(worldFolder, "playerdata"));
            }
        }

        return List.copyOf(players.values());
    }

    UUID canonicalUuid(KnownPlayer player, TargetMode mode) {
        UUID uuid = player.uuid();
        if (mode == TargetMode.ONLINE) {
            if (uuid.version() == ONLINE_UUID_VERSION) {
                return uuid;
            }
            return player.name() == null ? null : resolveOnlineUuid(player.name());
        }

        if (player.name() != null) {
            return UuidMigrationService.offlineUuid(player.name());
        }
        return uuid.version() == OFFLINE_UUID_VERSION ? uuid : null;
    }

    private void loadUserCache() {
        File file = new File("usercache.json");
        if (!file.isFile()) {
            return;
        }

        try {
            JsonElement root = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            if (!root.isJsonArray()) {
                return;
            }
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                if (!entry.has("name") || !entry.has("uuid")) {
                    continue;
                }

                String name = entry.get("name").getAsString();
                UUID uuid = UUID.fromString(entry.get("uuid").getAsString());
                rememberName(uuid, name);
            }
        } catch (Exception ignored) {
        }
    }

    private void collectPlayerData(Map<UUID, KnownPlayer> players, File playerDataDir) {
        File[] files = playerDataDir.listFiles((dir, name) -> name.endsWith(".dat"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            String name = file.getName();
            String uuidText = name.substring(0, name.length() - ".dat".length());
            try {
                UUID uuid = UUID.fromString(uuidText);
                addKnownPlayer(players, uuid, resolveName(uuid, file));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void addKnownPlayer(Map<UUID, KnownPlayer> players, UUID uuid, String name) {
        String resolvedName = normalizeName(name);
        if (resolvedName == null) {
            resolvedName = nameByUuid.get(uuid);
        } else {
            rememberName(uuid, resolvedName);
        }
        players.put(uuid, new KnownPlayer(uuid, resolvedName));
    }

    private String resolveName(UUID uuid, File datFile) {
        String name = nameByUuid.get(uuid);
        if (name == null) {
            name = normalizeName(Bukkit.getOfflinePlayer(uuid).getName());
        }
        if (name == null) {
            name = normalizeName(UuidMigrationService.readLastKnownNameFromDat(datFile));
        }
        if (name != null) {
            rememberName(uuid, name);
        }
        return name;
    }

    private void rememberName(UUID uuid, String name) {
        String normalized = normalizeName(name);
        if (normalized == null) {
            return;
        }
        nameByUuid.putIfAbsent(uuid, normalized);
        nameByUuid.putIfAbsent(UuidMigrationService.offlineUuid(normalized), normalized);
        if (uuid.version() == ONLINE_UUID_VERSION) {
            onlineUuidByName.putIfAbsent(normalized.toLowerCase(Locale.ROOT), uuid);
        }
    }

    private UUID resolveOnlineUuid(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        UUID cached = onlineUuidByName.get(key);
        if (cached != null) {
            return cached;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            "https://api.mojang.com/users/profiles/minecraft/"
                                    + URLEncoder.encode(name, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            JsonObject profile = JsonParser.parseString(response.body()).getAsJsonObject();
            UUID uuid = UuidMigrationService.fromUndashed(profile.get("id").getAsString());
            onlineUuidByName.put(key, uuid);
            rememberName(uuid, name);
            return uuid;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.trim();
    }
}
