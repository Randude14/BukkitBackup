package com.randude14.backup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;

public class BackupCommandExecutor implements CommandExecutor, Runnable {
	private final DateTimeFormatter fileFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss");
	private final DateFormat normalFormatter = new SimpleDateFormat("yyyy-MM-dd @ HH:mm:ss");
	private final Comparator<File> comp = (File f1, File f2) -> {
		long diff = f1.lastModified() - f2.lastModified();
		return (diff > 0) ? 1 : -1;
	};
	private final PlayerJoinListener listener;
	private final BukkitBackup plugin;
	private File backupFolder;
	private String backupFileName;
	private String monthlyBackupFileName;
	private List<Integer> backupTimes = new ArrayList<Integer>(); // backup times
	private int sd_hour, sd_minute;                               // shutdown times
	private int backupMax;
	private boolean backing = false;
	private boolean monthlyBackup = false; // keeps track if monthly 
	private int dayOfMonth, min, hour;
	
	
	public BackupCommandExecutor(BukkitBackup plugin) {
		this.plugin = plugin;
		listener = new PlayerJoinListener();
		
		plugin.getServer().getPluginManager().registerEvents(listener, plugin);
		reload();
	}
	
	/*
	 * reload the config
	 */
	public void reload() {
		BukkitBackup.getInstance().reloadConfig();
		ConfigurationSection config = BukkitBackup.getInstance().getConfig();
		String stringPath = config.getString("backup-folder");
		String shutdownString = config.getString("shutdown-time");
		backupFileName = config.getString("backup-file-name");
		monthlyBackupFileName = config.getString("monthly-backup-file-name");
		backupMax = config.getInt("backup-max");
		backupTimes.clear();
		
		/*
		 * attempts to set the backup folder and if it exists, will set the backup folder
		 */
		if(stringPath != null) {
			Path path = Paths.get(stringPath);
			if(Files.exists(path)) {
				backupFolder = path.toFile().getAbsoluteFile();
				BukkitBackup.log(backupFolder + " found. Using it as backup folder.");
			} else {
				backupFolder = new File("backups");
				backupFolder.mkdirs();
				BukkitBackup.log("Backup folder not found. Creating a default backup folder instead.");
			}
		} 
		
		int spaceIndex = shutdownString.indexOf(' ');
		// shutdown set incorrectly
		if(spaceIndex == -1) {
			sd_hour = -1;      // set to -1, nothing to do. Shutdown will never happen
			sd_minute = -1;
			BukkitBackup.log("Shutdown time either not set or set incorrectly. Hour and minute should be separated by a space.");
			
		} else {
			String hour_string = shutdownString.substring(0, spaceIndex);
			String min_string = shutdownString.substring(spaceIndex+1);
			
			try {
				sd_hour = Integer.parseInt(hour_string);
				sd_minute = Integer.parseInt(min_string);
				
				if(sd_hour >= 12) {
					BukkitBackup.log("Shutdown time loaded. Server will shutdown now at " + (sd_hour == 12 ? 12 : sd_hour-12) + ":" + sd_minute + " PM");
				} else {
					BukkitBackup.log("Shutdown time loaded. Server will shutdown now at " + sd_hour + ":" + sd_minute + " AM");
				}
				
			} catch (Exception ex) {
				sd_hour = -1;      // set to -1, nothing to do. Shutdown will never happen
				sd_minute = -1;
				BukkitBackup.log("Failed to load shutdown time. Contains non-number characters.");
			}
		}
		
		if(backupMax < 0) {
			BukkitBackup.log("Backup max must be greater than 0. Defaulting to 1.");
			backupMax = 1;
		}
		
		if(backupFileName.isEmpty()) {
			BukkitBackup.log("Backup file name cannot be empty. Defaulting to 'FullBackup'.");
			backupFileName = "FullBackup";
		}
		
		if(monthlyBackupFileName.isEmpty()) {
			BukkitBackup.log("Monthly backup file name cannot be empty. Defaulting to 'MonthlyBackup'.");
			monthlyBackupFileName = "MonthlyBackup";
		}
		
		for(String timeString : config.getString("backup-times").split("\\s+")) {
			try {
				int time = Integer.parseInt(timeString);
				backupTimes.add(time);
				
				if(time >= 12) {
					BukkitBackup.log("Server will now backup at " + (time == 12 ? 12 : time-12) + ":00 PM");
				} else {
					BukkitBackup.log("Server will now backup at " + time + ":00 AM");
				}
			} catch (Exception ex) {
				BukkitBackup.log(timeString + " is not an integer!");
			}
		}
		
		
		/*
		 * if still here the backup folder has not been set or doesn't exist and needs to be set to the default 'backups' folder
		 */
	}
	
	// called every minute or so
	public void run() {
		// update the current day, hour, and minute
		update_date_fields();
		
		// only backup at scheduled times
		for(int check : backupTimes) {
			if(check == hour) {
				
				// we only care if we are at an hour
				if(min != 0) {
					return;
				}
				
				if(! listener.player_activity()) {
					BukkitBackup.log("Backup scheduled but no player activity since last backup. No need to save.");
					break;
				}
				
				onServerBackup();
			}
		}
		
		// time to shutdown server
		if(sd_hour == hour && sd_minute == min) {
			BukkitBackup.log("Shutting down server according to the config...");
			Bukkit.getServer().shutdown();
		}
		
		
	}
	
	public void update_date_fields() {
		Calendar current = Calendar.getInstance();
		dayOfMonth = current.get(Calendar.DAY_OF_MONTH);
		hour = current.get(Calendar.HOUR_OF_DAY);
		min = current.get(Calendar.MINUTE);
	}
		
	/*
	 * (non-Javadoc)
	 * @see org.bukkit.command.CommandExecutor#onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
	 */
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		
		/*
		 * if the sender is not console and not opped they do not have access to this command
		 */
		if(!(sender instanceof ConsoleCommandSender) && !sender.isOp()) {
			sender.sendMessage(ChatColor.RED + "You cannot use this command!");
			return true;
		}
		
		/*
		 *  if they have not provided arguments, send help commands
		 */
		if(args.length == 0 || args[0].equalsIgnoreCase("help")) {
			sender.sendMessage(ChatColor.AQUA + "/backup help   - list commands");
			sender.sendMessage(ChatColor.AQUA + "/backup reload - reload config");
			sender.sendMessage(ChatColor.AQUA + "/backup list   - list all loaded worlds");
			sender.sendMessage(ChatColor.AQUA + "/backup last   - list last backup date");
			sender.sendMessage(ChatColor.AQUA + "/backup all    - backup all worlds");
			return false;
		}
		
		/*
		 * if 'reload', reload the config
		 */
		if(args[0].equalsIgnoreCase("reload")) {
			this.reload();
			sender.sendMessage(ChatColor.AQUA + "Config reloaded.");
			return true;
		}
		
		/*
		 * list command for listing the worlds
		 */
		if(args[0].equalsIgnoreCase("list")) {
			sender.sendMessage(ChatColor.AQUA + "------------[List]------------");
			final AtomicInteger index = new AtomicInteger(1);
			Bukkit.getWorlds().forEach(world -> sender.sendMessage(ChatColor.AQUA + "" + index.getAndIncrement() + ". " + world.getName()));
			return true;
		}
		
		if(args[0].equalsIgnoreCase("all")) {
			
			if(backing) {
				sender.sendMessage(ChatColor.RED + "Server is already in the middle of backing up.");
				return true;
			}
			
			sender.sendMessage(ChatColor.AQUA + "Backing up worlds. For more information. Check the console.");
			onServerBackup();
			return true;
		}
		
		if(args[0].equalsIgnoreCase("last")) {
			
			File[] files = backupFolder.listFiles((File file, String name) -> name.startsWith(backupFileName));
			Arrays.sort(files, comp);
			
			File lastBackupFile = files[files.length-1];
			Path path = Paths.get(lastBackupFile.getAbsolutePath());
			FileTime fileTime = null;
			try {
				fileTime = Files.getLastModifiedTime(path);
				sender.sendMessage(ChatColor.AQUA + "The last backup occured at " + normalFormatter.format(fileTime.toMillis()));
			} catch (Exception ex) {
				fileTime = null;
			}
		}
		
		return false;
	}

	/*
	 * attempts to backup all worlds to a zip file
	 * @return whether or not the save was successful
	 */
	private void onServerBackup() {
		backing = true;
		BukkitBackup.log(ChatColor.AQUA + "Saving worlds before backing up...");
		for(World world : Bukkit.getWorlds()) {
			world.save();
		}
				
		Runnable backupRunnable = () -> {
			BukkitBackup.log(ChatColor.AQUA + "Now attempting to backup worlds....");
					
			if(backupWorlds(backupFileName, backupMax)) {
				BukkitBackup.log(ChatColor.AQUA + "Worlds are now backed up.");
			} else {
				BukkitBackup.log(ChatColor.RED + "Failed to backup worlds.");
			}
					
			// check for monthly backup
			if(dayOfMonth == 1 && !monthlyBackup) {
				BukkitBackup.log(ChatColor.AQUA + "Today is the 1rst of the month...time to do montly backup....");
						
				if(backupWorlds(monthlyBackupFileName, 1)) {
					BukkitBackup.log(ChatColor.AQUA + "Monthly backup done.");
					monthlyBackup = true;
				} else {
					BukkitBackup.log(ChatColor.RED + "Failed monthly backup.");
				}
			} else if(dayOfMonth != 1) {
				monthlyBackup = false;
			}
			backing = false;
		};
				
		Bukkit.getServer().getScheduler().runTaskAsynchronously(plugin, backupRunnable);
	}
	
	// backupFileName - file to backup
	// max - max number of copies to keep
	private boolean backupWorlds(String backupFileName, int max) {
		/*
		 * set the time stamp of the zip file
		 */
		LocalDateTime dateTime = LocalDateTime.now();
		
		File outputFolder = new File(backupFolder, backupFileName + "-" + fileFormatter.format(dateTime) + ".zip");
		
		try(FileOutputStream out = new FileOutputStream(outputFolder);
				ZipOutputStream zipStream = new ZipOutputStream(out)) {
			
			for(World world : Bukkit.getWorlds()) {
				File sourceFile = world.getWorldFolder().getAbsoluteFile();
				int offset = sourceFile.getAbsolutePath().length() - sourceFile.getName().length();
				
				/*
				 * add the directory so it recursively traverses through all its subfolders and subfiles
				 */
				addDirectory(zipStream, offset, sourceFile);
			}
		} catch (IOException ex) {
			ex.printStackTrace();
			return false;
		}
		
		// delete old saves
		File[] files = backupFolder.listFiles((File file, String name) -> name.startsWith(backupFileName));
		Arrays.sort(files, comp);
		
		for(int i = 0; i < files.length - max; i++) {
			files[i].delete();
		}
		return true;
	}
	
	/*
	 * @param File file - the file to the the relative path from
	 * @param int offset - the offset from the original source
	 * @return the relative path
	 */
	private String getRelativePath(File file, int offset) {
		String path = file.getPath().substring(offset);
	    if (path.startsWith(File.pathSeparator)) {
	        path = path.substring(1);
	    }
	    return path;
	}
	
	/*
	 * @param ZipOutputStream zout - the ZipOutputStream to output to
	 * @param int offset - the offset from the path of the source
	 * @param File fileSource - directory to copy the contents from
	 */
	private void addDirectory(ZipOutputStream zout, int offset, File fileSource) throws IOException {
		
		/*
		 * only add it if it's a directory
		 */
		if(fileSource.isDirectory()) {
			
	        File[] files = fileSource.listFiles(); // list of files and directories contained within the current directory
	        
	        /*
	         * traverse through all the files and folders
	         */
	        for (int i = 0; i < files.length; i++) {
	        	
	        	/*
	        	 * if it's a directory, add it to the current working one
	        	 */
	            if (files[i].isDirectory()) {
	                addDirectory(zout, offset, files[i]);
	                
	            /*
	             * if it's a file, add it's contents the current directory
	             */
	            } else {
	                byte[] buffer = new byte[1024];
	                FileInputStream fin = new FileInputStream(files[i]);
	                zout.putNextEntry(new ZipEntry(getRelativePath(files[i], offset)));
	                int length;
	                while ((length = fin.read(buffer)) > 0) {
	                    zout.write(buffer, 0, length);
	                }
	                zout.closeEntry();
	                fin.close();
	            }	            
	        }			
		}		
	}
}
