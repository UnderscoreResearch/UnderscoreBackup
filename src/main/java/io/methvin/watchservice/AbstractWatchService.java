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

import lombok.EqualsAndHashCode;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

/**
 * Abstract implementation of {@link WatchService}. Provides the means for registering and managing
 * keys but does not handle actually watching. Subclasses should implement the means of watching
 * watchables, posting events to registered keys and queueing keys with the service by signalling
 * them.
 *
 * @author Colin Decker
 * @author Greg Methvin
 */
abstract class AbstractWatchService implements WatchService {

    private final BlockingQueue<WatchKey> queue = new LinkedBlockingQueue<>();
    private final WatchKey poison = new AbstractWatchKey(this, null, 1);

    private final AtomicBoolean open = new AtomicBoolean(true);

    /**
     * Registers the given watchable with this service, returning a new watch key for it. This
     * implementation just checks that the service is open and creates a key; subclasses may override
     * it to do other things as well.
     */
    public abstract WatchKey register(
            WatchablePath watchable, Iterable<? extends WatchEvent.Kind<?>> eventTypes)
            throws IOException;

    /**
     * Returns if this watch service is open.
     */
    public boolean isOpen() {
        return open.get();
    }

    /**
     * Enqueues the given key if the watch service is open; does nothing otherwise.
     */
    final void enqueue(AbstractWatchKey key) {
        if (isOpen()) {
            queue.add(key);
        }
    }

    /**
     * Called when the given key is cancelled. Does nothing by default.
     */
    public void cancelled(AbstractWatchKey ignored) {
    }

    @Override
    public WatchKey poll() {
        checkOpen();
        return check(queue.poll());
    }

    @Override
    public WatchKey poll(long timeout, TimeUnit unit) throws InterruptedException {
        checkOpen();
        return check(queue.poll(timeout, unit));
    }

    @Override
    public WatchKey take() throws InterruptedException {
        checkOpen();
        return check(queue.take());
    }

    /**
     * Returns the given key, throwing an exception if it's the poison.
     */
    private WatchKey check(WatchKey key) {
        if (key == poison) {
            // ensure other blocking threads get the poison
            poisonQueue();
            throw new ClosedWatchServiceException();
        }
        return key;
    }

    private void poisonQueue() {
        if (!queue.offer(poison))
            throw new RuntimeException();
    }

    /**
     * Checks that the watch service is open, throwing {@link ClosedWatchServiceException} if not.
     */
    protected final void checkOpen() {
        if (!open.get()) {
            throw new ClosedWatchServiceException();
        }
    }

    @Override
    public void close() {
        if (open.compareAndSet(true, false)) {
            queue.clear();
            poisonQueue();
        }
    }

    /**
     * A basic implementation of {@link WatchEvent}.
     */
    @EqualsAndHashCode
    static final class Event<T> implements WatchEvent<T> {

        private final Kind<T> kind;
        private final int count;

        private final T context;

        public Event(Kind<T> kind, int count, T context) {
            this.kind = requireNonNull(kind);
            if (count < 0) {
                throw new IllegalArgumentException(String.format("count (\u200E%s\u200E) must be non-negative", count));
            }
            this.count = count;
            this.context = context;
        }

        @Override
        public Kind<T> kind() {
            return kind;
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public T context() {
            return context;
        }
    }
}
