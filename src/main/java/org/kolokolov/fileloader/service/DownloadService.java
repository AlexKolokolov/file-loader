package org.kolokolov.fileloader.service;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.Future;

import org.apache.commons.io.FileUtils;

public class DownloadService {
    
    private ThreadService threadService;
    
    public DownloadService() {
        threadService = new ThreadService();
    }
    
    public DownloadService(ThreadService threadService) {
        this.threadService = threadService;
    }
    
    public Future<Boolean> downloadFileInNewThread(URL url, File file) {
        return threadService.performInNewThread(() -> downloadFile(url, file));
    }
  
    public boolean downloadFile(URL url, File file) {
        synchronized (this) {
            System.out.printf("File %s downloading started%n", file.getName());
        }
        try {
            if (!file.exists()) file.createNewFile();
            long startTime = System.currentTimeMillis();
            FileUtils.copyURLToFile(url, file, 3000, 3000);
            long downloadTime = System.currentTimeMillis() - startTime;
            long byteSize = FileUtils.sizeOf(file);
            long downlodSpeed = 1000 * byteSize / downloadTime;
            String fileSize = FileUtils.byteCountToDisplaySize(byteSize);
            synchronized (this) {
                System.out.printf("File %s : %s : %d bit/s has been downloaded%n", file.getName(), fileSize, downlodSpeed);
            }
            return true;
        } catch (IOException ioe) {
            synchronized (this) {
                System.out.printf("File %s downloading error%n", file.getName());
                System.out.printf("Error message: %s%n", ioe.getMessage());
            }
            return false;
        }
    }
}
