package com.randude14.backup;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinListener implements Listener {
	boolean player_activity = false;   // keep track of player activity
	int logged_in = 0;                 // keep track of players logged in
	
	public PlayerJoinListener() {
	}
	
	/*
	 * Set player activity and increment the amount of players logged in
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void PlayerJoined(PlayerJoinEvent event) {
		player_activity = true;
		logged_in++;
	}
	
	/*
	 * Decrement the players logged in
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void PlayerLeft(PlayerQuitEvent event) {
		logged_in--;
	}
	
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void PlayerKicked(PlayerKickEvent event) {
		logged_in--;
	}
	
	/*
	 * @returns whether a player has logged on since the last update or there is current a player logged in
	 */
	public boolean player_activity() {
		boolean result = false;
		if(player_activity) {
			result = true;
			// set player activity based on players logged in
			// if it is 0, set to false and will flag on when players log in
			player_activity = (logged_in > 0);
		}
		return result;
	}
}
