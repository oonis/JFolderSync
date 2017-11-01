package so.macalu.cache;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This is the abstract class for cache on a directory or folder.
 * If it is a folder, it will have it's own .jcache file within it
 */
public abstract class CacheInformation {
    /**
     * The file in which we are storing cache for.
     */
    protected final Path mFile;

    /**
     * The cache location for the file we are storing cache for.
     */
    protected final Path mCacheFile;

    /**
     * Flag for ignoring the file from being synchronized.
     */
    protected boolean mIgnoreFlag;

    /**
     * Time in which to ignore file changes when synchronizing.
     * Will be set to -1 if never frozen.
     */
    protected long mFrozenTime;

    /**
     * @param file
     * @throws IOException
     */
    public CacheInformation(Path file, boolean isRoot) throws IOException {
        mFile = file;
        if (Files.isDirectory(file)) {
            // It is a directory so we need to put .jcache in it
            File cacheFolder = new File(file.toFile(), ".jcache");
            Files.createDirectory(cacheFolder.toPath());
        }

        if (isRoot) {
            mCacheFile = null;
            return;
        }
        // we need to put it in the current .jcache

        File cacheFolder = new File(file.getParent().toFile(), ".jcache");
        File cacheFile = new File(cacheFolder, file.getFileName().toString() + ".json");
        mCacheFile = Files.createFile(cacheFile.toPath());
    }
}
