package cjs.DE_plugin.enchantment;

import org.bukkit.GameMode;
import cjs.DE_plugin.DE_plugin;
import cjs.DE_plugin.settings.SettingsManager;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EnchantmentLimitListener implements Listener {

    private final DE_plugin plugin;
    private final SettingsManager sm;

    // [추가] 자주 사용하는 인챈트를 상수로 정의하여 중복 조회를 피하고 가독성을 높입니다.
    private static final Enchantment INFINITY = Enchantment.getByKey(NamespacedKey.minecraft("infinity"));
    private static final Enchantment MENDING = Enchantment.getByKey(NamespacedKey.minecraft("mending"));
    private static final Enchantment PROTECTION = Enchantment.getByKey(NamespacedKey.minecraft("protection"));
    private static final Enchantment SHARPNESS = Enchantment.getByKey(NamespacedKey.minecraft("sharpness"));

    public EnchantmentLimitListener(DE_plugin plugin) {
        this.plugin = plugin;
        this.sm = plugin.getSettingsManager();
    }

    @EventHandler
    public void onEnchantItem(EnchantItemEvent event) {
        // 활 무한 비활성화
        if (INFINITY != null && sm.getBoolean(SettingsManager.ENCHANT_BOW_INFINITY_DISABLED) && event.getItem().getType() == Material.BOW) {
            event.getEnchantsToAdd().remove(INFINITY);
        }

        // 갑옷 수선 비활성화
        if (MENDING != null && sm.getBoolean(SettingsManager.ENCHANT_ARMOR_MENDING_DISABLED) && isArmor(event.getItem())) {
            event.getEnchantsToAdd().remove(MENDING);
        }
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack first = inventory.getFirstItem();
        ItemStack second = inventory.getSecondItem();

        if (first == null || second == null) {
            return;
        }

        // --- 1. 금지된 인챈트 조합을 사전에 모두 차단 ---
        // 활 + 무한
        if (sm.getBoolean(SettingsManager.ENCHANT_BOW_INFINITY_DISABLED) && INFINITY != null) {
            boolean firstIsBow = first.getType() == Material.BOW;
            boolean secondIsBow = second.getType() == Material.BOW;
            boolean firstHasInfinity = getEnchantments(first).containsKey(INFINITY);
            boolean secondHasInfinity = getEnchantments(second).containsKey(INFINITY);

            // 활과 무한 인챈트가 조합되는 모든 경우(아이템+책, 아이템+아이템)를 차단
            if ((firstIsBow && secondHasInfinity) || (secondIsBow && firstHasInfinity)) {
                event.setResult(null);
                return;
            }
        }

        // 갑옷 + 수선
        if (sm.getBoolean(SettingsManager.ENCHANT_ARMOR_MENDING_DISABLED) && MENDING != null) {
            boolean firstIsArmor = isArmor(first);
            boolean secondIsArmor = isArmor(second);
            boolean firstHasMending = getEnchantments(first).containsKey(MENDING);
            boolean secondHasMending = getEnchantments(second).containsKey(MENDING);

            // 갑옷과 수선 인챈트가 조합되는 모든 경우를 차단
            if ((firstIsArmor && secondHasMending) || (secondIsArmor && firstHasMending)) {
                event.setResult(null);
                return;
            }
        }

        // --- 2. 커스텀 레벨 인챈트 처리 (모든 조합 경우의 수 대응) ---
        Map<Enchantment, Integer> baseEnchants = getEnchantments(first);
        Map<Enchantment, Integer> sacrificeEnchants = getEnchantments(second);
        Map<Enchantment, Integer> finalEnchants = new HashMap<>(baseEnchants);
        boolean customUpgradeOccurred = false;

        // 2.1. 두 아이템의 인챈트를 바닐라 규칙에 따라 병합하여 최종 인챈트 목록을 계산
        for (Map.Entry<Enchantment, Integer> entry : sacrificeEnchants.entrySet()) {
            Enchantment enchant = entry.getKey();
            int level2 = entry.getValue();

            // 충돌하는 인챈트는 병합하지 않음
            boolean conflicts = false;
            for (Enchantment existingEnchant : finalEnchants.keySet()) {
                if (enchant != existingEnchant && enchant.conflictsWith(existingEnchant)) {
                    conflicts = true;
                    break;
                }
            }
            if (conflicts) continue;

            int level1 = finalEnchants.getOrDefault(enchant, 0);
            int finalLevel = (level1 == level2) ? level1 + 1 : Math.max(level1, level2);
            finalEnchants.put(enchant, finalLevel);
        }

        // 2.2. 계산된 최종 인챈트 중 커스텀 레벨업 조건에 해당하는 것이 있는지 확인
        int protectionMax = sm.getInt(SettingsManager.ENCHANT_PROTECTION_MAX_LEVEL);
        int sharpnessMax = sm.getInt(SettingsManager.ENCHANT_SHARPNESS_MAX_LEVEL);

        for (Map.Entry<Enchantment, Integer> entry : finalEnchants.entrySet()) {
            Enchantment enchant = entry.getKey();
            int level = entry.getValue();
            int vanillaMax = enchant.getMaxLevel();

            if (enchant.equals(PROTECTION) && level > vanillaMax && level <= protectionMax) {
                customUpgradeOccurred = true;
                break;
            }
            if (enchant.equals(SHARPNESS) && level > vanillaMax && level <= sharpnessMax) {
                customUpgradeOccurred = true;
                break;
            }
        }

        // 2.3. 커스텀 레벨업이 발생한 경우, 바닐라 결과를 무시하고 플러그인이 직접 결과 아이템을 생성
        if (customUpgradeOccurred) {
            ItemStack result = first.clone();
            ItemMeta meta = result.getItemMeta();

            clearEnchantments(meta); // 기존 인챈트 모두 제거 후 재계산하여 적용

            for (Map.Entry<Enchantment, Integer> entry : finalEnchants.entrySet()) {
                Enchantment enchant = entry.getKey();
                int level = entry.getValue();

                // 최종 레벨을 각 인챈트의 최대 레벨(커스텀 또는 바닐라)에 맞게 조정
                if (enchant.equals(PROTECTION)) {
                    if (level > protectionMax) level = protectionMax;
                } else if (enchant.equals(SHARPNESS)) {
                    if (level > sharpnessMax) level = sharpnessMax;
                } else {
                    if (level > enchant.getMaxLevel()) level = enchant.getMaxLevel();
                }

                if (level > 0) {
                    applyEnchantment(meta, enchant, level);
                }
            }

            result.setItemMeta(meta);
            event.setResult(result);

            // 크리에이티브 모드가 아닐 때만 커스텀 비용 설정
            if (event.getView().getPlayer() instanceof Player) {
                Player player = (Player) event.getView().getPlayer();
                if (player.getGameMode() != GameMode.CREATIVE) {
                    int cost = sm.getInt(SettingsManager.ENCHANT_OVER_LIMIT_COST);
                    plugin.getServer().getScheduler().runTask(plugin, () -> inventory.setRepairCost(cost));
                }
            }
        }
        // 커스텀 레벨업이 없는 경우, 바닐라 로직을 따름 (금지된 인챈트는 이미 위에서 차단됨)
    }

    /**
     * 아이템 또는 마법이 부여된 책에서 인챈트 목록을 가져옵니다.
     */
    private Map<Enchantment, Integer> getEnchantments(ItemStack item) {
        if (item.getType() == Material.ENCHANTED_BOOK) {
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
            return meta.getStoredEnchants();
        }
        return item.getEnchantments();
    }

    /**
     * 아이템 또는 마법이 부여된 책의 메타데이터에 인챈트를 적용합니다.
     */
    private void applyEnchantment(ItemMeta meta, Enchantment enchant, int level) {
        if (meta instanceof EnchantmentStorageMeta) {
            ((EnchantmentStorageMeta) meta).addStoredEnchant(enchant, level, true);
        } else {
            meta.addEnchant(enchant, level, true);
        }
    }

    /**
     * [신규] 아이템 메타에서 모든 인챈트를 제거합니다.
     */
    private void clearEnchantments(ItemMeta meta) {
        if (meta instanceof EnchantmentStorageMeta) {
            EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) meta;
            // 복사본을 만들어 ConcurrentModificationException 방지
            new HashMap<>(bookMeta.getStoredEnchants()).keySet().forEach(bookMeta::removeStoredEnchant);
        } else {
            new HashMap<>(meta.getEnchants()).keySet().forEach(meta::removeEnchant);
        }
    }

    /**
     * 아이템이 갑옷인지 확인합니다.
     */
    private boolean isArmor(ItemStack item) {
        if (item == null) return false;
        String typeName = item.getType().name();
        return typeName.endsWith("_HELMET") || typeName.endsWith("_CHESTPLATE") || typeName.endsWith("_LEGGINGS") || typeName.endsWith("_BOOTS");
    }

    /**
     * 아이템이 특정 인챈트가 부여된 책인지 확인합니다.
     */
    private boolean isEnchantedBookWith(ItemStack item, Enchantment enchant) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK || enchant == null) return false;
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
        return meta != null && meta.hasStoredEnchant(enchant);
    }
}