package cjs.DE_plugin.dragon_egg.egg_portal_prevention;

import cjs.DE_plugin.DE_plugin;
import cjs.DE_plugin.settings.SettingsManager;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerPortalEvent;

public class EggPortalPreventionListener implements Listener {

    private final SettingsManager sm;

    public EggPortalPreventionListener(DE_plugin plugin) {
        this.sm = plugin.getSettingsManager();
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        // 설정이 비활성화되어 있으면 아무것도 하지 않음
        if (!sm.getBoolean(SettingsManager.PREVENT_PORTAL_WITH_EGG)) {
            return;
        }

        Player player = event.getPlayer();

        if (hasDragonEgg(player)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("§c드래곤 알을 소지한 상태에서는 차원을 이동할 수 없습니다."));
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        // 설정이 비활성화되어 있으면 아무것도 하지 않음
        if (!sm.getBoolean(SettingsManager.PREVENT_PORTAL_WITH_EGG)) {
            return;
        }

        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();

        // 플레이어가 포탈 블록 내부에 있는지 확인 (발 또는 눈 위치)
        Block blockAtFeet = player.getLocation().getBlock();
        Block blockAtEyes = player.getEyeLocation().getBlock();
        boolean isInPortal = (blockAtFeet.getType() == Material.NETHER_PORTAL || blockAtFeet.getType() == Material.END_PORTAL) ||
                             (blockAtEyes.getType() == Material.NETHER_PORTAL || blockAtEyes.getType() == Material.END_PORTAL);

        if (isInPortal && hasDragonEgg(player)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("§c드래곤 알을 소지한 채 포탈 내부에서는 인벤토리를 열 수 없습니다."));
        }
    }

    private boolean hasDragonEgg(Player player) {
        // 인벤토리, 왼손, 마우스 커서에 드래곤 알이 있는지 모두 확인
        return player.getInventory().contains(Material.DRAGON_EGG) ||
               player.getInventory().getItemInOffHand().getType() == Material.DRAGON_EGG ||
               player.getItemOnCursor().getType() == Material.DRAGON_EGG;
    }
}