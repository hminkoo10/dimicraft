package kr.dimigo.dimicraft;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

import java.util.Map;
import java.util.UUID;

@SuppressWarnings("deprecation")
public final class TpaCommand implements CommandExecutor {
    private final Map<UUID, UUID> tpaRequests;

    public TpaCommand(Map<UUID, UUID> tpaRequests) {
        this.tpaRequests = tpaRequests;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("[디미크래프트] 플레이어만 사용할 수 있습니다.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(error("사용법: /tpa <플레이어>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(error("플레이어를 찾을 수 없거나 오프라인입니다."));
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage(error("자기 자신에게는 요청할 수 없습니다."));
            return true;
        }

        tpaRequests.put(target.getUniqueId(), player.getUniqueId());

        player.sendMessage(success(target.getName() + "님에게 텔레포트 요청을 보냈습니다."));
        target.sendMessage(requestMessage(player));
        return true;
    }

    private TextComponent requestMessage(Player requester) {
        TextComponent message = new TextComponent(prefix());

        TextComponent name = new TextComponent(requester.getName());
        name.setColor(ChatColor.YELLOW);
        name.setBold(true);
        message.addExtra(name);

        TextComponent text = new TextComponent("님이 텔레포트 요청을 보냈습니다. ");
        text.setColor(ChatColor.WHITE);
        message.addExtra(text);

        message.addExtra(button("[수락하기]", ChatColor.GREEN, "/tpaccept", "클릭하면 요청을 수락합니다."));
        message.addExtra(" ");
        message.addExtra(button("[거절하기]", ChatColor.RED, "/tpdeny", "클릭하면 요청을 거절합니다."));

        TextComponent hint = new TextComponent("  또는 /tpaccept, /tpdeny");
        hint.setColor(ChatColor.GRAY);
        message.addExtra(hint);

        return message;
    }

    private TextComponent button(String text, ChatColor color, String command, String hoverText) {
        TextComponent button = new TextComponent(text);
        button.setColor(color);
        button.setBold(true);
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hoverText)));
        return button;
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
