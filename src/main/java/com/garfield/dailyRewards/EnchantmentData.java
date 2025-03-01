package com.garfield.dailyRewards;

public class EnchantmentData {
    private String enchantment;
    private int level;

    public EnchantmentData(String enchantment, int level) {
        this.enchantment = enchantment;
        this.level = level;
    }

    public String getEnchantment() {
        return enchantment;
    }

    public int getLevel() {
        return level;
    }
}