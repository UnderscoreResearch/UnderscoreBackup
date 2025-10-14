# Reed-Solomon Implementation Package

This package contains the core implementation of the Reed-Solomon error correction algorithm used in the Underscore Backup system.

Reed-Solomon is a forward error correction code that works by oversampling a polynomial constructed from the data. The algorithm can detect and correct multiple symbol errors, making it ideal for error correction in backup data where portions of the data may be corrupted or lost.

This entire directory is copyright 2015, Backblaze, Inc although some modifications have been made to the original code. The original code is licensed under the Apache License, Version 2.0.