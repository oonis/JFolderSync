package so.macalu.cache;

import java.io.IOException;
import java.nio.file.Path;

public class FileCacheInformation extends CacheInformation{
    public FileCacheInformation(Path file) throws IOException {
        super(file,false);
    }
}
