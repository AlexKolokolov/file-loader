package org.kolokolov.fileloader.service;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.concurrent.Future;

import org.apache.commons.io.FileUtils;

public class DownloadService {
    
    private int bufferSize = 1024;
    private int speedLimit;
    
    private ThreadService threadService;
    private TockenBuket tockenBuket;
    
    public DownloadService(int speedLimit) {
        threadService = new ThreadService();
        this.tockenBuket = new TockenBuket();
        this.speedLimit = speedLimit;
        this.tockenBuket.fillBucket(this.speedLimit);
    }
    
    public DownloadService(ThreadService threadService, int speedLimit) {
        this.threadService = threadService;
        this.tockenBuket = new TockenBuket();
        this.speedLimit = speedLimit;
        this.tockenBuket.fillBucket(this.speedLimit);
    }
    
    public Future<Boolean> downloadFileInNewThread(URL url, File file) {
        return threadService.performInNewThread(() -> downloadFile(url, file));
    }
  
    public boolean downloadFile(URL url, File file) {
        synchronized (this) {
            System.out.printf("File %s downloading started%n", file.getName());
        }
        try (InputStream is = url.openStream();
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))){
            if (!file.exists()) file.createNewFile();
            long startTime = System.currentTimeMillis();
            copyBytsIfAllowed(is, bos);
            long downloadTime = System.currentTimeMillis() - startTime;
            long byteSize = FileUtils.sizeOf(file);
            long downlodSpeed = 8 * byteSize * 1000 / downloadTime / 1024;
            String fileSize = FileUtils.byteCountToDisplaySize(byteSize);
            synchronized (this) {
                System.out.printf("File %s : %s has been downloaded at %d kbit/s%n", file.getName(), fileSize, downlodSpeed);
            }
            return true;
        } catch (IOException | InterruptedException e) {
            synchronized (this) {
                System.out.printf("File %s downloading error%n", file.getName());
                System.out.printf("Error message: %s%n", e.getMessage());
            }
            return false;
        }
    }
    
    public void copyBytsIfAllowed(InputStream source, OutputStream target) throws IOException, InterruptedException {
        int count;
        byte[] buffer = new byte[bufferSize];
        while ((count = source.read(buffer)) != -1) {
            synchronized (tockenBuket) {
                while (tockenBuket.bucket < count) {
                    tockenBuket.wait();
                }
                tockenBuket.bucket -= count;
                tockenBuket.notifyAll();
            }
            target.write(buffer, 0, count);
        }
        target.flush();
    }
    
    private class TockenBuket {
        private int bucket;
        private int delay = 5; //ms
        
        public void fillBucket(int speed) {
            int stepSize = 1024 * speed * delay / 1000 / 8;
            int limit = bufferSize > stepSize ? bufferSize * 2 : stepSize * 2;
            bucket = limit;

            System.out.printf("Speed limit = %d kbit/s%n", speed);
            System.out.printf("Delay = %d ms%n", delay);
            System.out.printf("Step size = %d B%n", stepSize);
            Thread bucketFiller = new Thread(() -> {
                while (true) {
                    try {
                        synchronized (this) {
                            while (bucket >= limit) {
                                wait();
                            }
                            bucket += stepSize;
                            Thread.sleep(delay);
                            notifyAll();

                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
            bucketFiller.setDaemon(true);
            bucketFiller.start();
        }
    }
}
