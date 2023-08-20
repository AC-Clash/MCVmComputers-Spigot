package jdos.util;

import org.apache.logging.log4j.Level;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class UnZip {
    static final int BUFFER = 2048*32;
    public static void unzip(String fileName, String dir, Progress progress) {
        BufferedOutputStream dest;
        BufferedInputStream is;

        try {
            ZipEntry entry;
            ZipFile zipfile = new ZipFile(fileName);
            Enumeration e = zipfile.entries();
            long totalSize = 0;
            String root = null;
            while(e.hasMoreElements()) {
                entry = (ZipEntry) e.nextElement();
                totalSize+=entry.getSize();
            }
            e = zipfile.entries();
            while(e.hasMoreElements()) {
                entry = (ZipEntry) e.nextElement();
                Log.getLogger().info("Extracting: " +entry);
                progress.status("Extracting: " +entry);
                if (entry.isDirectory()) {
                    if (root == null) {
                        root = dir+"/"+entry.getName();
                    }
                    new File(dir+"/"+entry.getName()).mkdirs();
                    continue;
                }
                is = new BufferedInputStream(zipfile.getInputStream(entry));
                int count;
                byte[] data = new byte[BUFFER];
                File newFile = new File(dir+"/"+entry.getName());
                if (!newFile.getParentFile().exists()) {
                    if (root == null) {
                        root = newFile.getParentFile().getAbsolutePath();
                    }
                    newFile.getParentFile().mkdirs();
                }
                FileOutputStream fos = new FileOutputStream(newFile);
                dest = new BufferedOutputStream(fos, BUFFER);
                progress.initializeSpeedValue(totalSize);
                while ((count = is.read(data, 0, BUFFER)) != -1) {
                    dest.write(data, 0, count);
                    progress.incrementSpeedValue(count);
                    if (progress.hasCancelled()) {
                        Log.getLogger().info("Cancelled Unzip operation");
                        dest.flush();
                        dest.close();
                        is.close();
                        FileHelper.deleteFile(new File(root));
                        return;
                    }
                }
                dest.flush();
                dest.close();
                is.close();
            }
            zipfile.close();
        } catch(Exception e) {
            Log.getLogger().log(Level.ERROR, "zip file wasn't closed ", e);
        }
    }
}

