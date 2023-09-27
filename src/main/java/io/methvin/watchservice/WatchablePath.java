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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.util.Arrays;

import lombok.Getter;

@Getter
public class WatchablePath implements Watchable {
    private static final WatchEvent.Modifier[] NO_MODIFIERS = new WatchEvent.Modifier[0];
    private final Path file;

    public WatchablePath(Path file) {
        if (file == null) {
            throw new NullPointerException("file must not be null");
        }
        this.file = file;
    }

    @Override
    public WatchKey register(
            WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers)
            throws IOException {
        if (watcher == null) {
            throw new NullPointerException();
        }
        if (!(watcher instanceof AbstractWatchService)) {
            throw new ProviderMismatchException();
        }
        return ((AbstractWatchService) watcher).register(this, Arrays.asList(events));
    }

    @Override
    public final WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events)
            throws IOException {
        if (!file.toFile().exists()) {
            throw new RuntimeException("Directory to watch doesn't exist: " + file);
        }
        return register(watcher, events, NO_MODIFIERS);
    }
}
