package com.garfield.dailyRewards;
import java.util.List;
import java.util.Random;

public class RewardCategory {
    private String name;
    private List<RewardItem> rewards;
    private double chance;
    private String rarityColor;
    private String raritySound;

    public RewardCategory(String name, List<RewardItem> rewards, double chance, String rarityColor, String raritySound) {
        this.name = name;
        this.rewards = rewards;
        this.chance = chance;
        this.rarityColor = rarityColor;
        this.raritySound = raritySound;
    }

    public String getName() {
        return name;
    }

    public double getChance() {
        return chance;
    }

    public String getRarityColor() {
        return rarityColor;
    }

    public String getRaritySound() {
        return raritySound;
    }

    public RewardItem getRandomReward() {
        if (rewards.isEmpty()) {
            return null;
        }

        Random random = new Random();
        return rewards.get(random.nextInt(rewards.size()));
    }
}