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
import java.util.List;

public class SentinelPlugin extends JavaPlugin implements CommandExecutor, Listener {

    private final String GUI_TITLE = ChatColor.DARK_RED + "" + ChatColor.BOLD + "Flagged Suspects";

    @Override
    public void onEnable() {
        // Register both the command and the click listener to this class
        this.getCommand("sus").setExecutor(this);
        this.getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player staff)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        // Create a 27-slot GUI (3 rows)
        Inventory gui = Bukkit.createInventory(null, 27, GUI_TITLE);
        int slot = 0;

        // Loop through all online players and put their heads in the menu
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (slot >= 27) break; // Stop if the GUI fills up

            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) playerHead.getItemMeta();

            if (meta != null) {
                meta.setOwningPlayer(onlinePlayer);
                meta.setDisplayName(ChatColor.RED + onlinePlayer.getName());
                
                // DonutSMP Style stats overview
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Ping: " + ChatColor.GREEN + onlinePlayer.getPing() + "ms");
                lore.add(ChatColor.GRAY + "Health: " + ChatColor.GREEN + (int) onlinePlayer.getHealth() + "/20");
                lore.add("");
                lore.add(ChatColor.YELLOW + "▶ Click to Teleport & Investigate");
                
                meta.setLore(lore);
                playerHead.setItemMeta(meta);
            }

            gui.setItem(slot, playerHead);
            slot++;
        }

        staff.openInventory(gui);
        return true;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Only run if they are clicking inside the Suspects menu
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        // Cancel the event so staff cannot pull heads out into their own inventory
        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() != Material.PLAYER_HEAD) return;
        if (!(event.getWhoClicked() instanceof Player staff)) return;

        SkullMeta meta = (SkullMeta) event.getCurrentItem().getItemMeta();
        if (meta != null && meta.getOwningPlayer() != null) {
            Player target = Bukkit.getPlayer(meta.getOwningPlayer().getUniqueId());

            if (target != null && target.isOnline()) {
                staff.closeInventory();
                staff.teleport(target);
                staff.sendMessage(ChatColor.GREEN + "Teleported to " + ChatColor.GOLD + target.getName() + ChatColor.GREEN + " for inspection.");
            } else {
                staff.sendMessage(ChatColor.RED + "That player is no longer online.");
                staff.closeInventory();
            }
        }
    }
}
