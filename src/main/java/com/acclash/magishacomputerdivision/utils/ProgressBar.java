package com.acclash.magishacomputerdivision.utils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

public class ProgressBar {

    public static void updateProgress(double progressPercentage) {
        final int width = 40; // progress bar width in chars

        System.out.print("\r");
        int i = 0;
        for (; i <= (int) (progressPercentage * width); i++) {
            System.out.print("-"); // use
        }
        for (; i < width; i++) {
            System.out.print(" ");
        }
        System.out.println("\033[F");
    }

    public static void main(String[] args) {
        try {
            for (double progressPercentage = 0.0; progressPercentage < 1.0; progressPercentage += 0.01) {
                updateProgress(progressPercentage);
                Thread.sleep(20);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static int getFileSize(URL url) {
        URLConnection conn = null;
        try {
            conn = url.openConnection();
            if (conn instanceof HttpURLConnection) {
                ((HttpURLConnection) conn).setRequestMethod("HEAD");
            }
            conn.getInputStream();
            return conn.getContentLength();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (conn instanceof HttpURLConnection) {
                ((HttpURLConnection) conn).disconnect();
            }
        }
    }

}
