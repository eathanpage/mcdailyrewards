package com.garfield.dailyRewards;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Sound;

import java.io.*;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Main class for the DailyRewards plugin.
 */
public class Main extends JavaPlugin implements Listener, CommandExecutor {

    private File streaksFile;
    private File rewardsFile;
    private Map<String, RewardCategory> rewardCategories;
    private Gson gson;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        streaksFile = new File(getDataFolder(), "streaks.json");
        rewardsFile = new File(getDataFolder(), "rewards.yaml");

        rewardCategories = new HashMap<>();

        gson = new GsonBuilder()
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                .create();

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("daily").setExecutor(this);

        loadRewards();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        checkDailyReward(player);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("daily")) {
            if (sender instanceof Player player) {
                checkDailyReward(player);
            } else {
                sender.sendMessage("§cOnly players can use this command.");
            }
            return true;
        }
        return false;
    }

    /**
     * Gets the time until midnight.
     *
     * @return A string representing the time until midnight.
     */
    private String getTimeUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        Duration duration = Duration.between(now, midnight);

        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        return String.format("%02d hours, %02d minutes, and %02d seconds", hours, minutes, seconds);
    }

    /**
     * Checks and gives the daily reward to the player.
     *
     * @param player The player to check and give the reward to.
     */
    private void checkDailyReward(Player player) {
        UUID uuid = player.getUniqueId();
        Map<UUID, PlayerData> streaksData = loadStreaks();

        LocalDate currentDate = LocalDate.now();
        PlayerData playerData = streaksData.getOrDefault(uuid, new PlayerData(0, null));
        LocalDate lastRewardDate = playerData.getLastRewardDate();

        if (lastRewardDate != null) {
            long daysSinceLastReward = ChronoUnit.DAYS.between(lastRewardDate, currentDate);

            if (daysSinceLastReward >= 1) {
                if (daysSinceLastReward >= 2) {
                    playerData.setStreak(0);
                    player.sendMessage("§cYour login streak has been reset because you didn't log in for a day.");
                }

                if (giveDailyReward(player, playerData)) {
                    playerData.setLastRewardDate(currentDate);
                    streaksData.put(uuid, playerData);
                    saveStreaks(streaksData);
                }
            } else {
                String timeUntilMidnight = getTimeUntilMidnight();
                player.sendMessage("§eYou have already claimed your daily reward today. Come back in " + timeUntilMidnight + "!");
            }
        } else {
            if (giveDailyReward(player, playerData)) {
                playerData.setLastRewardDate(currentDate);
                streaksData.put(uuid, playerData);
                saveStreaks(streaksData);
            }
        }
    }

    /**
     * Plays sound to the player
     *
     * @param player The player to play the sound to.
     * @param raritySound The sound to play.
     */
    private void playSound(Player player, String raritySound) {
        if (raritySound != null && !raritySound.isEmpty()) {
            try {
                Sound sound = Sound.valueOf(raritySound);
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid sound: " + raritySound);
            }
        } else {
            getLogger().warning("Sound is null or empty.");
        }
    }

    /**
     * Gives the daily reward to the player.
     *
     * @param player The player to give the reward to.
     * @param playerData The player's data.
     * @return True if the reward was successfully given, false otherwise.
     */
    private boolean giveDailyReward(Player player, PlayerData playerData) {
        int streak = playerData.getStreak() + 1;
        playerData.setStreak(streak);
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§cIt looks like your inventory is full. Please free up a space in your inventory and run §e/daily§c again.");
            return false;
        }

        RewardResult rewardResult;
        if (streak % 7 == 0) {
            player.sendMessage("§6§l7-day streak!");
            RewardCategory category = rewardCategories.get("rare");
            int random = new Random().nextInt(100);
            if (random <= 15) {
                category = rewardCategories.get("legendary");
            }
            if (category != null) {
                rewardResult = getRewardFromCategory(category);
            } else {
                rewardResult = getRandomReward();
            }
        } else {
            rewardResult = getRandomReward();
        }

        player.getInventory().addItem(rewardResult.getItemStack());
        playSound(player, rewardResult.getRaritySound());
        String rarityColor = rewardResult.getRarityColor();
        getLogger().info(String.format("%s received %dx %s, which is a %s item! They are on a streak of %d.",
                player.getName(),
                rewardResult.getItemStack().getAmount(),
                rewardResult.getItemStack().getType().toString().toLowerCase().replace("_", " "),
                rewardResult.getRarity().toUpperCase(),
                streak
        ));
        player.sendMessage(String.format(
                "§aYou received §e%d§ax %s, which is a %s§l%s§a item! You are on a streak of §e§l%s§a, keep it up!",
                rewardResult.getItemStack().getAmount(),
                rewardResult.getItemStack().getType().toString().toLowerCase().replace("_", " "),
                rarityColor,
                rewardResult.getRarity().toUpperCase(),
                streak
        ));

        return true;
    }

    /**
     * Gets a random reward from the reward categories.
     *
     * @return A RewardResult object containing the reward details.
     */
    private RewardResult getRandomReward() {
        Random random = new Random();
        double totalWeight = rewardCategories.values().stream()
                .mapToDouble(RewardCategory::getChance)
                .sum();

        double randomValue = random.nextDouble() * totalWeight;
        double cumulativeWeight = 0;

        for (RewardCategory category : rewardCategories.values()) {
            cumulativeWeight += category.getChance();

            // getLogger().info("Random value: " + randomValue);
            // getLogger().info("Cumulative weight: " + cumulativeWeight);

            if (randomValue <= cumulativeWeight) {
                RewardItem rewardItem = category.getRandomReward();
                if (rewardItem != null) {
                    return new RewardResult(rewardItem.toItemStack(), category.getName(), category.getRarityColor(), category.getRaritySound());
                }
            }
        }

        return new RewardResult(new ItemStack(Material.IRON_INGOT, 1), "common", "§f", "ENTITY_VILLAGER_YES");
    }

    /**
     * Gets a reward from a specific category.
     *
     * @param category The reward category.
     * @return A RewardResult object containing the reward details.
     */
    private RewardResult getRewardFromCategory(RewardCategory category) {
        RewardItem rewardItem = category.getRandomReward();
        if (rewardItem != null) {
            return new RewardResult(rewardItem.toItemStack(), category.getName(), category.getRarityColor(), category.getRaritySound());
        }
        return new RewardResult(new ItemStack(Material.IRON_INGOT, 1), "common", "§f", "ENTITY_VILLAGER_YES");
    }

    /**
     * Loads the streaks data from the streaks file.
     *
     * @return A map of UUIDs to PlayerData objects.
     */
    private Map<UUID, PlayerData> loadStreaks() {
        if (!streaksFile.exists()) {
            return new HashMap<>();
        }

        try (Reader reader = new FileReader(streaksFile)) {
            Type type = new TypeToken<Map<String, PlayerData>>() {}.getType();
            Map<String, PlayerData> tempData = gson.fromJson(reader, type);

            Map<UUID, PlayerData> streaksData = new HashMap<>();
            for (Map.Entry<String, PlayerData> entry : tempData.entrySet()) {
                UUID uuid = UUID.fromString(entry.getKey());
                streaksData.put(uuid, entry.getValue());
            }

            return streaksData;
        } catch (IOException e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    /**
     * Saves player streaks to streaks.json.
     *
     * @param streaksData A map of UUIDs to PlayerData objects.
     */
    private void saveStreaks(Map<UUID, PlayerData> streaksData) {
        Map<String, PlayerData> dataMap = new HashMap<>();
        for (Map.Entry<UUID, PlayerData> entry : streaksData.entrySet()) {
            dataMap.put(entry.getKey().toString(), entry.getValue());
        }

        try (Writer writer = new FileWriter(streaksFile)) {
            gson.toJson(dataMap, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads rewards from rewards.yml
     */
    private void loadRewards() {
        if (!rewardsFile.exists()) {
            getLogger().severe("rewards.yaml does not exist! Please create the file with the correct structure.");
            return;
        }

        try (Reader reader = new FileReader(rewardsFile)) {
            Yaml yaml = new Yaml(new Constructor(new LoaderOptions()));
            Map<String, Map<String, Object>> yamlData = yaml.load(reader);
            int totalRewardCount = 0;
            for (Map.Entry<String, Map<String, Object>> entry : yamlData.entrySet()) {
                String categoryName = entry.getKey();
                Map<String, Object> categoryData = entry.getValue();

                double chance = ((Number) categoryData.get("chance")).doubleValue();
                String color = (String) categoryData.get("color");
                String sound = (String) categoryData.get("sound");
                List<Map<String, Object>> rewardsList = (List<Map<String, Object>>) categoryData.get("rewards");
                List<RewardItem> rewards = parseRewardItems(rewardsList);
                rewardCategories.put(categoryName, new RewardCategory(categoryName, rewards, chance, color, sound));
                totalRewardCount += rewards.size();
            }
            getLogger().info(totalRewardCount + " items loaded from rewards.yml!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Parses the reward items from the rewards list.
     *
     * @param rewardsList The list of rewards.
     * @return A list of RewardItem objects.
     */
    private List<RewardItem> parseRewardItems(List<Map<String, Object>> rewardsList) {
        List<RewardItem> rewardItems = new ArrayList<>();
        if (rewardsList == null) {
            return rewardItems;
        }

        for (Map<String, Object> rewardData : rewardsList) {
            Material material = Material.valueOf((String) rewardData.get("material"));
            int minAmount = ((Number) rewardData.get("minAmount")).intValue();
            int maxAmount = ((Number) rewardData.get("maxAmount")).intValue();
            List<Map<String, Object>> enchantmentsList = (List<Map<String, Object>>) rewardData.get("enchantments");
            List<EnchantmentData> enchantments = parseEnchantments(enchantmentsList);
            rewardItems.add(new RewardItem(material, minAmount, maxAmount, enchantments));
        }

        return rewardItems;
    }

    /**
     * Parses the enchantments from the enchantments list.
     *
     * @param enchantmentsList The list of enchantments.
     * @return A list of EnchantmentData objects.
     */
    private List<EnchantmentData> parseEnchantments(List<Map<String, Object>> enchantmentsList) {
        List<EnchantmentData> enchantments = new ArrayList<>();
        if (enchantmentsList == null) {
            return enchantments;
        }

        for (Map<String, Object> enchantmentData : enchantmentsList) {
            String enchantment = (String) enchantmentData.get("enchantment");
            int level = ((Number) enchantmentData.get("level")).intValue();
            enchantments.add(new EnchantmentData(enchantment, level));
        }

        return enchantments;
    }
}