package kr.dimigo.dimicraft;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Main extends JavaPlugin {
    private final Map<UUID, UUID> tpaRequests = new HashMap<>();
    private DimicraftSettings settings;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadDimicraft();

        PersonalSpawnService personalSpawnService = new PersonalSpawnService(this);

        getLogger().info("Dimicraft enabled!");
        getCommand("dimicraft").setExecutor(new DimicraftCommand(this));
        getCommand("tpa").setExecutor(new TpaCommand(tpaRequests));
        getCommand("tpaccept").setExecutor(new TpAcceptCommand(tpaRequests));
        getCommand("tpdeny").setExecutor(new TpDenyCommand(tpaRequests));
        getServer().getPluginManager().registerEvents(new PlayerHeadDropListener(this), this);
        getServer().getPluginManager().registerEvents(new SurvivalRuleListener(this, personalSpawnService), this);
    }

    public void reloadDimicraft() {
        reloadConfig();
        ensureCoordinateOffsetSecret();
        settings = DimicraftSettings.load(getConfig());
        CoordinateOffsetBridge.apply(this);
        ServerRuleManager.apply(this);
    }

    private void ensureCoordinateOffsetSecret() {
        String path = "coordinate.offset.secret";
        String secret = getConfig().getString(path, "");
        if (secret == null || secret.isBlank() || secret.equalsIgnoreCase("auto") || secret.equalsIgnoreCase("change-me")) {
            getConfig().set(path, UUID.randomUUID().toString());
            saveConfig();
        }
    }

    public DimicraftSettings settings() {
        return settings;
    }
}
