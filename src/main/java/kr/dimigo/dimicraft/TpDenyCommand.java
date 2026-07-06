package kr.dimigo.dimicraft;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.ChatColor;

import java.util.Map;
import java.util.UUID;

@SuppressWarnings("deprecation")
public final class TpDenyCommand implements CommandExecutor {
    private final Map<UUID, UUID> tpaRequests;

    public TpDenyCommand(Map<UUID, UUID> tpaRequests) {
        this.tpaRequests = tpaRequests;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[디미크래프트] 플레이어만 사용할 수 있습니다.");
            return true;
        }

        if (args.length != 0) {
            player.sendMessage(error("사용법: /tpdeny"));
            return true;
        }

        UUID requesterId = tpaRequests.remove(player.getUniqueId());
        if (requesterId == null) {
            player.sendMessage(error("거절할 텔레포트 요청이 없습니다."));
            return true;
        }

        Player requester = Bukkit.getPlayer(requesterId);
        if (requester != null) {
            requester.sendMessage(error(player.getName() + "님이 텔레포트 요청을 거절했습니다."));
        }

        player.sendMessage(success("텔레포트 요청을 거절했습니다."));
        return true;
    }

    private String success(String message) {
        return prefix() + ChatColor.GREEN + message;
    }

    private String error(String message) {
        return prefix() + ChatColor.RED + message;
    }

    private String prefix() {
        return ChatColor.AQUA + "" + ChatColor.BOLD + "[디미크래프트] " + ChatColor.RESET;
    }
}
