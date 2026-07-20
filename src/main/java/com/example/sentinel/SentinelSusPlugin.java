package com.example.sentinel;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.server.BroadcastMessageEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SentinelSusPlugin extends JavaPlugin implements CommandExecutor, Listener {

    private String guiTitle;
    private int guiSize;
    
    private final Map<UUID, Integer> violationCount = new HashMap<>();
    private final Map<UUID, String> lastFlagReason = new HashMap<>();
    
    private Pattern alertPattern;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        
        this.guiTitle = color(getConfig().getString("gui-title", "&4&lFlagged Suspects"));
        this.guiSize = getConfig().getInt("gui-size", 27);

        buildRegexPattern();

        this.getCommand("sus").setExecutor(this);
        this.getCommand("susflag").setExecutor(this);
        this.getServer().getPluginManager().registerEvents(this, this);
    }

    private void buildRegexPattern() {
        String format = getConfig().getString("anti-cheat-alert-format", "Sentinel AC > %player% triggered %reason%");
        
        // Escape regex special characters, then turn placeholders into named capture groups
        String regex = Pattern.quote(format)
                .replace("%player%", "\\E(?<player>[a-zA-Z0-9_]{3,16})\\Q")
                .replace("%reason%", "\\E(?<reason>.+)\\Q");
        
        this.alertPattern = Pattern.compile(regex);
    }

    // Intercepts server broadcast alerts
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBroadcast(BroadcastMessageEvent event) {
        checkAndParseMessage(event.getMessage());
    }

    private void checkAndParseMessage(String rawMessage) {
        if (rawMessage == null || alertPattern == null) return;

        // Strip color codes completely to ensure exact text matching
        String cleanMessage = ChatColor.stripColor(color(rawMessage));
        Matcher matcher = alertPattern.matcher(cleanMessage);
        
        if (matcher.find()) {
            try {
                String playerName = matcher.group("player");
                String hackReason = matcher.group("reason");
                
                Player target = Bukkit.getPlayer(playerName);
                if (target != null) {
                    UUID uuid = target.getUniqueId();
                    violationCount.put(uuid, violationCount.getOrDefault(uuid, 0) + 1);
                    lastFlagReason.put(uuid, hackReason);
                }
            } catch (IllegalArgumentException e) {
                // Catch missing capture groups
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("sus")) {
            if (!(sender instanceof Player staff)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }

            if (!staff.hasPermission("sentinel.sus")) {
                staff.sendMessage(color(getConfig().getString("messages.no-permission")));
                return true;
            }

            Inventory gui = Bukkit.createInventory(null, guiSize, guiTitle);
            int slot = 0;

            List<String> configuredLore = getConfig().getStringList("lore");

            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (slot >= guiSize) break;

                int violations = violationCount.getOrDefault(onlinePlayer.getUniqueId(), 0);
                if (violations == 0) continue; 

                String reason = lastFlagReason.getOrDefault(onlinePlayer.getUniqueId(), "None");

                ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) playerHead.getItemMeta();

                if (meta != null) {
                    meta.setOwningPlayer(onlinePlayer);
                    meta.setDisplayName(ChatColor.RED + onlinePlayer.getName());
                    
                    List<String> dynamicLore = new ArrayList<>();
                    for (String line : configuredLore) {
                        line = line.replace("%ping%", String.valueOf(onlinePlayer.getPing()))
                                   .replace("%health%", String.valueOf((int) onlinePlayer.getHealth()))
                                   .replace("%reason%", reason)
                                   .replace("%violations%", String.valueOf(violations));
                        dynamicLore.add(color(line));
                    }
                    
                    meta.setLore(dynamicLore);
                    playerHead.setItemMeta(meta);
                }

                gui.setItem(slot, playerHead);
                slot++;
            }

            staff.openInventory(gui);
            return true;
        }

        if (command.getName().equalsIgnoreCase("susflag")) {
            if (!sender.hasPermission("sentinel.sus.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /susflag <player> <reason>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }

            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                reasonBuilder.append(args[i]).append(" ");
            }
            String reason = reasonBuilder.toString().trim();

            UUID uuid = target.getUniqueId();
            violationCount.put(uuid, violationCount.getOrDefault(uuid, 0) + 1);
            lastFlagReason.put(uuid, reason);

            sender.sendMessage(ChatColor.GREEN + "Manually flagged " + target.getName());
            return true;
        }

        return false;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(guiTitle)) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() != Material.PLAYER_HEAD) return;
        if (!(event.getWhoClicked() instanceof Player staff)) return;

        SkullMeta meta = (SkullMeta) event.getCurrentItem().getItemMeta();
        if (meta != null && meta.getOwningPlayer() != null) {
            Player target = Bukkit.getPlayer(meta.getOwningPlayer().getUniqueId());

            if (target != null && target.isOnline()) {
                staff.closeInventory();
                staff.teleport(target);
                staff.sendMessage(color(getConfig().getString("messages.teleport-success").replace("%target%", target.getName())));
            } else {
                staff.sendMessage(color(getConfig().getString("messages.player-offline")));
                staff.closeInventory();
            }
        }
    }

    private String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }
}
