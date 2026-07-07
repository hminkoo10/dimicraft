package kr.dimigo.dimicraft;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Random;
import java.util.UUID;

public final class PersonalSpawnService {
    private final Main plugin;
    private final PersonalSpawnStore store;
    private final NamespacedKey zeroCompassKey;

    public PersonalSpawnService(Main plugin, PersonalSpawnStore store) {
        this.plugin = plugin;
        this.store = store;
        this.zeroCompassKey = new NamespacedKey(plugin, "zero_compass");
    }

    public Location getPersonalSpawn(Player player) {
        World world = plugin.getServer().getWorlds().getFirst();
        UUID uuid = player.getUniqueId();

        PersonalSpawnStore.SpawnPoint point = store.get(uuid);
        if (point == null) {
            point = computeSpawnPoint(uuid, plugin.settings());
            store.put(uuid, point);
        }

        int y = world.getHighestBlockYAt(point.x(), point.z()) + 1;
        return new Location(world, point.x() + 0.5, y, point.z() + 0.5);
    }

    static PersonalSpawnStore.SpawnPoint computeSpawnPoint(UUID uuid, DimicraftSettings settings) {
        Random random = new Random(uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits());

        int radius = settings.personalSpawnRadius();
        int minDistance = settings.personalSpawnMinDistanceFromZero();
        int x;
        int z;
        do {
            x = random.nextInt(radius * 2 + 1) - radius;
            z = random.nextInt(radius * 2 + 1) - radius;
        } while ((long) x * x + (long) z * z < (long) minDistance * minDistance);

        return new PersonalSpawnStore.SpawnPoint(x, z);
    }

    public Location getCompassTarget(World world) {
        DimicraftSettings settings = plugin.settings();
        int x = settings.compassTargetX();
        int z = settings.compassTargetZ();
        return new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1.0, z + 0.5);
    }

    public void updateCompass(Player player) {
        Location target = getCompassTarget(player.getWorld());
        player.setCompassTarget(target);

        for (ItemStack item : player.getInventory().getContents()) {
            updateCompassItem(item, target);
        }
    }

    private void updateCompassItem(ItemStack item, Location target) {
        if (item == null || item.getType() != Material.COMPASS) {
            return;
        }

        ItemMeta itemMeta = item.getItemMeta();
        if (!(itemMeta instanceof CompassMeta compassMeta)) {
            return;
        }

        if (compassMeta.hasLodestone() && !isDimicraftCompass(compassMeta) && !isLegacyZeroCompass(compassMeta, target)) {
            return;
        }

        compassMeta.setLodestone(target);
        compassMeta.setLodestoneTracked(false);
        compassMeta.setEnchantmentGlintOverride(false);
        compassMeta.getPersistentDataContainer().set(zeroCompassKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(compassMeta);
    }

    private boolean isDimicraftCompass(CompassMeta compassMeta) {
        return compassMeta.getPersistentDataContainer().has(zeroCompassKey, PersistentDataType.BYTE);
    }

    private boolean isLegacyZeroCompass(CompassMeta compassMeta, Location target) {
        Location lodestone = compassMeta.getLodestone();
        return lodestone != null
                && !compassMeta.isLodestoneTracked()
                && target.getWorld().equals(lodestone.getWorld())
                && target.getBlockX() == lodestone.getBlockX()
                && target.getBlockZ() == lodestone.getBlockZ();
    }
}
