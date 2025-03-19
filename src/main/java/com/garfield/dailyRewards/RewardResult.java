package com.garfield.dailyRewards;

import org.bukkit.inventory.ItemStack;

public class RewardResult {
    private ItemStack itemStack;
    private String rarity;
    private String rarityColor;
    private String raritySound;

    public RewardResult(ItemStack itemStack, String rarity, String rarityColor, String raritySound) {
        this.itemStack = itemStack;
        this.rarity = rarity;
        this.rarityColor = rarityColor;
        this.raritySound = raritySound;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public String getRarity() {
        return rarity;
    }

    public String getRarityColor() {
        return rarityColor;
    }

    public String getRaritySound() {
        return raritySound;
    }
}