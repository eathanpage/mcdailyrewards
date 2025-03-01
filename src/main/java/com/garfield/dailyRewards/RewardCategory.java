package com.garfield.dailyRewards;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Random;

public class RewardCategory {
    private String name;
    private List<RewardItem> rewards;
    private double chance;

    public RewardCategory(String name, List<RewardItem> rewards, double chance) {
        this.name = name;
        this.rewards = rewards;
        this.chance = chance;
    }

    public String getName() {
        return name;
    }

    public List<RewardItem> getRewards() {
        return rewards;
    }

    public double getChance() {
        return chance;
    }

    public RewardItem getRandomReward() {
        if (rewards.isEmpty()) {
            return null;
        }

        Random random = new Random();
        return rewards.get(random.nextInt(rewards.size()));
    }
}