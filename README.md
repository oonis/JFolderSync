[![Maven](https://img.shields.io/maven-central/v/so.macalu/jfoldersync.svg)](http://mvnrepository.com/artifact/so.macalu/jfoldersync)
# JFolderSync

Folder watcher and synchronization library built on ZeroMQ.
Please note that this is in the very early stages, and at the moment only watches locally.

## Installing
```xml
<dependency>
    <groupId>so.macalu</groupId>
    <artifactId>jfoldersync</artifactId>
    <version>0.6</version>
</dependency>
```

## API
There are three seperate parts to synchronize a file
### Adding a Watcher
```java
public class Main {

    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            DirectoryWatchService watchService = new SimpleDirectoryWatchService();
            watchService.register(
                    new DirectoryWatchService.OnFileChangeListener() {
                        @Override
                        public void onFileCreate(String filePath) {
                            // File created
                        }
                
                        @Override
                        public void onFileModify(String filePath) {
                            // File modified
                        }
                        
                        @Override
                        public void onFileDelete(String filePath) {
                            // File deleted
                        }
                    },
                    <directory>, // Directory to watch
                    <file-glob-pattern-1>, // E.g. "*.log"
                    <file-glob-pattern-2>, // E.g. "input-?.txt"
                    <file-glob-pattern-3>, // E.g. "config.ini"
                    ... // As many patterns as you like
            );
            
            watchService.start();
        } catch (IOException e) {
            LOGGER.error("Unable to register file change listener for " + fileName);
        }

        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                watchService.stop();
                LOGGER.error("Main thread interrupted.");
                break;
            }
        }
    }
}
```

## Credits
TODO