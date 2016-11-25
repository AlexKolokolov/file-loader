# File-loader
****

Console file downloader.<br> 
Reads tasks list from simple text file with lines<br>
consisting of http link and target file separated with the whitespace.<br>
Can download file in several threads with download speed limiting.<br>
If the same link is repeatedly mapped on different file names,<br>
the application will download them all at once.
****

`mvn package` will create executable jar-file.

usage: java -jar file-loader.jar<br>
 -f, --file \<arg\>    - task file name<br>
 -l, --limit \<arg\>   - speed limit<br>
 -n  \<arg\>           - number of downloading threads<br>
 -o, --output <\arg\>  - output folder<br>
