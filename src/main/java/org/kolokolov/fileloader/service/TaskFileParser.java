package org.kolokolov.fileloader.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;

/**
 * The service is designed for parsing a download tasks file and return a set of task descriptions to main application
 * 
 * @author kolokolov
 */
public class TaskFileParser {

    /**
     * Parses the task file and returns a set of objects of {@link TaskDescription} type
     * produced of its lines.
     *  
     * @param file a task file to be parsed
     * @return a set of an objects of {@link TaskDescription} type
     * @throws IOException
     * @throws ParseException
     */
    public Set<TaskDescription> parseTaskFile(File file) throws IOException, ParseException {
        Set<TaskDescription> taskSet = null;
        if (file.exists() && file.canRead()) {
            List<String> lines = FileUtils.readLines(file, StandardCharsets.UTF_8);
            if (lines != null) {
                taskSet = linesToTaskSet(lines);
                if (taskSet != null && targetFileHasDuplicates(taskSet)) {
                    String errorMsg = String.format("Task file '%s' includes target file duplications%n", file.getName());
                    throw new ParseException(errorMsg);
                }
            } else {
                String errorMsg = String.format("Task file '%s' format error%n", file.getName());
                throw new ParseException(errorMsg);
            }
        } else {
            String errorMsg = String.format("Task file '%s' does not exist or cannot be read%n", file.getName());
            throw new FileNotFoundException(errorMsg);
        }
        return taskSet;
    }
    
    /**
     * Processes the list of the task file lines to set of objects
     * of the {@link TaskDescription} type.
     * 
     * @param lines a list of task file lines
     * @return a set of an objects of {@link TaskDescription} type
     */
    public Set<TaskDescription> linesToTaskSet(List<String> lines) {
        return lines.parallelStream().map(line -> this.splitLine(line))
                .filter(td -> td != null)
                .collect(Collectors.toSet());
    }
    
    /**
     * Splits a line of the task file into two parts and builds an object of the
     * {@link TaskDescription} class.
     * 
     * @param line a line of the task file. It is supposed to consist of URL and target file name 
     * @return an object of the {@link TaskDescription} type
     */
    public TaskDescription splitLine(String line) {
        TaskDescription taskDescription = null;
        String[] pair = line.split(" ");
        if (pair.length == 2) {
            taskDescription = new TaskDescription(pair[0], pair[1]);
        } else {
            System.out.printf("Error processing line %s%n", line);
        }
        return taskDescription;
    }

    /**
     * Checks whether there were equal target file names mapped on different links in the task file.
     * 
     * @param taskSet a set of download task descriptions.
     * @return true if the target file name duplications are present in task file
     */
    public boolean targetFileHasDuplicates(Set<TaskDescription> taskSet) {
        Map<String, TaskDescription> testMap = new HashMap<>();
        taskSet.forEach(task -> {
            TaskDescription duplication;
            if ((duplication = testMap.put(task.getFile(), task)) != null) {
                System.out.printf("File name '%s' mapped on different links has been found%n", duplication.getFile());
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