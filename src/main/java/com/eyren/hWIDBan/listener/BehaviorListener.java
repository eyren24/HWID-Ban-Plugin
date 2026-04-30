package com.eyren.hWIDBan.listener;

import com.eyren.hWIDBan.Main;
import com.eyren.hWIDBan.collector.BehaviorCollector.BehaviorSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class BehaviorListener implements Listener {

    private final Main plugin;

    public BehaviorListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("behavior.track-cps", true)) return;
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        BehaviorSession s = plugin.getBehaviorCollector().get(event.getPlayer());
        if (s != null) s.incrementClicks();
        plugin.getNetworkCollector().recordPacket(event.getPlayer());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getConfig().getBoolean("behavior.track-movement", true)) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()) return;
        BehaviorSession s = plugin.getBehaviorCollector().get(event.getPlayer());
        if (s != null) s.incrementMovements();
        plugin.getNetworkCollector().recordPacket(event.getPlayer());
    }

    @EventHandler
    public void onInventory(InventoryClickEvent event) {
        if (!plugin.getConfig().getBoolean("behavior.track-inventory", true)) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        BehaviorSession s = plugin.getBehaviorCollector().get((Player) event.getWhoClicked());
        if (s != null) s.incrementInventory();
    }
}
