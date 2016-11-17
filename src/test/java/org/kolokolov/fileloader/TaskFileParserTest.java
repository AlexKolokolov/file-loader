package org.kolokolov.fileloader;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.kolokolov.fileloader.service.TaskFileParser;
import org.kolokolov.fileloader.service.TaskFileParser.TaskDescription;

public class TaskFileParserTest {
    
    private List<String> lines;
    private Set<TaskDescription> tasks;
    private TaskFileParser parser;
    
    @Before
    public void fillFiealds() {
        parser = new TaskFileParser();
        
        lines = new ArrayList<>();
        lines.add("first_link file1");
        lines.add("second_link file2");
        lines.add("first_link file3");
        
        tasks = new LinkedHashSet<>();
        tasks.add(new TaskDescription("first_link", "file1"));
        tasks.add(new TaskDescription("second_link", "file2"));
        tasks.add(new TaskDescription("first_link", "file3"));
        
    }

    @Test
    public void linesToTaskSetTest() {
        Assert.assertEquals(tasks, parser.linesToTaskSet(lines));
    }
}
