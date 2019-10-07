package com.randude14.backup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	private final Comparator<File> comp = (File f1, File f2) -> {
		long diff = f1.lastModified() - f2.lastModified();
		return (diff > 0) ? 1 : -1;
	};
	private final BukkitBackup plugin;
	private File backupFolder;
	private String backupFileName;
	private List<Integer> backupTimes = new ArrayList<Integer>();
	private int backupMax;
	private boolean backing = false;
	
	public BackupCommandExecutor(BukkitBackup plugin) {
		this.plugin = plugin;
		reload();
	}
	
	/*
	 * reload the config
	 */
	public void reload() {
		BukkitBackup.getInstance().reloadConfig();
		ConfigurationSection config = BukkitBackup.getInstance().getConfig();
		String stringPath = config.getString("backup-folder");
		backupFileName = config.getString("backup-file-name");
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
		
		if(backupMax < 0) {
			BukkitBackup.log("Backup max must be greater than 0. Defaulting to 1.");
			backupMax = 1;
		}
		
		if(backupFileName.isEmpty()) {
			BukkitBackup.log("Backup file name cannot be empty. Defaulting to 'FullBackup'.");
			backupFileName = "FullBackup";
		}
		
		for(String timeString : config.getString("backup-times").split("\\s+")) {
			try {
				int time = Integer.parseInt(timeString);
				backupTimes.add(time);
				System.out.println("Time added - " + time);
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
		Calendar current = Calendar.getInstance();
		int time = current.get(Calendar.HOUR_OF_DAY);
		int min = current.get(Calendar.MINUTE);
		
		// we only care if we are at an hour
		if(min != 0) {
			return;
		}
		
		for(int check : backupTimes) {
			if(check == time) {
				onServerBackup();
			}
		}
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
			backupWorlds();
		};
		
		Bukkit.getServer().getScheduler().runTask(plugin, backupRunnable);
	}
	
	private void backupWorlds() {
		/*
		 * set the time stamp of the zip file
		 */
		LocalDateTime dateTime = LocalDateTime.now();
		
		File outputFolder = new File(backupFolder, backupFileName + "-" + formatter.format(dateTime) + ".zip");
		
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
			BukkitBackup.log(ChatColor.AQUA + "Worlds are now backed up.");
		} catch (IOException ex) {
			BukkitBackup.log(ChatColor.RED + "Failed to backup worlds.");
			ex.printStackTrace();
			backing = false;
			return;
		}
		
		// delete old saves
		File[] files = backupFolder.listFiles((File file, String name) -> name.startsWith(backupFileName));
		Arrays.sort(files, comp);
		
		if(files.length > backupMax) {
			for(int i = 0; i < files.length - backupMax; i++) {
				files[i].delete();
			}
		}
		backing = false;
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
