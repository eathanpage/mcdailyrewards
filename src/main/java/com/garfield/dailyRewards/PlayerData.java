package com.garfield.dailyRewards;

import java.time.LocalDate;

public class PlayerData {
    private int streak;
    private LocalDate lastRewardDate;

    public PlayerData(int streak, LocalDate lastRewardDate) {
        this.streak = streak;
        this.lastRewardDate = lastRewardDate;
    }

    public int getStreak() {
        return streak;
    }

    public void setStreak(int streak) {
        this.streak = streak;
    }

    public LocalDate getLastRewardDate() {
        return lastRewardDate;
    }

    public void setLastRewardDate(LocalDate lastRewardDate) {
        this.lastRewardDate = lastRewardDate;
    }
}