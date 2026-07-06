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
public final class TpAcceptCommand implements CommandExecutor {
    private final Map<UUID, UUID> tpaRequests;

    public TpAcceptCommand(Map<UUID, UUID> tpaRequests) {
        this.tpaRequests = tpaRequests;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[디미크래프트] 플레이어만 사용할 수 있습니다.");
            return true;
        }

        if (args.length != 0) {
            player.sendMessage(error("사용법: /tpaccept"));
            return true;
        }

        UUID requesterId = tpaRequests.remove(player.getUniqueId());
        if (requesterId == null) {
            player.sendMessage(error("받은 텔레포트 요청이 없습니다."));
            return true;
        }

        Player requester = Bukkit.getPlayer(requesterId);
        if (requester == null) {
            player.sendMessage(error("요청을 보낸 플레이어가 오프라인입니다."));
            return true;
        }

        requester.teleport(player.getLocation());
        requester.sendMessage(success(player.getName() + "님에게 텔레포트했습니다."));
        player.sendMessage(success(requester.getName() + "님의 텔레포트 요청을 수락했습니다."));
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
