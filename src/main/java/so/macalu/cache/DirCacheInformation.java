package so.macalu.cache;

import java.io.IOException;
import java.nio.file.Path;

public class DirCacheInformation extends CacheInformation {
    private final boolean mRootFlag;

    public DirCacheInformation(Path dir, boolean isRoot) throws IOException {
        super(dir, isRoot);
        mRootFlag = isRoot;
    }
}
