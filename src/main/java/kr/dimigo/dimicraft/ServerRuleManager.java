package kr.dimigo.dimicraft;

import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;

@SuppressWarnings({"deprecation", "removal"})
public final class ServerRuleManager {
    private ServerRuleManager() {
    }

    public static void apply(Main plugin) {
        for (World world : plugin.getServer().getWorlds()) {
            applyWorldRules(world, plugin.settings());
        }
    }

    private static void applyWorldRules(World world, DimicraftSettings settings) {
        world.setGameRule(GameRule.KEEP_INVENTORY, settings.keepInventory());
        world.setGameRule(GameRule.REDUCED_DEBUG_INFO, settings.reducedDebugInfo());
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, settings.announceAdvancements());
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, settings.showDeathMessages());
        world.setGameRule(GameRule.SEND_COMMAND_FEEDBACK, settings.sendCommandFeedback());
        world.setGameRule(GameRule.LOG_ADMIN_COMMANDS, settings.logAdminCommands());
        world.setGameRule(GameRule.COMMAND_BLOCK_OUTPUT, settings.commandBlockOutput());

        Location compassTarget = new Location(
                world,
                settings.compassTargetX() + 0.5,
                world.getHighestBlockYAt(settings.compassTargetX(), settings.compassTargetZ()) + 1.0,
                settings.compassTargetZ() + 0.5
        );
        world.setSpawnLocation(compassTarget);
    }
}
