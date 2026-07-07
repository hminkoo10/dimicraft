package kr.dimigo.dimicraft;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 유저별 개인 스폰 좌표를 UUID와 무관하게 보존하기 위한 저장소.
 * UUID 시드로 매번 계산하면 online-mode 마이그레이션으로 UUID가 바뀔 때 스폰도 바뀌므로,
 * 최초 계산 결과를 파일에 고정하고 마이그레이션 시 키만 새 UUID로 옮긴다.
 */
public final class PersonalSpawnStore {
    public record SpawnPoint(int x, int z) {
    }

    private final Main plugin;
    private final File file;
    private final YamlConfiguration data;

    public PersonalSpawnStore(Main plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "personal-spawns.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public SpawnPoint get(UUID uuid) {
        String path = "spawns." + uuid;
        if (!data.isConfigurationSection(path)) {
            return null;
        }
        return new SpawnPoint(data.getInt(path + ".x"), data.getInt(path + ".z"));
    }

    public void put(UUID uuid, SpawnPoint point) {
        data.set("spawns." + uuid + ".x", point.x());
        data.set("spawns." + uuid + ".z", point.z());
        save();
    }

    public void remove(UUID uuid) {
        if (data.isConfigurationSection("spawns." + uuid)) {
            data.set("spawns." + uuid, null);
            save();
        }
    }

    public void save() {
        try {
            file.getParentFile().mkdirs();
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("personal-spawns.yml 저장 실패: " + ex.getMessage());
        }
    }
}
