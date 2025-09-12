package cjs.DE_plugin.enchantment;

import cjs.DE_plugin.DE_plugin;
import cjs.DE_plugin.settings.SettingsManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class EnchantmentLimitListener implements Listener {

    private final DE_plugin plugin;
    private final SettingsManager sm;
    private final NamespacedKey COLOR_KEY;
    private final NamespacedKey UUID_KEY;

    // [추가] 자주 사용하는 인챈트를 상수로 정의하여 중복 조회를 피하고 가독성을 높입니다.
    private static final Enchantment INFINITY = Enchantment.getByKey(NamespacedKey.minecraft("infinity"));
    private static final Enchantment MENDING = Enchantment.getByKey(NamespacedKey.minecraft("mending"));
    private static final Enchantment PROTECTION = Enchantment.getByKey(NamespacedKey.minecraft("protection"));
    private static final Enchantment SHARPNESS = Enchantment.getByKey(NamespacedKey.minecraft("sharpness"));

    public EnchantmentLimitListener(DE_plugin plugin) {
        this.plugin = plugin;
        this.sm = plugin.getSettingsManager();
        this.COLOR_KEY = new NamespacedKey(plugin, "anvil_name_color");
        this.UUID_KEY = new NamespacedKey(plugin, "anvil_process_uuid");
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

    /**
     * [신규] 모루에 아이템을 놓을 때 이름의 색상 코드를 제거합니다.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onAnvilItemPlace(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.ANVIL) {
            return;
        }

        // 아이템을 모루의 입력 슬롯에 놓는 동작인지 확인
        if (event.getSlotType() == InventoryType.SlotType.CRAFTING && (event.getSlot() == 0 || event.getSlot() == 1)) {
            ItemStack cursorItem = event.getCursor();
            if (cursorItem != null && cursorItem.getType() != Material.AIR) {
                stripColorFromName(cursorItem);
            }
        }
        // Shift + 클릭으로 아이템을 옮기는 경우
        else if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            ItemStack currentItem = event.getCurrentItem();
            if (currentItem != null && currentItem.getType() != Material.AIR) {
                stripColorFromName(currentItem);
            }
        }
    }

    /**
     * [신규] 모루 GUI를 닫을 때, 인벤토리 내 아이템들의 색상을 다시 적용합니다.
     * 모루에 넣었다가 다시 가져온 아이템의 색상을 복원하기 위함입니다.
     */
    @EventHandler
    public void onAnvilClose(InventoryCloseEvent event) {
        if (event.getInventory().getType() != InventoryType.ANVIL) {
            return;
        }

        Player player = (Player) event.getPlayer();
        // GUI가 닫힌 후 인벤토리 상태가 완전히 업데이트되도록 1틱 지연시킵니다.
        new BukkitRunnable() {
            @Override
            public void run() {
                // 커서에 있는 아이템 확인
                reapplyColorToItem(player.getItemOnCursor());

                // 인벤토리 전체 아이템 확인
                for (ItemStack item : player.getInventory().getContents()) {
                    reapplyColorToItem(item);
                }
            }
        }.runTask(plugin);
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

        // [핵심 추가] 초과 인챈트는 특정 아이템 종류에만 적용되도록 확인
        boolean isResultBook = first.getType() == Material.ENCHANTED_BOOK && second.getType() == Material.ENCHANTED_BOOK;
        boolean isResultArmor = isArmor(first) || isArmor(second);
        boolean isResultSword = isSword(first) || isSword(second);

        // [핵심 변경] 검에 날카로움이 붙는 경우 항상 커스텀 로직을 타도록 변경
        if (isResultSword && finalEnchants.containsKey(SHARPNESS)) {
            customUpgradeOccurred = true;
        }

        // 기존 초과 인챈트 확인 로직은 유지 (보호, 또는 책의 날카로움)
        if (!customUpgradeOccurred) {
            for (Map.Entry<Enchantment, Integer> entry : finalEnchants.entrySet()) {
                Enchantment enchant = entry.getKey();
                int level = entry.getValue();
                int vanillaMax = enchant.getMaxLevel();

                // 보호 인챈트는 갑옷 또는 책에만
                if ((isResultArmor || isResultBook) && enchant.equals(PROTECTION) && level > vanillaMax && level <= protectionMax) {
                    customUpgradeOccurred = true;
                    break;
                }
                // 날카로움 인챈트는 (검이 아닌) 책에만
                if (isResultBook && enchant.equals(SHARPNESS) && level > vanillaMax && level <= sharpnessMax) {
                    customUpgradeOccurred = true;
                    break;
                }
            }
        }

        // 2.3. 커스텀 레벨업이 발생한 경우, 바닐라 결과를 무시하고 플러그인이 직접 결과 아이템을 생성
        if (customUpgradeOccurred) {
            ItemStack result;
            // [핵심 수정] 바닐라가 결과물을 생성했다면(수리, 이름 변경 등), 그것을 기반으로 작업합니다.
            if (event.getResult() != null && event.getResult().getType() != Material.AIR) {
                result = event.getResult().clone();
            } else {
                // 바닐라 결과물이 없다면(레벨 초과 등), 직접 결과물을 생성하고 수리 로직을 적용합니다.
                result = first.clone();

                // 두 아이템이 모두 Damageable이고 타입이 같을 때 수리 로직을 적용합니다.
                if (first.getItemMeta() instanceof Damageable && second.getItemMeta() instanceof Damageable && first.getType() == second.getType()) {
                    Damageable firstDamageable = (Damageable) first.getItemMeta();
                    Damageable secondDamageable = (Damageable) second.getItemMeta();
                    short maxDura = result.getType().getMaxDurability();
                    int combinedDurability = (maxDura - firstDamageable.getDamage()) + (maxDura - secondDamageable.getDamage()) + (int) (maxDura * 0.12);

                    Damageable resultDamageable = (Damageable) result.getItemMeta();
                    resultDamageable.setDamage(Math.max(0, maxDura - combinedDurability));
                    result.setItemMeta(resultDamageable);
                }
            }

            ItemMeta meta = result.getItemMeta();

            // [Fix] 기존 로어를 완전히 초기화하여 중복 문제를 방지합니다.
            meta.setLore(new ArrayList<>());

            // --- 1. 아이템 이름 색상 결정 및 PDC에 저장 ---
            String nameColor = "";
            if (isResultSword && finalEnchants.containsKey(SHARPNESS)) {
                int level = finalEnchants.get(SHARPNESS);
                if (level > SHARPNESS.getMaxLevel()) {
                    if (level == 6) nameColor = "§c";
                    else if (level == 7) nameColor = "§4";
                    else nameColor = "§4"; // 8 이상은 짙은 빨강으로 고정
                }
            } else if (isResultArmor && finalEnchants.containsKey(PROTECTION)) {
                int level = finalEnchants.get(PROTECTION);
                if (level > PROTECTION.getMaxLevel()) {
                    if (level <= 6) nameColor = "§c";
                    else nameColor = "§4";
                }
            }

            // 이름 색상 적용이 필요하면, PDC에 정보를 저장합니다.
            if (!nameColor.isEmpty()) {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                pdc.set(COLOR_KEY, PersistentDataType.STRING, nameColor);
                pdc.set(UUID_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
            }

            // --- 1. 속성(Attribute) 수정 및 계산 ---
            double displayedFinalDamage = 0; // 로어에 표시될 최종 데미지
            if (isSword(result)) {
                // 모든 공격력 속성을 초기화
                meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE);

                // 무기의 기본 공격력 가져오기
                double baseDamage = getBaseDamage(result.getType());

                // 날카로움 레벨에 따른 보너스 데미지 계산
                double sharpnessBonusForDisplay = 0;
                double vanillaSharpnessDamage = 0;
                if (finalEnchants.containsKey(SHARPNESS)) {
                    int sharpnessLevel = finalEnchants.get(SHARPNESS);
                    // 요청: 날카로움 모든 레벨당 +1 데미지
                    sharpnessBonusForDisplay = sharpnessLevel;
                    if (sharpnessLevel > 0) {
                        // 바닐라 날카로움 데미지 공식 (보정용)
                        vanillaSharpnessDamage = 0.5 * sharpnessLevel + 0.5;
                    }
                }

                // 로어에 표시될 최종 데미지
                displayedFinalDamage = baseDamage + sharpnessBonusForDisplay;

                // 실제 데미지를 맞추기 위한 속성값 계산
                // (속성 데미지)가 (원하는 총 데미지 - 바닐라 날카로움 데미지)가 되도록 설정
                double attributeDamage = displayedFinalDamage - vanillaSharpnessDamage;

                // 바닐라 기본 공격력 속성 UUID를 사용하여 교체
                final UUID ATTACK_DAMAGE_MODIFIER_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
                AttributeModifier newBaseDamageModifier = new AttributeModifier(
                        ATTACK_DAMAGE_MODIFIER_UUID,
                        "Weapon modifier",
                        attributeDamage - 1.0, // 마인크래프트는 기본 공격력 1에 이 값을 더함
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlot.HAND
                );
                meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, newBaseDamageModifier);
            }


            // --- 2. 인챈트 적용 및 로어(Lore) 수정 ---
            clearEnchantments(meta); // 아이템의 실제 인챈트 목록 초기화
            List<String> newLore = new ArrayList<>();
            boolean useCustomLore = !isResultBook;

            if (useCustomLore) {
                // 커스텀 로어를 사용하므로, 바닐라 인챈트 로어를 숨깁니다.
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS); // 모든 인챈트 숨기기 (커스텀 로어로 대체)
            }

            // 최종 인챈트 목록을 순회하며 실제 인챈트 적용 및 커스텀 로어 생성
            for (Map.Entry<Enchantment, Integer> entry : finalEnchants.entrySet()) {
                Enchantment enchant = entry.getKey();
                int level = entry.getValue();

                // 최종 레벨을 설정된 최대치로 제한
                int clampedLevel = level;
                if (enchant.equals(PROTECTION)) {
                    if (clampedLevel > protectionMax) clampedLevel = protectionMax;
                } else if (enchant.equals(SHARPNESS)) {
                    if (clampedLevel > sharpnessMax) clampedLevel = sharpnessMax;
                } else {
                    if (clampedLevel > enchant.getMaxLevel()) clampedLevel = enchant.getMaxLevel();
                }

                if (clampedLevel > 0) {
                    // 실제 인챈트 적용
                    applyEnchantment(meta, enchant, clampedLevel);

                    if (useCustomLore) {
                        // 책이 아닌 경우에만 커스텀 로어 생성
                        String loreLine = "§7" + getEnchantmentName(enchant) + " " + toRoman(clampedLevel);
                        newLore.add(loreLine);
                    }
                }
            }

            if (useCustomLore) {
                // --- 3. 가짜 속성 로어 추가 ---
                if (isSword(result)) {
                    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                    newLore.add("");
                    newLore.add("§7주로 사용하는 손에 있을 때:");
                    newLore.add("§2 " + (int) displayedFinalDamage + " 공격 피해");
                    newLore.add("§2 1.6 공격 속도");
                }
                meta.setLore(newLore);
            }
            result.setItemMeta(meta);
            event.setResult(result);

            // [핵심 변경] 게임 모드와 관계없이 항상 커스텀 비용을 설정합니다.
            // (크리에이티브 모드에서는 비용이 표시되지만, 실제 경험치는 소모되지 않습니다.)
            if (event.getView().getPlayer() instanceof Player) {
                final int cost = sm.getInt(SettingsManager.ENCHANT_OVER_LIMIT_COST);
                plugin.getServer().getScheduler().runTask(plugin, () -> inventory.setRepairCost(cost)); // 비용 표시는 다음 틱에 적용해야 안정적입니다.
            }
        }
        // 커스텀 레벨업이 없는 경우, 바닐라 로직을 따름 (금지된 인챈트는 이미 위에서 차단됨)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnvilResultClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory) || event.getSlotType() != InventoryType.SlotType.RESULT) {
            return;
        }

        ItemStack resultItem = event.getCurrentItem();
        if (resultItem == null || resultItem.getType() == Material.AIR) {
            return;
        }

        ItemMeta meta = resultItem.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(UUID_KEY, PersistentDataType.STRING)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String uniqueId = pdc.get(UUID_KEY, PersistentDataType.STRING);
        String color = pdc.get(COLOR_KEY, PersistentDataType.STRING);

        if (uniqueId == null || color == null) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                // 플레이어가 아이템을 가져간 후이므로, 커서나 인벤토리에서 해당 아이템을 찾습니다.
                // 먼저 커서를 확인합니다. (일반 클릭)
                ItemStack cursorItem = player.getItemOnCursor();
                if (cursorItem != null && cursorItem.getType() != Material.AIR) {
                    ItemMeta cursorMeta = cursorItem.getItemMeta();
                    if (cursorMeta != null && uniqueId.equals(cursorMeta.getPersistentDataContainer().get(UUID_KEY, PersistentDataType.STRING))) {
                        applyColorAndCleanup(cursorItem, cursorMeta, color);
                        player.setItemOnCursor(cursorItem);
                        return;
                    }
                }

                // 커서에 없다면 인벤토리를 확인합니다. (Shift + 클릭)
                for (int i = 0; i < player.getInventory().getSize(); i++) {
                    ItemStack invItem = player.getInventory().getItem(i);
                    if (invItem == null || invItem.getType() == Material.AIR) {
                        continue;
                    }
                    ItemMeta invMeta = invItem.getItemMeta();
                    if (invMeta != null && uniqueId.equals(invMeta.getPersistentDataContainer().get(UUID_KEY, PersistentDataType.STRING))) {
                        applyColorAndCleanup(invItem, invMeta, color);
                        player.getInventory().setItem(i, invItem);
                        return; // 아이템을 찾아 처리했으므로 종료
                    }
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    /**
     * 아이템에 색상을 적용하고 PDC 태그를 정리하는 헬퍼 메소드.
     */
    private void applyColorAndCleanup(ItemStack item, ItemMeta meta, String color) {
        String nameToColor;
        if (meta.hasDisplayName()) {
            // [핵심 수정] Adventure Component를 레거시 문자열로 변환 후, ChatColor를 사용해 색상 코드를 확실하게 제거합니다.
            String legacyName = LegacyComponentSerializer.legacySection().serialize(meta.displayName());
            nameToColor = ChatColor.stripColor(legacyName);
        } else {
            nameToColor = getItemDefaultName(item.getType());
        }

        meta.displayName(net.kyori.adventure.text.Component.text(color + nameToColor)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

        // 처리 후 PDC 태그 제거
        meta.getPersistentDataContainer().remove(UUID_KEY);
        meta.getPersistentDataContainer().remove(COLOR_KEY);
        item.setItemMeta(meta);
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
     * [신규] 아이템에서 색상 코드를 제거하는 헬퍼 메소드.
     */
    private void stripColorFromName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta.hasDisplayName()) {
            String legacyName = LegacyComponentSerializer.legacySection().serialize(meta.displayName());
            String strippedName = ChatColor.stripColor(legacyName);

            // 색상 코드가 실제로 제거되었다면, 이름 없는 아이템으로 되돌리지 않고 색만 없는 이름으로 설정합니다.
            if (!legacyName.equals(strippedName)) {
                meta.displayName(net.kyori.adventure.text.Component.text(strippedName)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                item.setItemMeta(meta);
            }
        }
    }

    /**
     * [신규] 아이템의 인챈트를 기반으로 색상을 다시 적용하는 헬퍼 메소드.
     */
    private void reapplyColorToItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        // PDC 태그가 있는 아이템은 onAnvilResultClick에서 처리되므로 건너뜁니다.
        if (meta.getPersistentDataContainer().has(UUID_KEY, PersistentDataType.STRING)) {
            return;
        }

        Map<Enchantment, Integer> enchants = getEnchantments(item);
        if (enchants.isEmpty()) {
            return;
        }

        String nameColor = "";
        boolean isSword = isSword(item);
        boolean isArmor = isArmor(item);

        if (isSword && enchants.containsKey(SHARPNESS)) {
            int level = enchants.get(SHARPNESS);
            if (level > SHARPNESS.getMaxLevel()) {
                if (level == 6) nameColor = "§c";
                else if (level == 7) nameColor = "§4";
                else nameColor = "§4"; // 8 이상은 짙은 빨강으로 고정
            }
        } else if (isArmor && enchants.containsKey(PROTECTION)) {
            int level = enchants.get(PROTECTION);
            if (level > PROTECTION.getMaxLevel()) {
                if (level <= 6) nameColor = "§c";
                else nameColor = "§4";
            }
        }

        if (!nameColor.isEmpty()) {
            String currentName;
            String legacyDisplayName = meta.hasDisplayName() ? LegacyComponentSerializer.legacySection().serialize(meta.displayName()) : null;

            if (legacyDisplayName != null && !legacyDisplayName.isEmpty()) {
                // 이미 색상이 적용되어 있다면, 더 이상 처리하지 않음
                if (legacyDisplayName.startsWith("§")) {
                    return;
                }
                currentName = legacyDisplayName;
            } else {
                currentName = getItemDefaultName(item.getType());
            }

            meta.displayName(net.kyori.adventure.text.Component.text(nameColor + currentName)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
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
     * 아이템이 검인지 확인합니다.
     */
    private boolean isSword(ItemStack item) {
        if (item == null) return false;
        String typeName = item.getType().name();
        return typeName.endsWith("_SWORD");
    }

    /**
     * [신규] Material로부터 기본 아이템 이름을 생성합니다. (지역화 미지원)
     * @param material 아이템 Material
     * @return 영어 기반의 아이템 이름
     */
    private String getItemDefaultName(Material material) {
        // [핵심 변경] 주요 아이템의 기본 이름을 한글로 반환합니다.
        return switch (material) {
            // Swords
            case WOODEN_SWORD -> "나무 검";
            case STONE_SWORD -> "돌 검";
            case IRON_SWORD -> "철 검";
            case GOLDEN_SWORD -> "금 검";
            case DIAMOND_SWORD -> "다이아몬드 검";
            case NETHERITE_SWORD -> "네더라이트 검";
            // Helmets
            case LEATHER_HELMET -> "가죽 모자";
            case CHAINMAIL_HELMET -> "사슬 투구";
            case IRON_HELMET -> "철 투구";
            case GOLDEN_HELMET -> "금 투구";
            case DIAMOND_HELMET -> "다이아몬드 투구";
            case NETHERITE_HELMET -> "네더라이트 투구";
            case TURTLE_HELMET -> "거북 등딱지";
            // Chestplates
            case LEATHER_CHESTPLATE -> "가죽 조끼";
            case CHAINMAIL_CHESTPLATE -> "사슬 흉갑";
            case IRON_CHESTPLATE -> "철 흉갑";
            case GOLDEN_CHESTPLATE -> "금 흉갑";
            case DIAMOND_CHESTPLATE -> "다이아몬드 흉갑";
            case NETHERITE_CHESTPLATE -> "네더라이트 흉갑";
            // Leggings
            case LEATHER_LEGGINGS -> "가죽 바지";
            case CHAINMAIL_LEGGINGS -> "사슬 레깅스";
            case IRON_LEGGINGS -> "철 레깅스";
            case GOLDEN_LEGGINGS -> "금 레깅스";
            case DIAMOND_LEGGINGS -> "다이아몬드 레깅스";
            case NETHERITE_LEGGINGS -> "네더라이트 레깅스";
            // Boots
            case LEATHER_BOOTS -> "가죽 장화";
            case CHAINMAIL_BOOTS -> "사슬 부츠";
            case IRON_BOOTS -> "철 부츠";
            case GOLDEN_BOOTS -> "금 부츠";
            case DIAMOND_BOOTS -> "다이아몬드 부츠";
            case NETHERITE_BOOTS -> "네더라이트 부츠";
            // Book
            case ENCHANTED_BOOK -> "마법이 부여된 책";
            default -> {
                // 그 외 아이템은 기존 방식(영어)으로 이름을 생성합니다.
                String[] parts = material.name().toLowerCase().split("_");
                StringBuilder capitalized = new StringBuilder();
                for (String part : parts) {
                    capitalized.append(Character.toUpperCase(part.charAt(0)))
                            .append(part.substring(1))
                            .append(" ");
                }
                yield capitalized.toString().trim();
            }
        };
    }

    /**
     * [신규] 아이템 타입에 따른 기본 공격력을 반환합니다.
     * @param material 아이템 타입
     * @return 기본 공격력
     */
    private double getBaseDamage(Material material) {
        return switch (material) {
            case WOODEN_SWORD, GOLDEN_SWORD -> 4.0;
            case STONE_SWORD -> 5.0;
            case IRON_SWORD -> 6.0;
            case DIAMOND_SWORD -> 7.0;
            case NETHERITE_SWORD -> 8.0;
            default -> 0.0;
        };
    }

    /**
     * [신규] 숫자를 로마 숫자로 변환합니다. (1-15 범위 지원)
     */
    private String toRoman(int number) {
        if (number < 1) return String.valueOf(number);
        final String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV"};
        if (number < numerals.length) {
            return numerals[number];
        }
        return String.valueOf(number);
    }

    /**
     * [신규] 인챈트 객체로부터 한글 이름을 반환합니다.
     */
    private String getEnchantmentName(Enchantment enchant) {
        String key = enchant.getKey().getKey();
        return switch (key) {
            case "protection" -> "보호";
            case "fire_protection" -> "화염으로부터 보호";
            case "feather_falling" -> "가벼운 착지";
            case "blast_protection" -> "폭발로부터 보호";
            case "projectile_protection" -> "발사체로부터 보호";
            case "respiration" -> "호흡";
            case "aqua_affinity" -> "친수성";
            case "thorns" -> "가시";
            case "depth_strider" -> "심해 보행";
            case "frost_walker" -> "차가운 걸음";
            case "binding_curse" -> "귀속 저주";
            case "sharpness" -> "날카로움";
            case "smite" -> "강타";
            case "bane_of_arthropods" -> "살충";
            case "knockback" -> "밀치기";
            case "fire_aspect" -> "발화";
            case "looting" -> "약탈";
            case "sweeping_edge" -> "휘몰아치는 칼날";
            case "efficiency" -> "효율";
            case "silk_touch" -> "섬세한 손길";
            case "unbreaking" -> "내구성";
            case "fortune" -> "행운";
            case "power" -> "힘";
            case "punch" -> "밀어내기";
            case "flame" -> "화염";
            case "infinity" -> "무한";
            case "luck_of_the_sea" -> "바다의 행운";
            case "lure" -> "미끼";
            case "mending" -> "수선";
            case "vanishing_curse" -> "소실 저주";
            default -> {
                String[] parts = key.split("_");
                StringBuilder capitalized = new StringBuilder();
                for (String part : parts) {
                    capitalized.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
                }
                yield capitalized.toString().trim();
            }
        };
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