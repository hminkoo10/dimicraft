package kr.dimigo.dimicraft;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;

@SuppressWarnings("deprecation")
public final class DimicraftCommand implements TabExecutor {
    private final Main plugin;

    public DimicraftCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadDimicraft();
            sender.sendMessage(ChatColor.GREEN + "[디미크래프트] 설정을 다시 불러왔습니다.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("migrate")) {
            if (!plugin.getServer().getOnlinePlayers().isEmpty()) {
                sender.sendMessage(ChatColor.RED + "[디미크래프트] 접속 중인 플레이어가 없어야 마이그레이션할 수 있습니다.");
                return true;
            }
            plugin.runUuidMigration();
            sender.sendMessage(ChatColor.GREEN + "[디미크래프트] UUID 마이그레이션을 실행했습니다. 결과는 콘솔 로그를 확인하세요.");
            sender.sendMessage(ChatColor.YELLOW + "[디미크래프트] DiscordSRV 연동 변경은 서버 재시작 후에 적용됩니다.");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "사용법: /dimicraft <reload|migrate>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "migrate").stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        return List.of();
    }
}
