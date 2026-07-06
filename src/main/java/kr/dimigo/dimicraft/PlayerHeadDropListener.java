package kr.dimigo.dimicraft;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

@SuppressWarnings("deprecation")
public final class PlayerHeadDropListener implements Listener {
    private final Main plugin;

    public PlayerHeadDropListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        DimicraftSettings settings = plugin.settings();
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();

        if (settings.hideDeathDetails()) {
            event.setDeathMessage(null);
            Bukkit.broadcastMessage(ChatColor.RED + settings.deathMessage());
        }

        if (!settings.playerHeadDrops() || killer == null) {
            return;
        }

        event.getDrops().add(createPlayerHead(victim, killer));
    }

    private ItemStack createPlayerHead(Player victim, Player killer) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        meta.setOwningPlayer(victim);
        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + victim.getName() + "님의 머리");
        meta.setLore(List.of(
                ChatColor.GRAY + "처치한 플레이어: " + killer.getName(),
                ChatColor.DARK_AQUA + "디미크래프트 전리품"
        ));
        head.setItemMeta(meta);

        return head;
    }
}
