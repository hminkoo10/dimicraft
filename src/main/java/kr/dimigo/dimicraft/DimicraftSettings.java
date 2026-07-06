package kr.dimigo.dimicraft;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class DimicraftSettings {
    private static final List<String> DEFAULT_BLOCKED_COORDINATE_COMMANDS = List.of(
            "tp",
            "teleport",
            "tppos",
            "locate",
            "locatebiome",
            "seed",
            "execute",
            "data",
            "gamerule",
            "worldborder",
            "setworldspawn",
            "coords",
            "coordinate",
            "whereami",
            "getpos",
            "position",
            "advancement"
    );

    private final boolean keepInventory;
    private final boolean reducedDebugInfo;
    private final boolean announceAdvancements;
    private final boolean showDeathMessages;
    private final boolean sendCommandFeedback;
    private final boolean logAdminCommands;
    private final boolean commandBlockOutput;
    private final boolean hideJoinQuitMessages;
    private final boolean localChatEnabled;
    private final int localChatRadius;
    private final boolean coordinateCommandBlockEnabled;
    private final Set<String> blockedCoordinateCommands;
    private final boolean coordinateOffsetEnabled;
    private final int coordinateOffsetX;
    private final int coordinateOffsetZ;
    private final String coordinateOffsetSecret;
    private final boolean personalSpawnEnabled;
    private final int personalSpawnRadius;
    private final int personalSpawnMinDistanceFromZero;
    private final int compassTargetX;
    private final int compassTargetZ;
    private final boolean hideDeathDetails;
    private final String deathMessage;
    private final boolean playerHeadDrops;

    private DimicraftSettings(
            boolean keepInventory,
            boolean reducedDebugInfo,
            boolean announceAdvancements,
            boolean showDeathMessages,
            boolean sendCommandFeedback,
            boolean logAdminCommands,
            boolean commandBlockOutput,
            boolean hideJoinQuitMessages,
            boolean localChatEnabled,
            int localChatRadius,
            boolean coordinateCommandBlockEnabled,
            Set<String> blockedCoordinateCommands,
            boolean coordinateOffsetEnabled,
            int coordinateOffsetX,
            int coordinateOffsetZ,
            String coordinateOffsetSecret,
            boolean personalSpawnEnabled,
            int personalSpawnRadius,
            int personalSpawnMinDistanceFromZero,
            int compassTargetX,
            int compassTargetZ,
            boolean hideDeathDetails,
            String deathMessage,
            boolean playerHeadDrops
    ) {
        this.keepInventory = keepInventory;
        this.reducedDebugInfo = reducedDebugInfo;
        this.announceAdvancements = announceAdvancements;
        this.showDeathMessages = showDeathMessages;
        this.sendCommandFeedback = sendCommandFeedback;
        this.logAdminCommands = logAdminCommands;
        this.commandBlockOutput = commandBlockOutput;
        this.hideJoinQuitMessages = hideJoinQuitMessages;
        this.localChatEnabled = localChatEnabled;
        this.localChatRadius = localChatRadius;
        this.coordinateCommandBlockEnabled = coordinateCommandBlockEnabled;
        this.blockedCoordinateCommands = blockedCoordinateCommands;
        this.coordinateOffsetEnabled = coordinateOffsetEnabled;
        this.coordinateOffsetX = coordinateOffsetX;
        this.coordinateOffsetZ = coordinateOffsetZ;
        this.coordinateOffsetSecret = coordinateOffsetSecret;
        this.personalSpawnEnabled = personalSpawnEnabled;
        this.personalSpawnRadius = personalSpawnRadius;
        this.personalSpawnMinDistanceFromZero = personalSpawnMinDistanceFromZero;
        this.compassTargetX = compassTargetX;
        this.compassTargetZ = compassTargetZ;
        this.hideDeathDetails = hideDeathDetails;
        this.deathMessage = deathMessage;
        this.playerHeadDrops = playerHeadDrops;
    }

    public static DimicraftSettings load(FileConfiguration config) {
        int spawnRadius = Math.max(1, config.getInt("personal-spawn.radius", 10_000));
        int spawnMinDistance = Math.max(0, config.getInt("personal-spawn.min-distance-from-zero", 1_000));
        spawnMinDistance = Math.min(spawnMinDistance, spawnRadius);

        List<String> blockedCommands = config.getStringList("coordinate.blocked-commands");
        if (blockedCommands.isEmpty()) {
            blockedCommands = DEFAULT_BLOCKED_COORDINATE_COMMANDS;
        }

        return new DimicraftSettings(
                config.getBoolean("mechanics.keep-inventory", false),
                config.getBoolean("mechanics.reduced-debug-info", true),
                config.getBoolean("mechanics.announce-advancements", false),
                config.getBoolean("mechanics.show-death-messages", false),
                config.getBoolean("mechanics.send-command-feedback", false),
                config.getBoolean("mechanics.log-admin-commands", false),
                config.getBoolean("mechanics.command-block-output", false),
                config.getBoolean("logs.hide-join-quit-messages", true),
                config.getBoolean("local-chat.enabled", true),
                Math.max(1, config.getInt("local-chat.radius", 64)),
                config.getBoolean("coordinate.block-commands", true),
                normalizeCommands(blockedCommands),
                config.getBoolean("coordinate.offset.enabled", true),
                config.getInt("coordinate.offset.x", 50_000),
                config.getInt("coordinate.offset.z", -30_000),
                config.getString("coordinate.offset.secret", ""),
                config.getBoolean("personal-spawn.enabled", true),
                spawnRadius,
                spawnMinDistance,
                config.getInt("compass.target-x", 0),
                config.getInt("compass.target-z", 0),
                config.getBoolean("death.hide-details", true),
                config.getString("death.message", "사람이 죽었다."),
                config.getBoolean("death.player-head-drops", true)
        );
    }

    private static Set<String> normalizeCommands(List<String> commands) {
        return commands.stream()
                .map(command -> command.toLowerCase(Locale.ROOT).trim())
                .filter(command -> !command.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean keepInventory() {
        return keepInventory;
    }

    public boolean reducedDebugInfo() {
        return reducedDebugInfo;
    }

    public boolean announceAdvancements() {
        return announceAdvancements;
    }

    public boolean showDeathMessages() {
        return showDeathMessages;
    }

    public boolean sendCommandFeedback() {
        return sendCommandFeedback;
    }

    public boolean logAdminCommands() {
        return logAdminCommands;
    }

    public boolean commandBlockOutput() {
        return commandBlockOutput;
    }

    public boolean hideJoinQuitMessages() {
        return hideJoinQuitMessages;
    }

    public boolean localChatEnabled() {
        return localChatEnabled;
    }

    public int localChatRadius() {
        return localChatRadius;
    }

    public boolean coordinateCommandBlockEnabled() {
        return coordinateCommandBlockEnabled;
    }

    public Set<String> blockedCoordinateCommands() {
        return blockedCoordinateCommands;
    }

    public boolean coordinateOffsetEnabled() {
        return coordinateOffsetEnabled;
    }

    public int coordinateOffsetX() {
        return coordinateOffsetX;
    }

    public int coordinateOffsetZ() {
        return coordinateOffsetZ;
    }

    public String coordinateOffsetSecret() {
        return coordinateOffsetSecret;
    }

    public boolean personalSpawnEnabled() {
        return personalSpawnEnabled;
    }

    public int personalSpawnRadius() {
        return personalSpawnRadius;
    }

    public int personalSpawnMinDistanceFromZero() {
        return personalSpawnMinDistanceFromZero;
    }

    public int compassTargetX() {
        return compassTargetX;
    }

    public int compassTargetZ() {
        return compassTargetZ;
    }

    public boolean hideDeathDetails() {
        return hideDeathDetails;
    }

    public String deathMessage() {
        return deathMessage;
    }

    public boolean playerHeadDrops() {
        return playerHeadDrops;
    }
}
