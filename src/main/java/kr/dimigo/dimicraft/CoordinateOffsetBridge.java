package kr.dimigo.dimicraft;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

public final class CoordinateOffsetBridge {
    private static final String OFFSET_CLASS = "io.papermc.paper.dimicraft.DimicraftCoordinateOffset";

    private CoordinateOffsetBridge() {
    }

    public static void rotatePlayer(UUID playerId) {
        try {
            Method rotate = loadOffsetClass().getMethod("rotatePlayer", UUID.class);
            rotate.invoke(null, playerId);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    public static void apply(Main plugin) {
        DimicraftSettings settings = plugin.settings();

        try {
            Class<?> offsetClass = loadOffsetClass();
            configureOffset(offsetClass, settings);

            if (settings.coordinateOffsetEnabled()) {
                plugin.getLogger().info("좌표 암호화 적용: 플레이어별 오프셋");
            } else {
                plugin.getLogger().info("좌표 오프셋 비활성화");
            }
        } catch (ClassNotFoundException ignored) {
            if (settings.coordinateOffsetEnabled()) {
                plugin.getLogger().warning("좌표 오프셋이 켜져 있지만 Dimicraft Paper fork가 아닙니다. 일반 Paper에서는 진짜 좌표 은닉이 불가능합니다.");
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            plugin.getLogger().warning("좌표 오프셋 적용 실패: " + ex.getMessage());
        }
    }

    private static Class<?> loadOffsetClass() throws ClassNotFoundException {
        try {
            return Class.forName(OFFSET_CLASS);
        } catch (ClassNotFoundException ex) {
            ClassLoader serverClassLoader = org.bukkit.Bukkit.getServer().getClass().getClassLoader();
            return Class.forName(OFFSET_CLASS, true, serverClassLoader);
        }
    }

    private static void configureOffset(Class<?> offsetClass, DimicraftSettings settings)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        try {
            Method configure = offsetClass.getMethod("configure", boolean.class, int.class, int.class, String.class);
            configure.invoke(
                    null,
                    settings.coordinateOffsetEnabled(),
                    settings.coordinateOffsetX(),
                    settings.coordinateOffsetZ(),
                    settings.coordinateOffsetSecret()
            );
        } catch (NoSuchMethodException ignored) {
            Method configure = offsetClass.getMethod("configure", boolean.class, int.class, int.class);
            configure.invoke(null, settings.coordinateOffsetEnabled(), settings.coordinateOffsetX(), settings.coordinateOffsetZ());
        }
    }
}
