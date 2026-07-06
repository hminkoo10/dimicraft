package kr.dimigo.dimicraft;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * 접속할 때마다 좌표 오프셋이 새로 뽑히도록, 플레이 패킷이 오가기 전인
 * 프리로그인 시점에 저장된 오프셋을 폐기한다.
 */
public final class CoordinateOffsetSessionListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        CoordinateOffsetBridge.rotatePlayer(event.getUniqueId());
    }
}
