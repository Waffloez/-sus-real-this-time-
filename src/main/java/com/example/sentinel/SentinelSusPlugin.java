package com.example.sentinel;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SentinelSusPlugin extends JavaPlugin implements CommandExecutor, Listener {

    private String guiTitle;
    private int guiSize;
    
    // Storage for tracking flags dynamically in memory
    private final Map<UUID, Integer> violationCount = new HashMap<>();
    private final Map<UUID, String> lastFlagReason = new HashMap<>();

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        
        this.guiTitle = color(getConfig().getString("gui-title", "&4&lFlagged Suspects"));
        this.guiSize = getConfig().getInt("gui-size", 27);

        this.getCommand("sus").setExecutor(this);
        this.getCommand("susflag").setExecutor(this); // Register new flag tracker command
        this.getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Handle the /sus menu command
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

                // Only display players who have actually been flagged at least once
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

        // Handle the /susflag <player> <reason> command
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

            // Rebuild the flag reason from arguments
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                reasonBuilder.append(args[i]).append(" ");
            }
            String reason = reasonBuilder.toString().trim();

            UUID uuid = target.getUniqueId();
            violationCount.put(uuid, violationCount.getOrDefault(uuid, 0) + 1);
            lastFlagReason.put(uuid, reason);

            sender.sendMessage(ChatColor.GREEN + "Flagged " + target.getName() + " for: " + reason);
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
                
                String successMsg = getConfig().getString("messages.teleport-success", "&aTeleported to &6%target% &afor inspection.");
                staff.sendMessage(color(successMsg.replace("%target%", target.getName())));
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
