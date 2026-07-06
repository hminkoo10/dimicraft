package kr.dimigo.dimicraft;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.ban.BanListType;
import org.bukkit.BanEntry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.ban.ProfileBanList;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * online-mode 전환 시 기존 플레이어 데이터를 새 UUID 체계로 옮긴다.
 * 온라인(정품) UUID는 버전 4, 오프라인 UUID("OfflinePlayer:이름" MD5)는 버전 3이므로
 * 파일명의 UUID 버전만으로 어느 모드의 데이터인지 구분할 수 있어 상태 파일 없이 멱등하게 동작한다.
 */
public final class UuidMigrationService {
    private static final String LOG_PREFIX = "[UUID 마이그레이션] ";
    private static final int ONLINE_UUID_VERSION = 4;
    private static final int OFFLINE_UUID_VERSION = 3;
    private static final int MOJANG_BULK_LOOKUP_LIMIT = 10;
    private static final Pattern UUID_TOKEN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final int NBT_TAG_END = 0;
    private static final int NBT_TAG_STRING = 8;
    private static final int NBT_TAG_LIST = 9;
    private static final int NBT_TAG_COMPOUND = 10;

    private final Main plugin;
    private final Logger log;
    private final PersonalSpawnStore spawnStore;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<UUID, String> nameByCachedUuid = new HashMap<>();
    private final Map<UUID, String> nameByOfflineUuid = new HashMap<>();
    private final Map<String, UUID> onlineUuidByName = new HashMap<>();
    private final Set<String> unresolvableNames = new HashSet<>();
    private boolean overwriteExisting = true;
    private Path backupDir;

    public UuidMigrationService(Main plugin, PersonalSpawnStore spawnStore) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.spawnStore = spawnStore;
    }

    public void migrateIfNeeded() {
        DimicraftSettings settings = plugin.settings();
        if (!settings.uuidMigrationEnabled()) {
            return;
        }
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            log.warning(LOG_PREFIX + "접속 중인 플레이어가 있어 마이그레이션을 건너뜁니다. 서버를 재시작해 주세요.");
            return;
        }

        boolean targetOnline = resolveTargetOnline(settings.uuidMigrationOnlineMode());
        overwriteExisting = settings.uuidMigrationOverwriteExisting();
        loadUserCache();

        Map<UUID, String> candidates = collectPlayerDataCandidates(targetOnline);
        if (targetOnline) {
            resolveOnlineUuids(candidates.values());
        }

        int migratedPlayers = 0;
        int movedFiles = 0;
        for (Map.Entry<UUID, String> candidate : candidates.entrySet()) {
            UUID oldUuid = candidate.getKey();
            String name = candidate.getValue();
            UUID newUuid = resolveNewUuid(oldUuid, name, targetOnline);
            if (newUuid == null || newUuid.equals(oldUuid)) {
                continue;
            }
            int moved = movePlayerFiles(oldUuid, newUuid);
            if (moved > 0) {
                preservePersonalSpawn(oldUuid, newUuid);
                migratedPlayers++;
                movedFiles += moved;
                log.info(LOG_PREFIX + name + ": " + oldUuid + " -> " + newUuid + " (파일 " + moved + "개)");
            }
        }

        int whitelisted = migrateWhitelist(targetOnline);
        int operators = migrateOperators(targetOnline);
        int bans = migrateBans(targetOnline);
        int discordLinks = migrateDiscordSrvLinks(targetOnline);

        if (migratedPlayers + whitelisted + operators + bans + discordLinks == 0) {
            log.info(LOG_PREFIX + "모든 데이터가 이미 " + modeName(targetOnline) + " 기준입니다.");
            return;
        }
        log.info(LOG_PREFIX + modeName(targetOnline) + " 기준으로 마이그레이션 완료 — 플레이어 "
                + migratedPlayers + "명(파일 " + movedFiles + "개), 화이트리스트 " + whitelisted
                + "건, OP " + operators + "건, 밴 " + bans + "건, DiscordSRV 연동 " + discordLinks + "건"
                + (backupDir != null ? " / 백업: " + backupDir : ""));
    }

    private boolean resolveTargetOnline(String configured) {
        String mode = configured == null ? "auto" : configured.trim().toLowerCase(Locale.ROOT);
        boolean serverOnline = Bukkit.getOnlineMode();
        boolean target = switch (mode) {
            case "true", "online" -> true;
            case "false", "offline" -> false;
            default -> serverOnline;
        };
        if (target != serverOnline) {
            log.warning(LOG_PREFIX + "config의 uuid-migration.online-mode(" + mode
                    + ")가 server.properties의 online-mode(" + serverOnline
                    + ")와 다릅니다. 프록시 뒤에서 돌리는 게 아니라면 두 설정을 맞춰 주세요.");
        }
        return target;
    }

    private void loadUserCache() {
        File file = new File("usercache.json");
        if (!file.isFile()) {
            log.warning(LOG_PREFIX + "usercache.json이 없습니다. playerdata의 lastKnownName으로 대신 조회합니다.");
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
                try {
                    String name = entry.get("name").getAsString();
                    nameByCachedUuid.put(UUID.fromString(entry.get("uuid").getAsString()), name);
                    nameByOfflineUuid.put(offlineUuid(name), name);
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (Exception ex) {
            log.warning(LOG_PREFIX + "usercache.json을 읽지 못했습니다: " + ex.getMessage());
        }
    }

    private Map<UUID, String> collectPlayerDataCandidates(boolean targetOnline) {
        Map<UUID, String> candidates = new LinkedHashMap<>();
        for (File playerDataDir : playerDataDirs()) {
            File[] files = playerDataDir.listFiles((dir, fileName) -> fileName.endsWith(".dat"));
            if (files == null) {
                continue;
            }
            for (File file : files) {
                String baseName = file.getName().substring(0, file.getName().length() - ".dat".length());
                UUID uuid;
                try {
                    uuid = UUID.fromString(baseName);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                if (!needsMigration(uuid, targetOnline) || candidates.containsKey(uuid)) {
                    continue;
                }
                String name = resolveName(uuid);
                if (name == null) {
                    log.warning(LOG_PREFIX + "이름을 알 수 없어 건너뜁니다: " + file.getName());
                    continue;
                }
                candidates.put(uuid, name);
            }
        }
        return candidates;
    }

    private List<File> playerDataDirs() {
        List<File> dirs = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            File dir = new File(world.getWorldFolder(), "playerdata");
            if (dir.isDirectory() && !dirs.contains(dir)) {
                dirs.add(dir);
            }
        }
        return dirs;
    }

    private boolean needsMigration(UUID uuid, boolean targetOnline) {
        int version = uuid.version();
        return targetOnline ? version == OFFLINE_UUID_VERSION : version == ONLINE_UUID_VERSION;
    }

    private String resolveName(UUID uuid) {
        String name = nameByCachedUuid.get(uuid);
        if (name == null) {
            name = nameByOfflineUuid.get(uuid);
        }
        if (name == null) {
            name = Bukkit.getOfflinePlayer(uuid).getName();
        }
        if (name == null) {
            name = readLastKnownName(uuid);
        }
        if (name == null && uuid.version() == ONLINE_UUID_VERSION) {
            name = lookupNameFromMojang(uuid);
        }
        if (name != null) {
            nameByCachedUuid.putIfAbsent(uuid, name);
        }
        return name;
    }

    private UUID resolveNewUuid(UUID oldUuid, String name, boolean targetOnline) {
        if (name == null || name.isBlank()) {
            log.warning(LOG_PREFIX + oldUuid + "의 이름을 확인할 수 없어 건너뜁니다.");
            return null;
        }
        if (!targetOnline) {
            return offlineUuid(name);
        }
        resolveOnlineUuids(List.of(name));
        UUID online = onlineUuidByName.get(name.toLowerCase(Locale.ROOT));
        if (online == null) {
            log.warning(LOG_PREFIX + "Mojang에 등록되지 않은 이름이라 건너뜁니다: " + name + " (" + oldUuid + ")");
        }
        return online;
    }

    private void resolveOnlineUuids(Collection<String> names) {
        List<String> pending = new ArrayList<>();
        for (String name : new LinkedHashSet<>(names)) {
            String key = name.toLowerCase(Locale.ROOT);
            if (!onlineUuidByName.containsKey(key) && !unresolvableNames.contains(key)) {
                pending.add(name);
            }
        }
        for (int i = 0; i < pending.size(); i += MOJANG_BULK_LOOKUP_LIMIT) {
            List<String> batch = pending.subList(i, Math.min(i + MOJANG_BULK_LOOKUP_LIMIT, pending.size()));
            if (!resolveOnlineUuidBatch(batch)) {
                batch.forEach(this::resolveOnlineUuidSingle);
            }
            for (String name : batch) {
                String key = name.toLowerCase(Locale.ROOT);
                if (!onlineUuidByName.containsKey(key)) {
                    unresolvableNames.add(key);
                }
            }
        }
    }

    private boolean resolveOnlineUuidBatch(List<String> names) {
        JsonArray body = new JsonArray();
        names.forEach(body::add);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.mojang.com/profiles/minecraft"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return false;
            }
            for (JsonElement element : JsonParser.parseString(response.body()).getAsJsonArray()) {
                JsonObject profile = element.getAsJsonObject();
                onlineUuidByName.put(
                        profile.get("name").getAsString().toLowerCase(Locale.ROOT),
                        fromUndashed(profile.get("id").getAsString()));
            }
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    private void resolveOnlineUuidSingle(String name) {
        JsonObject profile = getJson("https://api.mojang.com/users/profiles/minecraft/" + name);
        if (profile != null && profile.has("id")) {
            onlineUuidByName.put(name.toLowerCase(Locale.ROOT), fromUndashed(profile.get("id").getAsString()));
        }
    }

    private String lookupNameFromMojang(UUID uuid) {
        JsonObject profile = getJson(
                "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", ""));
        return profile != null && profile.has("name") ? profile.get("name").getAsString() : null;
    }

    private JsonObject getJson(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            JsonElement root = JsonParser.parseString(response.body());
            return root.isJsonObject() ? root.getAsJsonObject() : null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ex) {
            log.warning(LOG_PREFIX + "Mojang API 요청 실패(" + url + "): " + ex.getMessage());
            return null;
        }
    }

    private String readLastKnownName(UUID uuid) {
        for (File dir : playerDataDirs()) {
            File dat = new File(dir, uuid + ".dat");
            if (!dat.isFile()) {
                continue;
            }
            String name = readLastKnownNameFromDat(dat);
            if (name != null) {
                return name;
            }
        }
        return null;
    }

    /** playerdata .dat(gzip NBT)에서 CraftBukkit이 기록하는 bukkit.lastKnownName을 읽는다. */
    private static String readLastKnownNameFromDat(File datFile) {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new GZIPInputStream(new FileInputStream(datFile))))) {
            if (in.readUnsignedByte() != NBT_TAG_COMPOUND) {
                return null;
            }
            in.readUTF();
            int type;
            while ((type = in.readUnsignedByte()) != NBT_TAG_END) {
                String name = in.readUTF();
                if (type == NBT_TAG_COMPOUND && name.equals("bukkit")) {
                    int innerType;
                    while ((innerType = in.readUnsignedByte()) != NBT_TAG_END) {
                        String innerName = in.readUTF();
                        if (innerType == NBT_TAG_STRING && innerName.equals("lastKnownName")) {
                            return in.readUTF();
                        }
                        skipNbtPayload(in, innerType);
                    }
                } else {
                    skipNbtPayload(in, type);
                }
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static void skipNbtPayload(DataInputStream in, int type) throws IOException {
        switch (type) {
            case 1 -> in.skipNBytes(1);
            case 2 -> in.skipNBytes(2);
            case 3, 5 -> in.skipNBytes(4);
            case 4, 6 -> in.skipNBytes(8);
            case 7 -> in.skipNBytes(in.readInt());
            case NBT_TAG_STRING -> in.skipNBytes(in.readUnsignedShort());
            case NBT_TAG_LIST -> {
                int elementType = in.readUnsignedByte();
                int length = in.readInt();
                for (int i = 0; i < length; i++) {
                    skipNbtPayload(in, elementType);
                }
            }
            case NBT_TAG_COMPOUND -> {
                int childType;
                while ((childType = in.readUnsignedByte()) != NBT_TAG_END) {
                    in.skipNBytes(in.readUnsignedShort());
                    skipNbtPayload(in, childType);
                }
            }
            case 11 -> in.skipNBytes(in.readInt() * 4L);
            case 12 -> in.skipNBytes(in.readInt() * 8L);
            default -> throw new IOException("알 수 없는 NBT 태그: " + type);
        }
    }

    private int movePlayerFiles(UUID oldUuid, UUID newUuid) {
        int moved = 0;
        for (World world : Bukkit.getWorlds()) {
            File worldFolder = world.getWorldFolder();
            moved += moveFile(new File(worldFolder, "playerdata"), oldUuid + ".dat", newUuid + ".dat");
            moved += moveFile(new File(worldFolder, "playerdata"), oldUuid + ".dat_old", newUuid + ".dat_old");
            moved += moveFile(new File(worldFolder, "advancements"), oldUuid + ".json", newUuid + ".json");
            moved += moveFile(new File(worldFolder, "stats"), oldUuid + ".json", newUuid + ".json");
        }
        return moved;
    }

    private int moveFile(File dir, String oldName, String newName) {
        File source = new File(dir, oldName);
        if (!source.isFile()) {
            return 0;
        }
        File destination = new File(dir, newName);
        try {
            if (destination.exists()) {
                if (!overwriteExisting) {
                    log.warning(LOG_PREFIX + destination.getPath()
                            + " 파일이 이미 있어 건너뜁니다. (uuid-migration.overwrite-existing: false)");
                    return 0;
                }
                backup(dir, destination);
                log.warning(LOG_PREFIX + destination.getPath()
                        + " 파일이 이미 있어 백업 후 이전 모드 데이터로 교체합니다.");
            }
            backup(dir, source);
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return 1;
        } catch (IOException ex) {
            log.warning(LOG_PREFIX + source.getPath() + " 이동 실패: " + ex.getMessage());
            return 0;
        }
    }

    private void backup(File dir, File source) throws IOException {
        if (backupDir == null) {
            backupDir = plugin.getDataFolder().toPath()
                    .resolve("uuid-migration-backup")
                    .resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        }
        Path target = backupDir.resolve(dir.getParentFile().getName()).resolve(dir.getName()).resolve(source.getName());
        Files.createDirectories(target.getParent());
        Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
    }

    private int migrateWhitelist(boolean targetOnline) {
        int migrated = 0;
        for (OfflinePlayer player : List.copyOf(Bukkit.getWhitelistedPlayers())) {
            UUID oldUuid = player.getUniqueId();
            if (!needsMigration(oldUuid, targetOnline)) {
                continue;
            }
            UUID newUuid = resolveNewUuid(oldUuid, nameOf(player), targetOnline);
            if (newUuid == null || newUuid.equals(oldUuid)) {
                continue;
            }
            Bukkit.getOfflinePlayer(newUuid).setWhitelisted(true);
            player.setWhitelisted(false);
            migrated++;
        }
        return migrated;
    }

    private int migrateOperators(boolean targetOnline) {
        int migrated = 0;
        for (OfflinePlayer player : List.copyOf(Bukkit.getOperators())) {
            UUID oldUuid = player.getUniqueId();
            if (!needsMigration(oldUuid, targetOnline)) {
                continue;
            }
            UUID newUuid = resolveNewUuid(oldUuid, nameOf(player), targetOnline);
            if (newUuid == null || newUuid.equals(oldUuid)) {
                continue;
            }
            Bukkit.getOfflinePlayer(newUuid).setOp(true);
            player.setOp(false);
            migrated++;
        }
        return migrated;
    }

    private int migrateBans(boolean targetOnline) {
        int migrated = 0;
        try {
            ProfileBanList banList = Bukkit.getBanList(BanListType.PROFILE);
            Set<BanEntry<PlayerProfile>> entries = banList.getEntries();
            for (BanEntry<PlayerProfile> entry : List.copyOf(entries)) {
                PlayerProfile profile = entry.getBanTarget();
                UUID oldUuid = profile.getId();
                if (oldUuid == null || !needsMigration(oldUuid, targetOnline)) {
                    continue;
                }
                String name = profile.getName() != null ? profile.getName() : resolveName(oldUuid);
                UUID newUuid = resolveNewUuid(oldUuid, name, targetOnline);
                if (newUuid == null || newUuid.equals(oldUuid)) {
                    continue;
                }
                banList.addBan(Bukkit.createProfile(newUuid, name), entry.getReason(), entry.getExpiration(), entry.getSource());
                entry.remove();
                migrated++;
            }
        } catch (Exception ex) {
            log.warning(LOG_PREFIX + "밴 목록 마이그레이션 실패: " + ex.getMessage());
        }
        return migrated;
    }

    /**
     * DiscordSRV의 디스코드-마인크래프트 계정 연동을 새 UUID로 바꾼다.
     * 최신 버전은 accounts.aof("discordId uuid ..." 줄 단위), 구버전은 linkedaccounts.json을 쓴다.
     * plugin.yml의 loadbefore 덕분에 DiscordSRV가 파일을 읽기 전에 실행된다.
     */
    private int migrateDiscordSrvLinks(boolean targetOnline) {
        File dir = new File(plugin.getDataFolder().getParentFile(), "DiscordSRV");
        if (!dir.isDirectory()) {
            return 0;
        }
        return migrateDiscordSrvAof(new File(dir, "accounts.aof"), targetOnline)
                + migrateDiscordSrvJson(new File(dir, "linkedaccounts.json"), targetOnline);
    }

    private int migrateDiscordSrvAof(File file, boolean targetOnline) {
        if (!file.isFile()) {
            return 0;
        }
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            List<String> updated = new ArrayList<>(lines.size());
            int migrated = 0;
            for (String line : lines) {
                String rewritten = rewriteUuidTokens(line, targetOnline);
                if (!rewritten.equals(line)) {
                    migrated++;
                }
                updated.add(rewritten);
            }
            if (migrated == 0) {
                return 0;
            }
            backup(file.getParentFile(), file);
            Files.write(file.toPath(), updated, StandardCharsets.UTF_8);
            return migrated;
        } catch (Exception ex) {
            log.warning(LOG_PREFIX + "DiscordSRV accounts.aof 마이그레이션 실패: " + ex.getMessage());
            return 0;
        }
    }

    private int migrateDiscordSrvJson(File file, boolean targetOnline) {
        if (!file.isFile()) {
            return 0;
        }
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                return 0;
            }
            JsonObject links = root.getAsJsonObject();
            Map<String, UUID> updates = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : links.entrySet()) {
                UUID oldUuid;
                try {
                    oldUuid = UUID.fromString(entry.getValue().getAsString());
                } catch (Exception ignored) {
                    continue;
                }
                if (!needsMigration(oldUuid, targetOnline)) {
                    continue;
                }
                UUID newUuid = resolveNewUuid(oldUuid, resolveName(oldUuid), targetOnline);
                if (newUuid != null && !newUuid.equals(oldUuid)) {
                    updates.put(entry.getKey(), newUuid);
                }
            }
            if (updates.isEmpty()) {
                return 0;
            }
            backup(file.getParentFile(), file);
            updates.forEach((discordId, uuid) -> links.addProperty(discordId, uuid.toString()));
            Files.writeString(file.toPath(), links.toString(), StandardCharsets.UTF_8);
            return updates.size();
        } catch (Exception ex) {
            log.warning(LOG_PREFIX + "DiscordSRV linkedaccounts.json 마이그레이션 실패: " + ex.getMessage());
            return 0;
        }
    }

    /** 한 줄 안의 모든 UUID 토큰 중 마이그레이션 대상인 것을 새 UUID로 치환한다(연동/해제/주석 줄 모두 처리). */
    private String rewriteUuidTokens(String line, boolean targetOnline) {
        Matcher matcher = UUID_TOKEN.matcher(line);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String replacement = matcher.group();
            try {
                UUID oldUuid = UUID.fromString(replacement);
                if (needsMigration(oldUuid, targetOnline)) {
                    UUID newUuid = resolveNewUuid(oldUuid, resolveName(oldUuid), targetOnline);
                    if (newUuid != null) {
                        replacement = newUuid.toString();
                    }
                }
            } catch (IllegalArgumentException ignored) {
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 개인 스폰이 UUID 시드에서 파생되므로, UUID가 바뀌어도 기존 스폰이 유지되도록
     * 옛 UUID 기준 좌표를 새 UUID 키로 고정해 둔다. 마이그레이션은 옛 신원이 우선이므로
     * 새 UUID에 이미 기록된 값이 있어도 덮어쓴다.
     */
    private void preservePersonalSpawn(UUID oldUuid, UUID newUuid) {
        PersonalSpawnStore.SpawnPoint point = spawnStore.get(oldUuid);
        if (point == null) {
            point = PersonalSpawnService.computeSpawnPoint(oldUuid, plugin.settings());
        }
        spawnStore.remove(oldUuid);
        spawnStore.put(newUuid, point);
    }

    private String nameOf(OfflinePlayer player) {
        String name = player.getName();
        return name != null ? name : resolveName(player.getUniqueId());
    }

    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static UUID fromUndashed(String hex) {
        return UUID.fromString(hex.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5"));
    }

    private static String modeName(boolean online) {
        return online ? "온라인 모드(정품 UUID)" : "오프라인 모드 UUID";
    }
}
