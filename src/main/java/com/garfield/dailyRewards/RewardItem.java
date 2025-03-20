package com.garfield.dailyRewards;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Random;

public class RewardItem {
    private Material material;
    private int minAmount;
    private int maxAmount;
    private List<EnchantmentData> enchantments;

    public RewardItem(Material material, int minAmount, int maxAmount, List<EnchantmentData> enchantments) {
        this.material = material;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.enchantments = enchantments;
    }

    public ItemStack toItemStack() {
        Random random = new Random();
        int amount = minAmount + random.nextInt(maxAmount - minAmount + 1);
        ItemStack item = new ItemStack(material, amount);

        if (enchantments != null) {
            for (EnchantmentData enchantmentData : enchantments) {
                Enchantment enchantment = Enchantment.getByName(enchantmentData.getEnchantment());
                if (enchantment != null) {
                    item.addUnsafeEnchantment(enchantment, enchantmentData.getLevel());
                }
            }
        }

        return item;
    }
}