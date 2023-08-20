package jdos.cpu.core_dynamic;

import jdos.Dosbox;
import jdos.hardware.mame.RasterizerCompiler;
import jdos.util.Log;
import org.apache.logging.log4j.Level;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Loader {
    private static class SaveItem {
        public SaveItem(String name, byte[] byteCode, int start, byte[] opCode, String source) {
            this.name = name;
            this.byteCode = byteCode;
            this.opCode = opCode;
            this.source = source;
            this.start = start;
        }
        final String source;
        final String name;
        final byte[] byteCode;
        final byte[] opCode;
        final int start;
    }
    private static class Item {
        String name;
        byte[] opCodes;
        int start;
    }

    private static final Vector savedItems = new Vector();

    private static final Hashtable items = new Hashtable();
    private static boolean initialized = false;

    public static boolean isLoaded() {
        if (!initialized)
            init();
        return !items.isEmpty();
    }
    private static void init() {
        initialized = true;
        InputStream is = Dosbox.class.getResourceAsStream("Cache.index");
        if (is != null) {
            DataInputStream dis = new DataInputStream(is);
            try {
                int count = dis.readInt();
                for (int i=0;i<count;i++) {
                    Item item = new Item();
                    item.name = dis.readUTF();
                    item.start = dis.readInt();
                    int len = dis.readInt();
                    item.opCodes = new byte[len];
                    dis.readFully(item.opCodes);
                    Integer key = item.start;
                    Vector bucket = (Vector)items.get(key);
                    if (bucket == null) {
                        bucket = new Vector();
                        items.put(key, bucket);
                    }
                    bucket.addElement(item);
                }
                Log.getLogger().info("Loaded " + count + " blocks");
            } catch (Exception e) {
                Log.getLogger().log(Level.ERROR, "Could not read cache index: ", e);
            }
            try {dis.close();} catch (Exception e) {
                Log.getLogger().log(Level.ERROR, "Runtime error: ", e);
            }
        }
    }
    public static Op load(int start, byte[] opCodes) {
        Integer key = start;
        Vector bucket = (Vector)items.get(key);
        if (bucket != null) {
            for (int i=0;i<bucket.size();i++) {
                Item item = (Item)bucket.elementAt(i);
                if (item.start==start && Arrays.equals(item.opCodes, opCodes)) {
                    try {
                        return (Op)Class.forName(item.name).newInstance();
                    } catch (Exception e) {
                        Log.getLogger().log(Level.ERROR, "Could not load bucket: ", e);
                    }
                }
            }
        }
        return null;
    }
    public static void add(String className, byte[] byteCode, int start, byte[] opCode, String source) {
        savedItems.add(new SaveItem(className, byteCode, start, opCode, source));
    }
    public static void save(String fileName, boolean source) {
        source = true;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            dos.writeInt(savedItems.size());
            ByteArrayOutputStream src_bos = null;
            DataOutputStream src_dos = null;
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(Paths.get(fileName + ".jar"))));
            String root = fileName+"_src"+File.separator+ "jdos";
            String dirName = root+File.separator+"cpu"+File.separator+"core_dynamic";
            if (source) {
                src_bos = new ByteArrayOutputStream();
                src_dos = new DataOutputStream(src_bos);
                src_dos.writeInt(savedItems.size());
                File dir = new File(dirName);
                if (!dir.exists())
                    dir.mkdirs();
                File[] existing = dir.listFiles();
                for (File file : existing) {
                    file.delete();
                }
            }
            for (int i=0;i<savedItems.size();i++) {
                SaveItem item = (SaveItem)savedItems.elementAt(i);
                out.putNextEntry(new ZipEntry(item.name + ".class"));
                out.write(item.byteCode);
                dos.writeUTF(item.name);
                dos.writeInt(item.start);
                dos.writeInt(item.opCode.length);
                dos.write(item.opCode);
                if (source) {
                    FileOutputStream fos = new FileOutputStream(dirName+File.separator+item.name.substring(item.name.lastIndexOf('.')+1)+".java");
                    fos.write(item.source.getBytes());
                    fos.close();
                    src_dos.writeUTF("jdos.cpu.core_dynamic." + item.name);
                    src_dos.writeInt(item.start);
                    src_dos.writeInt(item.opCode.length);
                    src_dos.write(item.opCode);
                }
            }
            out.putNextEntry(new ZipEntry("jdos/Cache.index"));
            dos.flush();
            out.write(bos.toByteArray());
            RasterizerCompiler.save(out);
            out.flush();
            out.close();
            if (source) {
                src_dos.flush();
                FileOutputStream fos = new FileOutputStream(root+File.separator+"Cache.index");
                fos.write(src_bos.toByteArray());
                fos.close();
            }
            Log.getLogger().info("Saved "+savedItems.size()+" blocks");
        } catch (Exception e) {
            Log.getLogger().log(Level.ERROR, "Could not save cache index: ", e);
        }
    }
}