/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package so.macalu.watcher;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import com.google.common.hash.HashCode;
import so.macalu.watcher.DirectoryChangeEvent.EventType;
import so.macalu.watchservice.MacOSXListeningWatchService;
import so.macalu.watchservice.WatchablePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class DirectoryWatcher {

  static final Logger logger = LoggerFactory.getLogger(DirectoryWatcher.class);

  private final WatchService watchService;
  private final List<Path> paths;
  private final boolean isMac;
  private final DirectoryChangeListener listener;
  private final Map<Path, HashCode> pathHashes;
  private final Map<WatchKey, Path> keyRoots;

  public static DirectoryWatcher create(Path path, DirectoryChangeListener listener) throws IOException {
    return create(Collections.singletonList(path), listener);
  }

  public static DirectoryWatcher create(List<Path> paths, DirectoryChangeListener listener) throws IOException {
    boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");
    WatchService ws = isMac ? new MacOSXListeningWatchService() : FileSystems.getDefault().newWatchService();
    return new DirectoryWatcher(paths, listener, ws);
  }

  public DirectoryWatcher(List<Path> paths, DirectoryChangeListener listener, WatchService watchService) throws IOException {
    this.paths = paths;
    this.listener = listener;
    this.watchService = watchService;
    this.isMac = watchService instanceof MacOSXListeningWatchService;
    this.pathHashes = PathUtils.createHashCodeMap(paths);
    this.keyRoots = PathUtils.createKeyRootsMap();

    for (Path path : paths) {
      registerAll(path);
    }
  }

  /**
   * Asynchronously watch the directories using ForkJoinPool.commonPool() as the executor
   */
  public CompletableFuture<Void> watchAsync() {
    return watchAsync(ForkJoinPool.commonPool());
  }

  /**
   * Asynchronously watch the directories.
   *
   * @param executor the executor to use to watch asynchronously
   */
  public CompletableFuture<Void> watchAsync(Executor executor) {
    return CompletableFuture.supplyAsync(() -> {
      watch();
      return null;
    }, executor);
  }

  /**
   * Watch the directories. Block until either the listener stops watching or the DirectoryWatcher is closed.
   */
  public void watch() {
    for (;;) {
      if (!listener.isWatching()) {
        return;
      }
      // wait for key to be signalled
      WatchKey key;
      try {
        key = watchService.take();
      } catch (InterruptedException x) {
        return;
      }
      for (WatchEvent<?> event : key.pollEvents()) {
        try {
          WatchEvent.Kind<?> kind = event.kind();
          // Context for directory entry event is the file name of entry
          WatchEvent<Path> ev = PathUtils.cast(event);
          int count = ev.count();
          Path name = ev.context();
          if (!keyRoots.containsKey(key)) {
            throw new IllegalStateException(
                "WatchService returned key [" + key + "] but it was not found in keyRoots!");
          }
          Path child = keyRoots.get(key).resolve(name);
          // if directory is created, and watching recursively, then register it and its sub-directories
          logger.debug("{} [{}]", kind, child);
          if (kind == OVERFLOW) {
            listener.onEvent(new DirectoryChangeEvent(EventType.OVERFLOW, child, count));
          } else if (kind == ENTRY_CREATE) {
            if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
              registerAll(child);
            } else {
              HashCode newHash = PathUtils.hash(child);
              // newHash can be null if the file was deleted before we had a chance to hash it
              if (newHash != null) {
                pathHashes.put(child, newHash);
              } else {
                logger.debug("Failed to hash created file [{}]. It may have been deleted.", child);
              }
            }
            listener.onEvent(new DirectoryChangeEvent(EventType.CREATE, child, count));
          } else if (kind == ENTRY_MODIFY) {
            // Note that existingHash may be null due to the file being created before we start listening
            // It's important we don't discard the event in this case
            HashCode existingHash = pathHashes.get(child);

            // newHash can be null when using File#delete() on windows - it generates MODIFY and DELETE in succession
            // in this case the MODIFY event can be safely ignored
            HashCode newHash = PathUtils.hash(child);

            if (newHash != null && !newHash.equals(existingHash)) {
              pathHashes.put(child, newHash);
              listener.onEvent(new DirectoryChangeEvent(EventType.MODIFY, child, count));
            } else if (newHash == null) {
              logger.debug("Failed to hash modified file [{}]. It may have been deleted.", child);
            }
          } else if (kind == ENTRY_DELETE) {
            pathHashes.remove(child);
            listener.onEvent(new DirectoryChangeEvent(EventType.DELETE, child, count));
          }
        } catch (Exception e) {
          listener.onException(e);
        }
      }
      boolean valid = key.reset();
      if (!valid) {
        logger.debug("WatchKey for [{}] no longer valid; removing.", key.watchable());
        // remove the key from the keyRoots
        keyRoots.remove(key);
        // if there are no more keys left to watch, we can break out
        if (keyRoots.isEmpty()) {
          logger.debug("No more directories left to watch; terminating watcher.");
          break;
        }
      }
    }
  }

  private void register(Path directory) throws IOException {
    logger.debug("Registering [{}].", directory);
    Watchable watchable = isMac ? new WatchablePath(directory) : directory;
    keyRoots.put(watchable.register(watchService, new WatchEvent.Kind[] {ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY}), directory);
  }

  /**
   * Grabs the listener attached to the watcher.
   */
  public DirectoryChangeListener getListener() {
    return listener;
  }

  public void close() throws IOException {
    watchService.close();
  }

  private void registerAll(final Path start) throws IOException {
    if (isMac) {
      // For the mac implementation, we will get events for subdirectories too
      register(start);
    } else {
      // register root directory and sub-directories
      Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
          register(dir);
          return FileVisitResult.CONTINUE;
        }
      });
    }
  }

}
