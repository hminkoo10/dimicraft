package kr.dimigo.dimicraft;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Locale;

@SuppressWarnings("deprecation")
public final class SurvivalRuleListener implements Listener {
    private final Main plugin;
    private final PersonalSpawnService personalSpawnService;

    public SurvivalRuleListener(Main plugin, PersonalSpawnService personalSpawnService) {
        this.plugin = plugin;
        this.personalSpawnService = personalSpawnService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        DimicraftSettings settings = plugin.settings();
        Player player = event.getPlayer();

        if (settings.hideJoinQuitMessages()) {
            event.setJoinMessage(null);
        }

        player.setCompassTarget(personalSpawnService.getCompassTarget(player.getWorld()));

        if (settings.personalSpawnEnabled() && !player.hasPlayedBefore()) {
            player.teleport(personalSpawnService.getPersonalSpawn(player));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (plugin.settings().hideJoinQuitMessages()) {
            event.setQuitMessage(null);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!plugin.settings().personalSpawnEnabled() || event.isBedSpawn() || event.isAnchorSpawn()) {
            return;
        }

        event.setRespawnLocation(personalSpawnService.getPersonalSpawn(event.getPlayer()));
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        DimicraftSettings settings = plugin.settings();
        String label = commandLabel(event.getMessage());
        if (!settings.coordinateCommandBlockEnabled() || !settings.blockedCoordinateCommands().contains(label)) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "이 서버에서는 좌표 확인 명령어를 사용할 수 없습니다.");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        DimicraftSettings settings = plugin.settings();
        if (!settings.localChatEnabled()) {
            return;
        }

        Player sender = event.getPlayer();
        event.getRecipients().removeIf(recipient -> !canHear(sender, recipient, settings.localChatRadius()));

        long nearbyCount = event.getRecipients().stream()
                .filter(recipient -> !recipient.equals(sender))
                .count();

        if (nearbyCount == 0) {
            event.setCancelled(true);
            sender.sendMessage(ChatColor.GRAY + "근처에 들을 수 있는 사람이 없습니다.");
            return;
        }

        event.setFormat(ChatColor.DARK_GRAY + "[근거리] " + ChatColor.RESET + "%1$s: %2$s");
    }

    private boolean canHear(Player sender, Player recipient, int radius) {
        if (sender.equals(recipient)) {
            return true;
        }

        Location senderLocation = sender.getLocation();
        Location recipientLocation = recipient.getLocation();
        return senderLocation.getWorld().equals(recipientLocation.getWorld())
                && senderLocation.distanceSquared(recipientLocation) <= (long) radius * radius;
    }

    private String commandLabel(String message) {
        String command = message.substring(1).trim().toLowerCase(Locale.ROOT);
        int spaceIndex = command.indexOf(' ');
        String label = spaceIndex == -1 ? command : command.substring(0, spaceIndex);
        int namespaceIndex = label.indexOf(':');
        return namespaceIndex == -1 ? label : label.substring(namespaceIndex + 1);
    }
}
