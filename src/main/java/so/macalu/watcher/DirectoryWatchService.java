package so.macalu.watcher;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * A simple class which can monitor files and notify interested parties
 * (i.e. listeners) of file changes.
 *
 * This class is kept lean by only keeping methods that are actually being
 * called.
 */
public class DirectoryWatchService implements IDirectoryWatchService, Runnable {

    private final WatchService mWatchService;
    private final AtomicBoolean mIsRunning;
    private final ConcurrentMap<WatchKey, Path> mWatchKeyToDirPathMap;
    private final ConcurrentMap<Path, Set<OnFileChangeListener>> mDirPathToListenersMap;
    private final ConcurrentMap<OnFileChangeListener, Set<PathMatcher>> mListenerToFilePatternsMap;

    /**
     * A simple no argument constructor for creating a <code>SimpleDirectoryWatchService</code>.
     *
     * @throws IOException If an I/O error occurs.
     */
    public DirectoryWatchService() throws IOException {
        mWatchService = FileSystems.getDefault().newWatchService();
        mIsRunning = new AtomicBoolean(false);
        mWatchKeyToDirPathMap = newConcurrentMap();
        mDirPathToListenersMap = newConcurrentMap();
        mListenerToFilePatternsMap = newConcurrentMap();
    }

    @SuppressWarnings("unchecked")
    private static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>)event;
    }

    private static <K, V> ConcurrentMap<K, V> newConcurrentMap() {
        return new ConcurrentHashMap<>();
    }

    private static <T> Set<T> newConcurrentSet() {
        return Collections.newSetFromMap(newConcurrentMap());
    }

    /**
     * Convert a String to a <code>PathMatcher</code>.
     * 
     * @param globPattern <code>String</code> Pattern string.
     * @return <code>PathMatcher</code> The PathMatcher representation.
     */
    public static PathMatcher matcherForGlobExpression(String globPattern) {
        return FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
    }

    public static boolean match(Path input, PathMatcher pattern) {
        return pattern.matches(input);
    }

    /**
     * Determine if the input is covered by the patterns
     * 
     * @param input <code>Path</code> The input path.
     * @param patterns <code>Set<PathMatcher></code> One or more patterns to compare to.
     * 
     * @return <code>boolean</code> Weather or not the input matches the patterns.
     */
    public static boolean matchesAny(Path input, Set<PathMatcher> patterns) {
        for (PathMatcher pattern : patterns) {
            if (pattern.matches(input)) {
                return true;
            }
        }

        return false;
    }

    private Path getDirPath(WatchKey key) {
        return mWatchKeyToDirPathMap.get(key);
    }

    private Set<OnFileChangeListener> getListeners(Path dir) {
        return mDirPathToListenersMap.get(dir);
    }

    private Set<PathMatcher> getPatterns(OnFileChangeListener listener) {
        return mListenerToFilePatternsMap.get(listener);
    }

    private Set<OnFileChangeListener> matchedListeners(Path dir, Path file) {
        return getListeners(dir)
                .stream()
                .filter(listener -> matchesAny(file, getPatterns(listener)))
                .collect(Collectors.toSet());
    }

    private void notifyListeners(WatchKey key) {
        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind eventKind = event.kind();

            // Overflow occurs when the watch event queue is overflown
            // with events.
            if (eventKind.equals(OVERFLOW)) {
                return;
            }

            WatchEvent<Path> pathEvent = cast(event);
            Path file = pathEvent.context();

            if (eventKind.equals(ENTRY_CREATE)) {
                matchedListeners(getDirPath(key), file)
                        .forEach(listener -> listener.onFileCreate(file.toString()));
            } else if (eventKind.equals(ENTRY_MODIFY)) {
                matchedListeners(getDirPath(key), file)
                        .forEach(listener -> listener.onFileModify(file.toString()));
            } else if (eventKind.equals(ENTRY_DELETE)) {
                matchedListeners(getDirPath(key), file)
                        .forEach(listener -> listener.onFileDelete(file.toString()));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void register(OnFileChangeListener listener, String dirPath, String... globPatterns)
            throws IOException {
        Path dir = Paths.get(dirPath);

        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException(dirPath + " is not a directory.");
        }

        if (!mDirPathToListenersMap.containsKey(dir)) {
            WatchKey key = dir.register(
                    mWatchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE
            );

            mWatchKeyToDirPathMap.put(key, dir);
            mDirPathToListenersMap.put(dir, newConcurrentSet());
        }

        getListeners(dir).add(listener);

        Set<PathMatcher> patterns = newConcurrentSet();

        for (String globPattern : globPatterns) {
            patterns.add(matcherForGlobExpression(globPattern));
        }

        if (patterns.isEmpty()) {
            // Match everything if no filter is found
            patterns.add(matcherForGlobExpression("*"));
        }

        mListenerToFilePatternsMap.put(listener, patterns);
    }

    /**
     * Start this <code>SimpleDirectoryWatchService</code> instance by spawning a new thread.
     *
     * @see #stop()
     */
    @Override
    public void start() {
        if (mIsRunning.compareAndSet(false, true)) {
            Thread runnerThread = new Thread(this, DirectoryWatchService.class.getSimpleName());
            runnerThread.start();
        }
    }

    /**
     * Stop this <code>SimpleDirectoryWatchService</code> thread.
     * The killing happens lazily, giving the running thread an opportunity
     * to finish the work at hand.
     *
     * @see #start()
     */
    @Override
    public void stop() {
        mIsRunning.set(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        while (mIsRunning.get()) {
            WatchKey key;
            try {
                key = mWatchService.take();
            } catch (InterruptedException e) {
                //LOGGER.info(DirectoryWatchService.class.getSimpleName() + " service interrupted.");
                break;
            }

            if (null == getDirPath(key)) {
                //LOGGER.error("Watch key not recognized.");
                continue;
            }

            notifyListeners(key);

            // Reset key to allow further events for this key to be processed.
            boolean valid = key.reset();
            if (!valid) {
                mWatchKeyToDirPathMap.remove(key);
                if (mWatchKeyToDirPathMap.isEmpty()) {
                    break;
                }
            }
        }

        mIsRunning.set(false);
        //LOGGER.info("Stopping file watcher service.");
    }
}