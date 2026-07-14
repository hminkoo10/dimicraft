package kr.dimigo.dimicraft;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class Main extends JavaPlugin {
    private DimicraftSettings settings;
    private PersonalSpawnStore personalSpawnStore;
    private PersonalSpawnService personalSpawnService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadDimicraft();

        personalSpawnStore = new PersonalSpawnStore(this);
        runUuidMigration();

        personalSpawnService = new PersonalSpawnService(this, personalSpawnStore);

        getLogger().info("Dimicraft enabled!");
        getCommand("dimicraft").setExecutor(new DimicraftCommand(this));
        getServer().getPluginManager().registerEvents(new PlayerHeadDropListener(this), this);
        getServer().getPluginManager().registerEvents(new SurvivalRuleListener(this, personalSpawnService), this);
        getServer().getPluginManager().registerEvents(new CoordinateOffsetSessionListener(), this);
    }

    public void reloadDimicraft() {
        reloadConfig();
        ensureCoordinateOffsetSecret();
        settings = DimicraftSettings.load(getConfig());
        CoordinateOffsetBridge.apply(this);
        ServerRuleManager.apply(this);
        if (personalSpawnService != null) {
            getServer().getOnlinePlayers().forEach(personalSpawnService::updateCompass);
        }
    }

    private void ensureCoordinateOffsetSecret() {
        String path = "coordinate.offset.secret";
        String secret = getConfig().getString(path, "");
        if (secret == null || secret.isBlank() || secret.equalsIgnoreCase("auto") || secret.equalsIgnoreCase("change-me")) {
            getConfig().set(path, UUID.randomUUID().toString());
            saveConfig();
        }
    }

    public void runUuidMigration() {
        new UuidMigrationService(this, personalSpawnStore).migrateIfNeeded();
    }

    public DimicraftSettings settings() {
        return settings;
    }

    public PersonalSpawnStore personalSpawnStore() {
        return personalSpawnStore;
    }
}
