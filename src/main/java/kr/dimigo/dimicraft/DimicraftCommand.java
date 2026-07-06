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

        sender.sendMessage(ChatColor.RED + "사용법: /dimicraft reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && "reload".startsWith(args[0].toLowerCase())) {
            return List.of("reload");
        }

        return List.of();
    }
}
