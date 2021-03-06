package org.kolokolov.fileloader.main;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;
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
 * The main application class with main method
 * 
 * @author kolokolov
 */
public class App {

    private static String taskFileName;
    private static int threadsNumber;
    private static int speedLimit;
    private static String outputFolderName = "download";

    private int tasksTotal;
    private int downloaded;
    private long downloadedSize;
    private int failed;
    private long elapsedTime;

    private TaskFileParser parser;
    private DownloadService downloadService;

    public App() {
        this.parser = new TaskFileParser();
        this.downloadService = new DownloadService(new ThreadService(threadsNumber), speedLimit);
    }

    public static void main(String[] args) {

        App.parseArgs(args);

        App app = new App();
        app.printInitReport();
        Set<TaskDescription> taskDescriptions = app.getTaskDescriptions(taskFileName);
        Map<URL, Task> taskMap = app.createTasks(taskDescriptions);
        Map<Task, Future<Boolean>> downloadReports = app.startTasks(taskMap);
        app.processDownloadReports(downloadReports);
        app.printReport();
    }

    public void printInitReport() {
        if (speedLimit > 0) {
            System.out.printf("Download speed limit = %.03f kbit/s%n", (double) speedLimit / 1024);
        } else {
            System.out.println("No speed limit");
        }

        if (threadsNumber > 0) {
            System.out.printf("Download threads: %d%n", threadsNumber);
        } else {
            System.out.println("Download threads number was not specified");
        }
    }

    /**
     * Parses the command line argument set and stores the values received form it in static variables.
     * 
     * @param args the command line arguments array
     */
    public static void parseArgs(String[] args) {

        Options options = new Options();
        Option taskFile = new Option("f", "file", true, "task file name");
        taskFile.setRequired(true);
        options.addOption(taskFile);

        Option threads = new Option("n", true, "number of downloading threads");
        options.addOption(threads);

        Option output = new Option("o", "output", true, "output folder");
        options.addOption(output);

        Option limit = new Option("l", "limit", true, "speed limit");
        options.addOption(limit);

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

        String sThreadsNumber = cmdLine.getOptionValue("n");
        if (sThreadsNumber != null) {
            threadsNumber = Integer.parseInt(sThreadsNumber);
        }

        String outputFolder = cmdLine.getOptionValue("o");
        if (outputFolder != null) {
            outputFolderName = outputFolder;
        }

        String sSpeedLimit = cmdLine.getOptionValue("l");
        if (sSpeedLimit != null) {
            int factor = 1;
            if (sSpeedLimit.endsWith("k")) {
                factor = 1024;
            }
            if (sSpeedLimit.endsWith("m")) {
                factor = 1024 * 1024;
            }
            speedLimit = Integer.parseInt(sSpeedLimit.split("\\D")[0]) * factor;
        }
    }

    /**
     * Analyzes passed an output folder name and creates the folder if it does not exist and can be created. If folder
     * creation failed the application would be closed with error code '1'.
     * 
     * @return created folder
     */
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
    
    /**
     * Uses the object of {@link TaskFileParser} class to parse a task file
     * and get a set of {@link TaskDescription} objects.
     * 
     * @param taskFileName a name of the task file
     * @return a set of an objects of {@link TaskDescription} type
     */
    public Set<TaskDescription> getTaskDescriptions(String taskFileName) {
        File taskFile = new File(taskFileName);
        System.out.printf("Processing task file '%s'%n", taskFile.getName());
        Set<TaskDescription> taskDescriptions = null;
        try {
            taskDescriptions = parser.parseTaskFile(taskFile);
        } catch (IOException | ParseException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        return taskDescriptions;
    }

    /**
     * Creates instances of {@link Task} class based on task descriptions and stores them into a Map.
     * If several task descriptions include the same URL, then files from such task descriptions will be added to the
     * same task instance in order to avoid multiple downloading of the same file.
     * 
     * @param taskDescriptions a set of an objects of {@link TaskDescription} type
     * @return a map of URLs mapped on the appropriate tasks
     */
    public Map<URL, Task> createTasks(Set<TaskDescription> taskDescriptions) {
        File outputFolder = createOutputFolder();
        Map<URL, Task> taskMap = new HashMap<>();
        taskDescriptions.parallelStream().forEach(td -> {
            File file = new File(outputFolder, td.getFile());
            try {
                System.out.printf("Processing URL: '%s'%n", td.getUrl());
                URL url = new URL(td.getUrl());
                Task task = new Task(url, file);
                taskMap.merge(url, task, Task::combainTasks);
            } catch (IOException ioe) {
                System.out.printf("Error processing task description '%s : %s'%n", td.getUrl(), td.getFile());
                System.out.printf("Error message: %s%n", ioe.getMessage());
            }
        });
        tasksTotal = taskMap.size();
        System.out.printf("Total tasks: %d%n", tasksTotal);
        System.out.printf("Output folder: '%s'%n", outputFolder.getAbsolutePath());
        return taskMap;
    }

    /**
     * Process the map with task stored in. 
     * Tries to start every task using {@link DownloadService} and then stores the
     * results of tasks performing to reports map.
     * 
     * @param taskMap a map of URLs mapped on the appropriate tasks
     * @return a map of tasks mapped on their {@link Future} report
     */
    public Map<Task, Future<Boolean>> startTasks(Map<URL, Task> taskMap) {
        return taskMap.values().stream().collect(Collectors.toMap(Function.identity(),
                task -> downloadService.downloadFilesInNewThread(task.getUrl(), task.getFiles())));
    }

    /**
     * Processes the map of {@link Future} download reports and
     * analyzes the results.
     * 
     * @param downloadReports a map of tasks mapped on their {@link Future} report
     */
    public void processDownloadReports(Map<Task, Future<Boolean>> downloadReports) {
        long startTime = System.nanoTime();
        downloadReports.forEach((task, report) -> {
            try {
                if (report.get()) {
                    downloaded++;
                    downloadedSize += FileUtils.sizeOf(task.getOneOfFiles());
                } else
                    failed++;
            } catch (InterruptedException | ExecutionException e) {
                System.out.printf("Error downloading from %s%n", task.getOneOfFiles());
                System.out.printf("Error message: %s%n", e.getMessage());
                e.printStackTrace();
            }
        });
        elapsedTime = System.nanoTime() - startTime;
        downloadService.closeDownloadThreads();
    }

    public void printReport() {
        System.out.println("Download has been completed");
        System.out.printf("Tasks total: %d%n", tasksTotal);
        System.out.printf("Completed: %d%n", downloaded);
        System.out.printf("Failed: %d%n", failed);
        System.out.printf("Total downloaded size: %s%n", FileUtils.byteCountToDisplaySize(downloadedSize));
        System.out.printf("Total download time: %.03f sec%n", (double) elapsedTime / 1_000_000_000);
        System.out.printf("Average download speed: %.03f kbit/s%n", 8.0 * 1_000_000_000 * downloadedSize / elapsedTime / 1024);
    }

    private static class Task {
        private URL url;
        private List<File> files = new ArrayList<>();

        public Task(URL url, File file) {
            this.url = url;
            this.files.add(file);
        }

        public Task(URL url, List<File> files) {
            this.url = url;
            this.files.addAll(files);
        }

        public URL getUrl() {
            return url;
        }

        public File getOneOfFiles() {
            return files.get(0);
        }

        public List<File> getFiles() {
            return this.files;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((files == null) ? 0 : files.hashCode());
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
            if (files == null) {
                if (other.files != null)
                    return false;
            } else if (!files.equals(other.files))
                return false;
            if (url == null) {
                if (other.url != null)
                    return false;
            } else if (!url.equals(other.url))
                return false;
            return true;
        }

        public static Task combainTasks(Task t1, Task t2) {
            Task result = new Task(t1.url, t1.files);
            result.files.addAll(t2.files);
            return result;
        }
    }
}