package com.acclash.magishacomputerdivision.utils;

import com.acclash.magishacomputerdivision.MagishaComputerDivision;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

public class Compress {

    static BukkitTask progress;

    public static void compressFileTo7Zip(File file, File compressedFile) throws IOException {
        try (SevenZOutputFile sevenZOutput = new SevenZOutputFile(compressedFile)) {
            SevenZArchiveEntry entry = sevenZOutput.createArchiveEntry(file, file.getName());
            sevenZOutput.putArchiveEntry(entry);

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    sevenZOutput.write(buffer, 0, len);
                }
            }

            sevenZOutput.closeArchiveEntry();
        }
    }

    public static void decompress7ZipFile(File compressedFile, File outputFile) throws IOException {
        try (SevenZFile sevenZFile = new SevenZFile(compressedFile)) {
            while (sevenZFile.getNextEntry() != null) {
                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = sevenZFile.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
            }
        }
    }

    public static void farts() throws IOException {
        File image = new File("Win95.img");
        File compressedFile = new File("Win95.7z");

        // Compress the file
        //compressFileTo7Zip(inputFile, compressedFile);

        // Decompress the file
        URL website = new URL("https://www.dropbox.com/s/910ir3ta262uqd4/Win95.7z?dl=1");
        Bukkit.getScheduler().runTaskAsynchronously(MagishaComputerDivision.getPlugin(), () -> {
            try {
                FileUtils.copyURLToFile(website, compressedFile);
            } catch (Exception e) {
                MagishaComputerDivision.getPlugin().getLogger().severe("No Internet: Unable to download Windows 95 image file");
            }
        });
        progress = Bukkit.getScheduler().runTaskTimer(MagishaComputerDivision.getPlugin(), () -> {
            int total = ProgressBar.getFileSize(website);
            double percent = (double) compressedFile.length() / (double) total;
            ProgressBar.updateProgress(percent);
            if (percent == 1) {
                progress.cancel();
                MagishaComputerDivision.getPlugin().getLogger().info(ChatColor.GREEN + "Downloaded the WWindows 95 image archive. Attempting to decompress it");
                try {
                    decompress7ZipFile(compressedFile, image);
                } catch (IOException e) {
                    MagishaComputerDivision.getPlugin().getLogger().severe("Unable to download Windows 95 image file. Please report this to the developer!");
                }
            }
        }, 0, 1);
    }
}
