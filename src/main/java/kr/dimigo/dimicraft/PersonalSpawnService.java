package kr.dimigo.dimicraft;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Random;
import java.util.UUID;

public final class PersonalSpawnService {
    private final Main plugin;

    public PersonalSpawnService(Main plugin) {
        this.plugin = plugin;
    }

    public Location getPersonalSpawn(Player player) {
        DimicraftSettings settings = plugin.settings();
        World world = plugin.getServer().getWorlds().getFirst();
        UUID uuid = player.getUniqueId();
        Random random = new Random(uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits());

        int radius = settings.personalSpawnRadius();
        int minDistance = settings.personalSpawnMinDistanceFromZero();
        int x;
        int z;
        do {
            x = random.nextInt(radius * 2 + 1) - radius;
            z = random.nextInt(radius * 2 + 1) - radius;
        } while ((long) x * x + (long) z * z < (long) minDistance * minDistance);

        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    public Location getCompassTarget(World world) {
        DimicraftSettings settings = plugin.settings();
        int x = settings.compassTargetX();
        int z = settings.compassTargetZ();
        return new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1.0, z + 0.5);
    }
}
