package com.randude14.backup;

import java.io.File;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class BukkitBackup extends JavaPlugin {
	private static BukkitBackup instance;
	private BackupCommandExecutor backup;
	
	/*
	 * (non-Javadoc)
	 * @see org.bukkit.plugin.java.JavaPlugin#onEnable()
	 */
	@Override
	public void onEnable() {	
		
		/*
		 * set the instance
		 */
		instance = this;
		
		/*
		 * save default config if it doesn't exist
		 */
		if(!(new File("plugins/BukkitBackup/config.yml").exists())) {
			this.saveDefaultConfig();
			this.reloadConfig();
		}
		
		/*
		 * set the command executor
		 */
		backup = new BackupCommandExecutor();
		this.getCommand("backup").setExecutor(backup);
		
		/*
		 * log that the plugin is enabled
		 */
		BukkitBackup.log(Level.INFO, "enabled!");
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.bukkit.plugin.java.JavaPlugin#onDisable()
	 */
	@Override
	public void onDisable() {
		/*
		 * log that the plugin is disabled
		 */
		BukkitBackup.log(Level.INFO, "disabled!");
		
		/*
		 * finally set the instance to null 
		 */
		instance = null;
	}
	
	/*
	 * @return the current working instance
	 */
	public static BukkitBackup getInstance() {
		return instance;
	}

	/*
	 * log messages
	 * @param Level level - the level of the message
	 * @param String message - the message to log
	 */
	public static void log(Level level, String message) {
		Bukkit.getLogger().log(level, "[BukkitBackup v" + instance.getDescription().getVersion() + "] - " + message);
	}
	
}
