package cjs.DE_plugin.dragon_egg.egg_footprint;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public class FootprintChunkListener implements Listener {

    private final FootprintManager footprintManager;

    public FootprintChunkListener(FootprintManager footprintManager) {
        this.footprintManager = footprintManager;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // 발자국 만료 확인 로직은 GameTimeManager에서 주기적으로 처리하므로,
        // 청크 로드 시 별도의 작업이 필요하지 않습니다.
    }
}