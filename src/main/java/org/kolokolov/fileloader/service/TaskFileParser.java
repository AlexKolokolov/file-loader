package org.kolokolov.fileloader.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

/**
 * The service is designed for parsing a download tasks file 
 * and return a set of task descriptions to main application
 * @author kolokolov
 */
public class TaskFileParser {

    public Set<TaskDescription> parseTaskFile(File file) {
        if (file.exists() && file.canRead()) {
            List<String> lines = parseFileToLines(file);
            if (lines != null) {
                Set<TaskDescription> taskSet = linesToTaskSet(lines);
                if (taskSet != null) {
                    if (!targetFileHasDuplicates(taskSet)) {
                        return taskSet;
                    } else {
                        System.err.printf("Task file '%s' includes target file duplications%n", file.getName());
                    }
                } else {
                    System.err.printf("Task file '%s' format errors%n", file.getName());
                }
            }
        } else {
            System.err.printf("Task file '%s' does not exist or can not be read%n", file.getName());
        }
        return null;
    }

    public List<String> parseFileToLines(File file) {
        List<String> lines = null;
        try {
            lines = FileUtils.readLines(file, StandardCharsets.UTF_8);
        } catch (IOException ioe) {
            System.err.printf("File %s reading error: %s%n", file.getName(), ioe.getMessage());
        }
        return lines;
    }

    public Set<TaskDescription> linesToTaskSet(List<String> lines) {
        return lines.parallelStream().map(line -> {
            String[] pair = line.split(" ");
            if (pair.length == 2) {
                return new TaskDescription(pair[0], pair[1]);
            } else {
                System.out.printf("Error processing line %s%n", line);
                return null;
            }
        }).filter(td -> td != null).collect(Collectors.toSet());
    }

    /**
     * Method checks whether there were equal target file names 
     * mapped on different links in the task file.
     * @param set of download task descriptions.
     * @return true if the target file name duplications are present in task file
     */
    public boolean targetFileHasDuplicates(Set<TaskDescription> taskSet) {
        Map<String, TaskDescription> testMap = new HashMap<>();
        taskSet.forEach(task -> {
            TaskDescription duplication;
            if ((duplication = testMap.put(task.getFile(), task)) != null) {
                System.out.printf("File name '%s' mapped on different links was found%n", duplication.getFile());
            }
        });
        return testMap.size() != taskSet.size();
    }

    /**
     * Class is designed for task description storing.
     * 
     * @author kolokolov
     */
    public static class TaskDescription {
        private String url;
        private String file;

        public TaskDescription(String url, String file) {
            this.url = url;
            this.file = file;
        }

        public String getUrl() {
            return url;
        }

        public String getFile() {
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
            TaskDescription other = (TaskDescription) obj;
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