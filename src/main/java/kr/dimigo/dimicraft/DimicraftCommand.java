package kr.dimigo.dimicraft;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

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

        if (args.length >= 1 && args[0].equalsIgnoreCase("coords")) {
            return sendRealCoordinates(sender, args);
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("recover-spawns")) {
            return recoverPersonalSpawns(sender, args);
        }

        sender.sendMessage(ChatColor.RED + "사용법: /dimicraft <reload|migrate|coords|recover-spawns>");
        return true;
    }

    private boolean sendRealCoordinates(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "사용법: /dimicraft coords <플레이어>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "[디미크래프트] 접속 중인 플레이어를 찾을 수 없습니다: " + args[1]);
            return true;
        }

        Location location = target.getLocation();
        sender.sendMessage(ChatColor.GOLD + "[디미크래프트] " + ChatColor.YELLOW + target.getName()
                + ChatColor.GOLD + " 실제 좌표");
        sender.sendMessage(ChatColor.GRAY + "월드: " + ChatColor.WHITE + location.getWorld().getName());
        sender.sendMessage(ChatColor.GRAY + "정확: " + ChatColor.WHITE
                + format(location.getX()) + ", " + format(location.getY()) + ", " + format(location.getZ()));
        sender.sendMessage(ChatColor.GRAY + "블록: " + ChatColor.WHITE
                + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ());
        return true;
    }

    private boolean recoverPersonalSpawns(CommandSender sender, String[] args) {
        if (args.length > 2 || (args.length == 2 && !args[1].equalsIgnoreCase("overwrite"))) {
            sender.sendMessage(ChatColor.RED + "사용법: /dimicraft recover-spawns [overwrite]");
            return true;
        }

        boolean overwrite = args.length == 2;
        Set<UUID> playerIds = collectKnownPlayerIds();
        if (playerIds.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "[디미크래프트] 복구할 플레이어 UUID를 찾지 못했습니다.");
            sender.sendMessage(ChatColor.YELLOW + "월드의 players/data 또는 playerdata 폴더가 남아 있는지 확인해 주세요.");
            return true;
        }

        int restored = 0;
        int skipped = 0;
        PersonalSpawnStore store = plugin.personalSpawnStore();
        for (UUID uuid : playerIds) {
            if (!overwrite && store.get(uuid) != null) {
                skipped++;
                continue;
            }

            PersonalSpawnStore.SpawnPoint point = PersonalSpawnService.computeSpawnPoint(uuid, plugin.settings());
            store.put(uuid, point);
            restored++;
        }
        store.save();

        sender.sendMessage(ChatColor.GREEN + "[디미크래프트] 개인 스폰 복구 완료: "
                + restored + "명 복구, " + skipped + "명 기존값 유지, " + playerIds.size() + "명 검사");
        sender.sendMessage(ChatColor.YELLOW + "현재 personal-spawn 설정 기준으로 다시 계산했습니다. 예전 설정과 다르면 좌표도 달라질 수 있습니다.");
        if (!overwrite && skipped > 0) {
            sender.sendMessage(ChatColor.GRAY + "기존값까지 다시 만들려면 /dimicraft recover-spawns overwrite");
        }
        return true;
    }

    private Set<UUID> collectKnownPlayerIds() {
        Set<UUID> playerIds = new LinkedHashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            playerIds.add(player.getUniqueId());
        }

        for (World world : Bukkit.getWorlds()) {
            collectPlayerDataIds(playerIds, new File(world.getWorldFolder(), "players/data"));
            collectPlayerDataIds(playerIds, new File(world.getWorldFolder(), "playerdata"));
        }

        File[] worldFolders = Bukkit.getWorldContainer().listFiles(File::isDirectory);
        if (worldFolders != null) {
            for (File worldFolder : worldFolders) {
                if (!new File(worldFolder, "level.dat").isFile()) {
                    continue;
                }
                collectPlayerDataIds(playerIds, new File(worldFolder, "players/data"));
                collectPlayerDataIds(playerIds, new File(worldFolder, "playerdata"));
            }
        }

        return playerIds;
    }

    private void collectPlayerDataIds(Set<UUID> playerIds, File playerDataDir) {
        File[] files = playerDataDir.listFiles((dir, name) -> name.endsWith(".dat"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            String name = file.getName();
            String uuidText = name.substring(0, name.length() - ".dat".length());
            try {
                playerIds.add(UUID.fromString(uuidText));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "migrate", "coords", "recover-spawns").stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("coords")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("recover-spawns")
                && "overwrite".startsWith(args[1].toLowerCase(Locale.ROOT))) {
            return List.of("overwrite");
        }

        return List.of();
    }
}
