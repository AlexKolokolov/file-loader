package org.kolokolov.fileloader.service;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

/**
 * The service designed to perform copying data from an URL to a file. It uses {@link ThreadService} to provide each
 * file copying in different thread and {@link TokenBucket} to limit copying speed.
 * 
 * @author kolokolov
 */
public class DownloadService {

    private final int BUFFER_SIZE = 1024;

    private ThreadService threadService;
    private TokenBucket tokenBuket;

    /**
     * Creates an instance of the DownloadService class. If a proper speed limit value is passed than an instance of
     * TokenBucket class is also created. Otherwise no TokenBucket class will be created and no speed limit set.
     * 
     * @param threadService an instance of the {@link ThreadService} class providing method executing in new thread.
     * @param speedLimit int value of speed limit in bits/s. If this value is equal or less than 0, than no speed limit
     *            is set.
     */
    public DownloadService(ThreadService threadService, int speedLimit) {
        this.threadService = threadService;
        if (speedLimit > 0) {
            this.tokenBuket = new TokenBucket(speedLimit);
            this.threadService.startNewDaemon(() -> {
                try {
                    this.tokenBuket.fillBucket();
                } catch (InterruptedException e) {
                    System.out.printf("Fatal error! Error message %s%n", e.getMessage());
                    System.exit(1);
                }
            });
        }
    }

    /**
     * Provides the downloadFiles() method execution in new thread using {@link ThreadService} object.
     * 
     * @param url an absolute URL of a web resource representing a file
     * @param files Collection of files for storing data read from web resource in
     * @return an object with the Future interface that returns the result returned by the lambda expression after its
     *         execution.
     */
    public Future<Boolean> downloadFilesInNewThread(URL url, List<File> files) {
        return threadService.executeInNewThread(() -> downloadFiles(url, files));
    }

    /**
     * Reads data form a web resource presented with an URL and than stores it in one or several files.
     * 
     * @param url an absolute URL of a web resource representing a file
     * @param files Collection of files for storing data read from web resource in
     * @return true if data reading and storing succeeded.
     */
    public boolean downloadFiles(URL url, List<File> files) {
        boolean multipleFiles = files.size() > 1;
        Set<String> fileNames = files.stream().map(File::getName).collect(Collectors.toSet());
        if (multipleFiles) {
            System.out.printf("Files %s downloading started%n", fileNames);
        } else {
            System.out.printf("File %s downloading started%n", fileNames);
        }
        try (InputStream input = url.openStream();
                BufferedOutputStream output = new BufferedOutputStream(new MultipleFileOutputStream(files))) {
            for (File file : files) {
                if (!file.exists()) {
                    file.createNewFile();
                }
            }

            long startTime = System.currentTimeMillis();

            copyBytesIfAllowed(input, output);

            long downloadTime = System.currentTimeMillis() - startTime; // ms
            long fileSize = FileUtils.sizeOf(files.get(0)); // bytes
            long downloadSpeed = 8 * fileSize * 1000 / downloadTime / 1024; // kbit/s
            String displayFileSize = FileUtils.byteCountToDisplaySize(fileSize); // in human readable format
            if (multipleFiles) {
                System.out.printf("Files %s (%s each) have been downloaded at %d kbit/s%n", fileNames, displayFileSize,
                        downloadSpeed);
            } else {
                System.out.printf("File %s (%s) has been downloaded at %d kbit/s%n", fileNames, displayFileSize,
                        downloadSpeed);
            }
            return true;
        } catch (IOException | InterruptedException e) {
            if (multipleFiles) {
                System.out.printf("Files %s downloading error%n", fileNames);
            } else {
                System.out.printf("File %s downloading error%n", fileNames);
            }
            System.out.printf("Error message: %s%n", e.getMessage());
            return false;
        }
    }

    /**
     * Reads data from an input stream and then writes it to an output stream if token bucket exists and allows this
     * action.
     * 
     * @param souce an instance of the InputStream
     * @param target an instance of the OutputStream
     * @throws IOException
     * @throws InterruptedException
     */
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
     * The class designed for download speed limiting. The fillBucket() method fills the token bucket over determinate
     * periods with values depending on download speed limit within a separate thread. The emptyBucket() method empties
     * the token bucket by value passed as a parameter only if the token bucket is greater or equal to this value.
     * Otherwise the method stops its thread until the token bucket is full enough. It is supposed to be used within a
     * method that reads data from input stream, thus limiting number of data readings depending on set speed limit
     * value.
     * 
     * @author kolokolov
     */
    private class TokenBucket {
        private final int BUCKET_FILLING_DELAY = 5; // ms
        private final int SPEED_LIMIT;

        private int bucket;

        private final Lock bucketLock = new ReentrantLock();
        private final Condition enoughTokens = bucketLock.newCondition();
        private final Condition notFullBucket = bucketLock.newCondition();

        public TokenBucket(int speedLimit) {
            this.SPEED_LIMIT = speedLimit;
        }

        public void fillBucket() throws InterruptedException {
            final int BUCKET_FILLING_STEP = SPEED_LIMIT * BUCKET_FILLING_DELAY / 1000 / 8; // bytes
            final int BUCKET_LIMIT = BUFFER_SIZE > BUCKET_FILLING_STEP ? BUFFER_SIZE * 2 : BUCKET_FILLING_STEP * 2; // bytes
            bucket = BUCKET_LIMIT;

            while (true) {
                bucketLock.lock();
                try {
                    while (bucket >= BUCKET_LIMIT) {
                        notFullBucket.await();
                    }
                    bucket += BUCKET_FILLING_STEP;
                    enoughTokens.signalAll();
                } finally {
                    bucketLock.unlock();
                    Thread.sleep(BUCKET_FILLING_DELAY);
                }
            }
        }

        public void emptyBucket(int byteCount) throws InterruptedException {
            bucketLock.lock();
            try {
                while (bucket < byteCount) {
                    enoughTokens.await();
                }
                bucket -= byteCount;
                notFullBucket.signal();
            } finally {
                bucketLock.unlock();
            }
        }
    }

    /**
     * The class designed for providing data storing to several files simultaneously.
     * 
     * @author kolokolov
     */
    private class MultipleFileOutputStream extends OutputStream {

        private final Set<FileOutputStream> fileOutputStreams = new HashSet<>();

        /**
         * Creates an instance of the class using a collection of File type objects.
         * 
         * @param files a Collection of {@link File} type object
         * @throws FileNotFoundException
         */
        public MultipleFileOutputStream(Collection<File> files) throws FileNotFoundException {
            for (File file : files) {
                if (file != null) {
                    this.fileOutputStreams.add(new FileOutputStream(file));
                }
            }
        }

        @Override
        public void write(int b) throws IOException {
            for (FileOutputStream fos : fileOutputStreams) {
                fos.write(b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            for (FileOutputStream fos : fileOutputStreams) {
                fos.write(b, off, len);
            }
        }

        @Override
        public void flush() throws IOException {
            for (FileOutputStream fos : fileOutputStreams) {
                fos.flush();
            }
        }

        @Override
        public void close() throws IOException {
            for (FileOutputStream fos : fileOutputStreams) {
                fos.close();
            }
        }
    }
}
