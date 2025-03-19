package com.garfield.dailyRewards;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
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

public class Main extends JavaPlugin implements Listener, CommandExecutor {

    private File streaksFile; // File for saving streaks and last reward dates
    private File rewardsFile; // File for reward configuration
    private Map<String, RewardCategory> rewardCategories; // List of reward categories (common, uncommon, rare, legendary)
    private Gson gson;

    @Override
    public void onEnable() {
        // Ensure the data folder exists
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Initialize files
        streaksFile = new File(getDataFolder(), "streaks.json");
        rewardsFile = new File(getDataFolder(), "rewards.json");

        // Initialize rewardCategories
        rewardCategories = new HashMap<>();

        // Configure Gson with a custom LocalDate adapter
        gson = new GsonBuilder()
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                .create();

        // Register events and command
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("daily").setExecutor(this); // Register the /daily command

        // Load rewards configuration
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
            if (sender instanceof Player) {
                Player player = (Player) sender;
                checkDailyReward(player);
            } else {
                sender.sendMessage("§cOnly players can use this command.");
            }
            return true;
        }
        return false;
    }

    private String getTimeUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(); // Next midnight
        Duration duration = Duration.between(now, midnight);

        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        return String.format("%02d hours, %02d minutes, and %02d seconds", hours, minutes, seconds);
    }

    private void checkDailyReward(Player player) {
        UUID uuid = player.getUniqueId();
        Map<UUID, PlayerData> streaksData = loadStreaks();

        LocalDate currentDate = LocalDate.now();
        PlayerData playerData = streaksData.getOrDefault(uuid, new PlayerData(0, null));
        LocalDate lastRewardDate = playerData.getLastRewardDate();

        if (lastRewardDate != null) {
            long daysSinceLastReward = ChronoUnit.DAYS.between(lastRewardDate, currentDate);

            // Check if more than 1 day has passed since the last reward
            if (daysSinceLastReward >= 1) {
                // If more than 2 days have passed, reset the streak
                if (daysSinceLastReward >= 2) {
                    playerData.setStreak(0);
                    player.sendMessage("§cYour login streak has been reset because you didn't log in for a day.");
                }

                // Attempt to give the reward
                if (giveDailyReward(player, playerData)) {
                    // Only update the last reward date if the reward was successfully given
                    playerData.setLastRewardDate(currentDate);
                    streaksData.put(uuid, playerData);
                    saveStreaks(streaksData);
                }
            } else {
                // Calculate time until midnight and send the countdown message
                String timeUntilMidnight = getTimeUntilMidnight();
                player.sendMessage("§eYou have already claimed your daily reward today. Come back in " + timeUntilMidnight + "!");
            }
        } else {
            // First-time reward
            if (giveDailyReward(player, playerData)) {
                // Only update the last reward date if the reward was successfully given
                playerData.setLastRewardDate(currentDate);
                streaksData.put(uuid, playerData);
                saveStreaks(streaksData);
            }
        }
    }

    private void playRaritySound(Player player, String rarity) {
        switch (rarity.toLowerCase()) {
            case "common":
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
                break;
            case "uncommon":
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                break;
            case "rare":
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                break;
            case "legendary":
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                break;
            default:
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                break;
        }
    }

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

        if (rewardResult == null) {
            player.sendMessage("§cAn error occurred while generating your reward. Please contact an administrator.");
            return false;
        }

        // Give the reward to the player
        player.getInventory().addItem(rewardResult.getItemStack());
        playRaritySound(player, rewardResult.getRarity());
        String rarityColor = getRarityColor(rewardResult.getRarity());
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

    private RewardResult getRandomReward() {
        Random random = new Random();
        double totalWeight = rewardCategories.values().stream()
                .mapToDouble(RewardCategory::getChance)
                .sum();

        double randomValue = random.nextDouble() * totalWeight;
        double cumulativeWeight = 0;

        for (RewardCategory category : rewardCategories.values()) {
            cumulativeWeight += category.getChance();

            if (randomValue <= cumulativeWeight) {
                RewardItem rewardItem = category.getRandomReward();
                if (rewardItem != null) {
                    return new RewardResult(rewardItem.toItemStack(), category.getName());
                }
            }
        }

        // Fallback reward if something goes wrong
        return new RewardResult(new ItemStack(Material.IRON_INGOT, 1), "common");
    }

    private RewardResult getRewardFromCategory(RewardCategory category) {
        RewardItem rewardItem = category.getRandomReward();
        if (rewardItem != null) {
            return new RewardResult(rewardItem.toItemStack(), category.getName());
        }
        // Fallback reward if something goes wrong
        return new RewardResult(new ItemStack(Material.IRON_INGOT, 1), "common");
    }

    private String getRarityColor(String rarity) {
        switch (rarity.toLowerCase()) {
            case "common":
                return "§f"; // White
            case "uncommon":
                return "§a"; // Green
            case "rare":
                return "§9"; // Blue
            case "legendary":
                return "§6"; // Gold
            default:
                return "§f"; // Default to white
        }
    }

    private Map<UUID, PlayerData> loadStreaks() {
        if (!streaksFile.exists()) {
            return new HashMap<>();
        }

        try (Reader reader = new FileReader(streaksFile)) {
            Type type = new TypeToken<Map<String, PlayerData>>() {}.getType();
            Map<String, PlayerData> tempData = gson.fromJson(reader, type);

            Map<UUID, PlayerData> streaksData = new HashMap<>();
            for (Map.Entry<String, PlayerData> entry : tempData.entrySet()) {
                UUID uuid = UUID.fromString(entry.getKey()); // Convert string to UUID
                streaksData.put(uuid, entry.getValue());
            }

            return streaksData;
        } catch (IOException e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

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

    private void loadRewards() {
        if (!rewardsFile.exists()) {
            getLogger().severe("rewards.json does not exist! Please create the file with the correct structure.");
            return;
        }

        try (Reader reader = new FileReader(rewardsFile)) {
            // Parse the JSON as a JsonObject
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();

            // Iterate through each category in the JSON object
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                String categoryName = entry.getKey(); // e.g., "common", "uncommon", etc.
                JsonObject categoryObject = entry.getValue().getAsJsonObject();

                // Extract the rewards and chance for this category
                double chance = categoryObject.get("chance").getAsDouble();
                JsonArray rewardsArray = categoryObject.getAsJsonArray("rewards");

                // Parse the reward items for this category
                List<RewardItem> rewards = parseRewardItems(rewardsArray);

                // Create a new RewardCategory and add it to the map
                rewardCategories.put(categoryName, new RewardCategory(categoryName, rewards, chance));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<RewardItem> parseRewardItems(JsonArray jsonArray) {
        List<RewardItem> rewardItems = new ArrayList<>();
        if (jsonArray == null) {
            return rewardItems;
        }

        for (JsonElement element : jsonArray) {
            JsonObject rewardObject = element.getAsJsonObject();
            Material material = Material.valueOf(rewardObject.get("material").getAsString());
            int minAmount = rewardObject.get("minAmount").getAsInt();
            int maxAmount = rewardObject.get("maxAmount").getAsInt();
            List<EnchantmentData> enchantments = parseEnchantments(rewardObject.getAsJsonArray("enchantments"));
            rewardItems.add(new RewardItem(material, minAmount, maxAmount, enchantments));
        }

        return rewardItems;
    }

    private List<EnchantmentData> parseEnchantments(JsonArray jsonArray) {
        List<EnchantmentData> enchantments = new ArrayList<>();
        if (jsonArray == null) {
            return enchantments;
        }

        for (JsonElement element : jsonArray) {
            JsonObject enchantmentObject = element.getAsJsonObject();
            String enchantment = enchantmentObject.get("enchantment").getAsString();
            int level = enchantmentObject.get("level").getAsInt();
            enchantments.add(new EnchantmentData(enchantment, level));
        }

        return enchantments;
    }
}