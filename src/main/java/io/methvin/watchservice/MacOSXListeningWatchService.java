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

/*
 * Heavily modified for Undersscore Backup use to efficiently handle entire filesystems.
 *
 * Original at https://github.com/gmethvin/directory-watcher
 */
package io.methvin.watchservice;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import io.methvin.watchservice.jna.CFArrayRef;
import io.methvin.watchservice.jna.CFIndex;
import io.methvin.watchservice.jna.CFRunLoopRef;
import io.methvin.watchservice.jna.CFStringRef;
import io.methvin.watchservice.jna.CarbonAPI;
import io.methvin.watchservice.jna.FSEventStreamRef;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * This class contains the bulk of my implementation of the Watch Service API. It hooks into
 * Carbon's File System Events API.
 *
 * @author Greg Methvin
 * @author Steve McLeod
 */
public class MacOSXListeningWatchService extends AbstractWatchService {

    private static final long kFSEventStreamEventIdSinceNow = -1; // this is 0xFFFFFFFFFFFFFFFF
    private static final int kFSEventStreamCreateFlagNoDefer = 0x00000002;
    private static final int kFSEventStreamCreateFlagFileEvents = 0x00000010;
    // need to keep reference to callbacks to prevent garbage collection
    @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
    private final List<CarbonAPI.FSEventStreamCallback> callbackList = new ArrayList<>();
    private final List<CFRunLoopThread> threadList = new ArrayList<>();
    private final Set<Path> pathsWatching = new HashSet<>();
    private final double latency;
    private final int queueSize;

    public MacOSXListeningWatchService(Config config) {
        this.latency = config.latency();
        this.queueSize = config.queueSize();
    }

    public MacOSXListeningWatchService() {
        this(new Config() {
        });
    }

    @Override
    public synchronized WatchKey register(
            WatchablePath watchable, Iterable<? extends WatchEvent.Kind<?>> events) throws IOException {
        checkOpen();
        final MacOSXWatchKey watchKey = new MacOSXWatchKey(this, watchable, queueSize);
        final Path file = watchable.getFile().toAbsolutePath();
        // if we are already watching a parent of this directory, do nothing.
        for (Path watchedPath : pathsWatching) {
            if (file.startsWith(watchedPath)) return watchKey;
        }

        final Pointer[] values = {CFStringRef.toCFString(file.toString()).getPointer()};
        final CFArrayRef pathsToWatch =
                CarbonAPI.INSTANCE.CFArrayCreate(null, values, CFIndex.valueOf(1), null);
        final MacOSXListeningCallback callback =
                new MacOSXListeningCallback(watchKey, file);
        callbackList.add(callback);
        int flags = kFSEventStreamCreateFlagNoDefer | kFSEventStreamCreateFlagFileEvents;

        final FSEventStreamRef streamRef =
                CarbonAPI.INSTANCE.FSEventStreamCreate(
                        Pointer.NULL,
                        callback,
                        Pointer.NULL,
                        pathsToWatch,
                        kFSEventStreamEventIdSinceNow,
                        latency,
                        flags);

        final CFRunLoopThread thread = new CFRunLoopThread(streamRef, file.toFile());
        callback.onClose(() -> close(thread, callback, file));

        thread.setDaemon(true);
        thread.start();
        threadList.add(thread);
        pathsWatching.add(file);
        return watchKey;
    }

    @Override
    public synchronized void close() {
        super.close();
        threadList.forEach(CFRunLoopThread::close);
        threadList.clear();
        callbackList.clear();
        pathsWatching.clear();
    }

    public synchronized void close(
            CFRunLoopThread runLoopThread, CarbonAPI.FSEventStreamCallback callback, Path path) {
        threadList.remove(runLoopThread);
        callbackList.remove(callback);
        pathsWatching.remove(path);
        runLoopThread.close();
    }

    /**
     * Configuration for the watch service.
     */
    public interface Config {

        double DEFAULT_LATENCY = 0.5;
        int DEFAULT_QUEUE_SIZE = 1024;

        /**
         * The maximum number of seconds to wait after hearing about an event
         */
        default double latency() {
            return DEFAULT_LATENCY;
        }

        /**
         * The size of the queue used for each WatchKey
         */
        default int queueSize() {
            return DEFAULT_QUEUE_SIZE;
        }
    }

    public static class CFRunLoopThread extends Thread {
        private final FSEventStreamRef streamRef;
        private CFRunLoopRef runLoopRef;
        private boolean isClosed = false;

        public CFRunLoopThread(FSEventStreamRef streamRef, File file) {
            super("WatchService for " + file);
            this.streamRef = streamRef;
        }

        @Override
        public void run() {
            synchronized (streamRef) {
                if (isClosed) return;
                runLoopRef = CarbonAPI.INSTANCE.CFRunLoopGetCurrent();
                final CFStringRef runLoopMode = CFStringRef.toCFString("kCFRunLoopDefaultMode");
                CarbonAPI.INSTANCE.FSEventStreamScheduleWithRunLoop(streamRef, runLoopRef, runLoopMode);
                CarbonAPI.INSTANCE.FSEventStreamStart(streamRef);
            }
            CarbonAPI.INSTANCE.CFRunLoopRun();
        }

        public void close() {
            synchronized (streamRef) {
                if (isClosed) return;
                if (runLoopRef != null) {
                    CarbonAPI.INSTANCE.CFRunLoopStop(runLoopRef);
                    CarbonAPI.INSTANCE.FSEventStreamStop(streamRef);
                    CarbonAPI.INSTANCE.FSEventStreamInvalidate(streamRef);
                }
                CarbonAPI.INSTANCE.FSEventStreamRelease(streamRef);
                isClosed = true;
            }
        }
    }

    private static class MacOSXListeningCallback implements CarbonAPI.FSEventStreamCallback {
        private final MacOSXWatchKey watchKey;
        private final Path realPath;
        private final Path absPath;
        private final int realPathSize;

        private Runnable onCloseCallback;

        private MacOSXListeningCallback(
                MacOSXWatchKey watchKey,
                Path absPath)
                throws IOException {
            this.watchKey = watchKey;
            this.realPath = absPath.toRealPath();
            this.absPath = absPath;
            this.realPathSize = realPath.toString().length() + 1;
        }

        public void onClose(Runnable callback) {
            this.onCloseCallback = callback;
        }

        @Override
        public void invoke(
                FSEventStreamRef streamRef,
                Pointer clientCallBackInfo,
                NativeLong numEvents,
                Pointer eventPaths,
                Pointer eventFlags /* array of unsigned int */,
                Pointer eventIds /* array of unsigned long */) {
            final int length = numEvents.intValue();

            for (String fileName : eventPaths.getStringArray(0, length)) {
                /*
                 * Simplifying this wildly because all I really care about is of something has changed.
                 * Does not matter if it is created, deleted or modified. That will be figured out later.
                 */

                Path path =
                        fileName.length() + 1 != realPathSize
                                ? absPath.resolve(fileName.substring(realPathSize))
                                : absPath;

                watchKey.signalEvent(ENTRY_MODIFY, path);

                if (!path.equals(realPath) && !realPath.toFile().exists()) {
                    // The underlying file is gone, cancel the key.
                    // This must happen before this event, so that DirectoryWatch also cleans up
                    watchKey.cancel();

                    // all underlying paths are gone, so stop this service
                    try {
                        onCloseCallback.run();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }
}
