package cjs.DE_plugin.settings.apply;

import cjs.DE_plugin.settings.SettingsManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public class GameplayListener implements Listener {

    private final SettingsManager sm;

    public GameplayListener(SettingsManager settingsManager) {
        this.sm = settingsManager;
    }

    // 플레이어 경험치 드롭 배율
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // keepInventory gamerule이 켜져있으면 경험치를 드롭하지 않으므로, 아무것도 하지 않습니다.
        if (event.getKeepLevel()) {
            return;
        }

        // [핵심 변경] 설정된 배율에 따라 플레이어의 전체 경험치를 드롭합니다.
        double multiplier = sm.getDouble(SettingsManager.PLAYER_EXP_DROP_MULTIPLIER);
        int finalExp = (int) (event.getEntity().getTotalExperience() * multiplier);
        event.setDroppedExp(finalExp);
    }

    // 부활 드래곤 경험치 & 폭발 데미지
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // 부활 드래곤 경험치
        if (event.getEntityType() == EntityType.ENDER_DRAGON) {
            // DragonBattle 객체가 null이 아니고, 엔드 포탈이 이미 존재하는지(두 번째 드래곤 이후인지) 확인
            if (event.getEntity().getWorld().getEnderDragonBattle() != null &&
                    event.getEntity().getWorld().getEnderDragonBattle().getEndPortalLocation() != null) {
                // [핵심 변경] 부활한 드래곤이 죽을 때, 설정된 배율에 따라 경험치를 드롭하도록 설정합니다.
                double multiplier = sm.getDouble(SettingsManager.RESPAWNED_DRAGON_EXP_MULTIPLIER);
                int finalExp = (int) (12000 * multiplier);
                event.setDroppedExp(finalExp);
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        // 폭발 데미지 비율
        if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            double multiplier = sm.getDouble(SettingsManager.EXPLOSION_DAMAGE_MULTIPLIER);
            double newDamage = event.getDamage() * multiplier;
            event.setDamage(newDamage);
        }
    }
}