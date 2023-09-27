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

class MacOSXWatchKey extends AbstractWatchKey {

    public MacOSXWatchKey(
            AbstractWatchService macOSXWatchService,
            WatchablePath watchable,
            int queueSize) {
        super(macOSXWatchService, watchable, queueSize);
    }

    MacOSXListeningWatchService watchService() {
        return (MacOSXListeningWatchService) super.watchService();
    }
}
