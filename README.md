# File-loader
****

Console file downloader. Reads tasks list from file. 
Can download file in several threads with download speed limiting.
****

`mvn package` will create executable jar-file.

usage: java -jar file-loader.jar
 -f,--file <arg>     task file name
 -l,--limit <arg>    speed limit
 -n <arg>            number of downloading threads
 -o,--output <arg>   output folder


Where `task file` - is a simple text file with lines consisting of http link and target file separated with the whitespace.
