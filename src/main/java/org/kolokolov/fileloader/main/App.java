package org.kolokolov.fileloader.main;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.kolokolov.fileloader.service.DownloadService;
import org.kolokolov.fileloader.service.TaskFileParser;
import org.kolokolov.fileloader.service.TaskFileParser.TaskDescription;
import org.kolokolov.fileloader.service.ThreadService;

/**
 * Main application class with main method
 * @author Alex Kolokolov
 */
public class App {

    private static String taskFileName;
    private static int threadLimit;
    private static String outputFolderName = "download";

    private Set<Task> tasks;
    private int tasksTotal;
    private int downloaded;
    private long downloadedSize;
    private int failed;
    private long elapsedTime;

    Map<Task, Future<Boolean>> downloadReports = new HashMap<>();

    private TaskFileParser parser;
    private DownloadService downloadService;
    
    public static void main(String[] args) {
        
        App.parseArgs(args);
        
        App app = new App();
        app.setDependensies();
        app.getTasks();
        app.processTasks();
        app.printReport();
        System.exit(0);
    }

    public static void parseArgs(String[] args) {
        
        Options options = new Options();
        Option taskFile = new Option("f", "file", true, "task file name");
        taskFile.setRequired(true);
        options.addOption(taskFile);

        Option limit = new Option("n", true, "number of downloading threads");
        options.addOption(limit);
        
        Option output = new Option("o", "output", true,"output folder");
        options.addOption(output);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmdLine = null;

        try {
            cmdLine = parser.parse(options, args);
        } catch (ParseException pe) {
            formatter.printHelp("java -jar file-loader.jar", options);
            System.exit(1);
        }

        taskFileName = cmdLine.getOptionValue("file");
        String sThreadLimit = cmdLine.getOptionValue("n");
        if (sThreadLimit != null) {
            threadLimit = Integer.parseInt(sThreadLimit);
        }
        String outputFolder = cmdLine.getOptionValue("o");
        if (outputFolder != null) {
            outputFolderName = outputFolder;
        }
    }
    
    public File createOutputFolder() {
       File outputFolder = new File(outputFolderName);
       if (!outputFolder.exists() && !outputFolder.mkdirs()) {
           System.err.printf("Can not create output folder '%s'%n", outputFolder.getAbsolutePath());
           System.exit(1);
       } else if (!outputFolder.isDirectory()) {
           System.err.printf("Existing file '%s' can not be an output folder%n", outputFolder.getAbsolutePath());
           System.exit(1);
       }
       return outputFolder;
    }

    public void getTasks() {
        File outputFolder = createOutputFolder();
        System.out.printf("Starting download to %s%n", outputFolder.getAbsolutePath());
        Set<TaskDescription> taskDescriptions = parser.parseTaskFile(new File(taskFileName));
        if (taskDescriptions != null) {
            tasks = taskDescriptions.stream().map(td -> {
                File file = new File(outputFolder, td.getFile());
                try {
                    URL url = new URL(td.getUrl());
                    return new Task(url, file);
                } catch (IOException ioe) {
                    System.out.printf("Error processing task description %s : %s%n", td.getUrl(), td.getFile());
                    System.out.printf("Error message: %s%n", ioe.getMessage());
                    return null;
                }
            }).collect(Collectors.toSet());
            tasksTotal = tasks.size();
        } else {
            System.err.printf("Error getting tasks from file %s%n", taskFileName);
            System.exit(1);
        }
    }

    public void processTasks() {
        
        long startTime = System.currentTimeMillis();
        
        for (Task task : tasks) {
            if (task != null) {
                downloadReports.put(task, downloadService.downloadFileInNewThread(task.getUrl(), task.getFile()));
            }
        }

        downloadReports.forEach((task, report) -> {
            try {
                if (report.get()){
                    downloaded++;
                    downloadedSize += FileUtils.sizeOf(task.getFile());
                } else failed++;
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        });
        
        elapsedTime = System.currentTimeMillis() - startTime;
    }

    public void printReport() {
        synchronized (downloadService) {
            System.out.println("Download has been completed");
            System.out.printf("Tasks total : %d%n", tasksTotal);
            System.out.printf("Downloaded : %d%n", downloaded);
            System.out.printf("Failed : %d%n", failed);
            System.out.printf("Total size : %s%n", FileUtils.byteCountToDisplaySize(downloadedSize));
            System.out.printf("Downloading time : %.03f sec%n", (double) elapsedTime / 1000);
            System.out.printf("Average download speed : %d kbit/sec%n", 8 * 1000 * downloadedSize / elapsedTime / 1000);
        }
    }

    public void setDependensies() {
        this.parser = new TaskFileParser();
        if (threadLimit != 0) {
            this.downloadService = new DownloadService(new ThreadService(threadLimit), 100);
        } else {
            this.downloadService = new DownloadService(100);
        }
    }
    
    public static class Task {
        private URL url;
        private File file;
        
        public Task(URL url, File file) {
            this.url = url;
            this.file = file;
        }

        public URL getUrl() {
            return url;
        }

        public File getFile() {
            return file;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((file == null) ? 0 : file.hashCode());
            result = prime * result + ((url == null) ? 0 : url.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Task other = (Task) obj;
            if (file == null) {
                if (other.file != null)
                    return false;
            } else if (!file.equals(other.file))
                return false;
            if (url == null) {
                if (other.url != null)
                    return false;
            } else if (!url.equals(other.url))
                return false;
            return true;
        }
    }
}
