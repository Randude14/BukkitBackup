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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;

public class BackupCommandExecutor implements TabExecutor {
	private File backupFolder;
	
	public BackupCommandExecutor() {
		reload();
	}
	
	/*
	 * reload the config
	 */
	public void reload() {
		BukkitBackup.getInstance().reloadConfig();
		String stringPath = BukkitBackup.getInstance().getConfig().getString("backup-folder");
		
		/*
		 * attempts to set the backup folder and if it exists, will set the backup folder
		 */
		if(stringPath != null) {
			Path path = Paths.get(stringPath);
			if(Files.exists(path)) {
				backupFolder = path.toFile().getAbsoluteFile();
				BukkitBackup.log(Level.INFO, backupFolder + " found. Using it as backup folder.");
				return;
			}
		} 
		
		/*
		 * if still here the backup folder has not been set or doesn't exist and needs to be set to the default 'backups' folder
		 */
		backupFolder = new File("backups");
		backupFolder.mkdirs();
		BukkitBackup.log(Level.INFO, "Backup folder not found. Creating a default backup folder instead.");
		System.out.println(stringPath);
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
			sender.sendMessage(ChatColor.RED + "You cannot this command!");
			return true;
		}
		
		/*
		 *  if they have not provided arguments, send help commands
		 */
		if(args.length == 0 || args[0].equalsIgnoreCase("help")) {
			sender.sendMessage(ChatColor.AQUA + "/backup help - list commands");
			sender.sendMessage(ChatColor.AQUA + "/backup reload - reload config");
			sender.sendMessage(ChatColor.AQUA + "/backup list - list current loaded worlds");
			sender.sendMessage(ChatColor.AQUA + "/backup <world name> <name of file (optional)> - can have spaces in name");
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
		
		/*
		 * set the world name, separate arguments are assumed to be spaces
		 */
		String w = "";
		for(int i = 0;i < args.length;i++) {
			w += args[i];
			if(i < args.length-1){
				w += " ";
			}
		}
		
		/*
		 * find the world and if it doesn't exist, send an error message to the user it was not found
		 */
		World world = Bukkit.getServer().getWorld(w);	
		if(world == null) {
			sender.sendMessage(ChatColor.RED + "The world, " + ChatColor.AQUA + w + ChatColor.RED + ", does not exist!");
			return false;
		}
		
		/*
		 * save the world before backuping
		 */
		sender.sendMessage(ChatColor.AQUA + "Saving world before backing up...may cause lag...");
		world.save();
		
		
		/*
		 * since we're not accessing any Bukkit API methods, we can run asynchronously so we don't back up the server
		 */
		final String worldName = w;
		Runnable runnable = () -> {
			String sourceDir = world.getWorldFolder().getAbsoluteFile().getPath();
			sender.sendMessage(ChatColor.AQUA + "Now backing up world...");
			if(onBackup(worldName, sourceDir, backupFolder)) {
				sender.sendMessage(ChatColor.AQUA + "The world " + world.getName() + " has been backed up.");
			} else {
				sender.sendMessage(ChatColor.RED + "An error has occurred. Be sure to check the command line.");
			}
		};
		new Thread(runnable).start();
		
		return true; // finally return true that it was a valid command
	}
	
	public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
		
		/*
		 * if no args then we cannot suggest any world names
		 */
		if(args.length == 0) {
			return null;
		}
		
		/*
		 * if it's a known command return null
		 */
		if(args[0].equalsIgnoreCase("reload") 
				|| args[0].equalsIgnoreCase("list")
				|| args[0].equalsIgnoreCase("help")) {
			return null;
		}
		
		/*
		 * turn the arguments into a world name
		 */
		String w = "";
		for(int i = 0;i < args.length;i++) {
			w += args[i];
			if(i < args.length-1){
				w += " ";
			}
		}
		
		final String worldName = w;
		List<World> worlds = Bukkit.getWorlds().stream() // get stream of worlds loaded currently
				.filter(world -> world.getName().toLowerCase().startsWith(worldName.toLowerCase())) //filter out the worlds that do not match our world name
				.collect(Collectors.toList()); // collect as a list
		
		/*
		 * if no worlds are found return null
		 */
		if(worlds.isEmpty()) {
			return null;
		}
		
		/*
		 * traverse through our list of matched worlds and add the index of their name according to the argument the sender has provided
		 */
		int index = args.length-1; // the index in the argument
		
		List<String> list = new ArrayList<String>(); //the list to return;
		
		worlds.forEach(world -> {
			
			String[] words = world.getName().split("\\s+"); // get array of words like arguments
			
			if(index <= words.length-1) {
				list.add(words[index]); // add the index of world name according to args
			}
		}); 
		
		return list;
	}
	
	/*
	 * attempts to backup the world to a folder
	 * @return whether or not the save was successful
	 */
	private static boolean onBackup(String worldName, String sourceDir, File folderToSaveIn) {
		
		/*
		 * set the time stamp of the zip file
		 */
		LocalDateTime dateTime = LocalDateTime.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
		
		File sourceFile = new File(sourceDir); // source file
		File toFile = new File(folderToSaveIn, worldName + "-" + formatter.format(dateTime) + ".zip").getAbsoluteFile(); // the zip file we are backing up to
		
		try(FileOutputStream out = new FileOutputStream(toFile);
				ZipOutputStream zipStream = new ZipOutputStream(out)) {
			
			/*
			 * add the directory so it recursively traverses through all its subfolders and subfiles
			 */
			addDirectory(zipStream, sourceDir.length() - sourceFile.getName().length(), sourceFile);
			
		} catch (IOException ex) {
			ex.printStackTrace();
			return false;
		}
		return true;
	}
	
	/*
	 * @param File file - the file to the the relative path from
	 * @param int offset - the offset from the original source
	 * @return the relative path
	 */
	private static String getRelativePath(File file, int offset) {
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
	private static void addDirectory(ZipOutputStream zout, int offset, File fileSource) throws IOException {
		
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
