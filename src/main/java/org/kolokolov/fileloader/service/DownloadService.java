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

/**
 * Service is designed to perform copying data from an URL to a file.
 * It uses ThreadService to provide data copying in different thread
 * and TokenBucket to limit copying speed.
 * @author kolokolov
 */
public class DownloadService {

    private final int BUFFER_SIZE = 1024;

    private ThreadService threadService;
    private TokenBucket tokenBuket;

    public DownloadService(ThreadService threadService, int speedLimit) {
        this.threadService = threadService;
        if (speedLimit > 0) {
            this.tokenBuket = new TokenBucket(speedLimit);
        }
    }

    public Future<Boolean> downloadFileInNewThread(URL url, File file) {
        return threadService.executeInNewThread(() -> downloadFile(url, file));
    }

    public boolean downloadFile(URL url, File file) {
        System.out.printf("File '%s' downloading started%n", file.getName());
        try (InputStream input = url.openStream();
                BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(file))) {
            if (!file.exists()) {
                file.createNewFile();
            }
            long startTime = System.currentTimeMillis();
            
            copyBytesIfAllowed(input, output);
            
            long downloadTime = System.currentTimeMillis() - startTime; //ms
            long fileSize = FileUtils.sizeOf(file); //bytes
            long downlodSpeed = 8 * fileSize * 1000 / downloadTime / 1024; //kbit/s
            String displayFileSize = FileUtils.byteCountToDisplaySize(fileSize); //in human readable format
            System.out.printf("File '%s' : %s has been downloaded at %d kbit/s%n", file.getName(), displayFileSize,
                    downlodSpeed);
            return true;
        } catch (IOException | InterruptedException e) {
            System.out.printf("File '%s' downloading error%n", file.getName());
            System.out.printf("Error message: %s%n", e.getMessage());
            return false;
        }
    }

    private void copyBytesIfAllowed(InputStream source, OutputStream target) throws IOException, InterruptedException {
        int count;
        byte[] buffer = new byte[BUFFER_SIZE];
        while ((count = source.read(buffer)) != -1) {
            if (tokenBuket != null) {
                tokenBuket.emptyBucket(count);
            }
            target.write(buffer, 0, count);
        }
        target.flush();
    }

    /**
     * Class designed for download speed limiting.
     * The fillBucket() method fills the token bucket over determinate periods with values
     * depending on download speed limit within a separate thread.
     * The emptyBucket() method empties the token bucket by value passed as a parameter
     * only if the token bucket is greater or equal to this value.
     * Otherwise the method stops its thread until the token bucket is full enough. 
     * It is supposed to be used within a method that reads data from input stream,
     * thus limiting number of data readings depending on set speed limit.
     * @author kolokolov
     */
    private class TokenBucket {
        private final int BUCKET_FILLING_DELAY = 5; // ms
        private final int SPEED_LIMIT;
        
        private int bucket;

        public TokenBucket(int speedLimit) {
            this.SPEED_LIMIT = speedLimit;
            fillBucket();
        }

        public void fillBucket() {
            final int BUCKET_FILLING_STEP = SPEED_LIMIT * BUCKET_FILLING_DELAY / 1000 / 8; //bytes
            final int BUCKET_LIMIT = BUFFER_SIZE > BUCKET_FILLING_STEP ? BUFFER_SIZE * 2 : BUCKET_FILLING_STEP * 2; //bytes
            bucket = BUCKET_LIMIT;
            
            Thread bucketFiller = new Thread(() -> {
                while (true) {
                    try {
                        synchronized (this) {
                            while (bucket >= BUCKET_LIMIT) {
                                wait();
                            }
                            bucket += BUCKET_FILLING_STEP;
                            Thread.sleep(BUCKET_FILLING_DELAY);
                            notifyAll();
                        }
                    } catch (InterruptedException e) {
                        System.out.printf("Fatal error!!! Error message: %s%n", e.getMessage());
                        System.exit(1);
                    }
                }
            });
            bucketFiller.setDaemon(true);
            bucketFiller.start();
        }

        public synchronized void emptyBucket(int byteCount) throws InterruptedException {
            while (bucket < byteCount) {
                wait();
            }
            bucket -= byteCount;
            notifyAll();
        }
    }
}
