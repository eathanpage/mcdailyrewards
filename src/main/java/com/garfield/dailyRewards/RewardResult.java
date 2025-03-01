package com.garfield.dailyRewards;

import org.bukkit.inventory.ItemStack;

public class RewardResult {
    private final ItemStack itemStack;
    private final String rarity;

    public RewardResult(ItemStack itemStack, String rarity) {
        this.itemStack = itemStack;
        this.rarity = rarity;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public String getRarity() {
        return rarity;
    }
}