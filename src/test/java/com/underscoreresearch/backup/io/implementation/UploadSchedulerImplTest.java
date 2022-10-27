package com.underscoreresearch.backup.io.implementation;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.hamcrest.Matchers;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.base.Stopwatch;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.io.IOPlugin;
import com.underscoreresearch.backup.io.IOProvider;
import com.underscoreresearch.backup.io.IOProviderFactory;
import com.underscoreresearch.backup.io.RateLimitController;
import com.underscoreresearch.backup.io.UploadScheduler;
import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupLimits;
import com.underscoreresearch.backup.model.BackupUploadCompletion;

public class UploadSchedulerImplTest {
    private RateLimitController rateLimitController;
    private BackupDestination destination;

    @BeforeEach
    public void setup() {
        InstanceFactory.initialize(new String[]{"--no-log", "--config-data", "{}"}, null);
        rateLimitController = new RateLimitController(null);
        destination = new BackupDestination();
        destination.setType("DELAY");

        IOProviderFactory.registerProvider("DELAY", DelayIOProvider.class);
    }

    @Test
    public void testConcurrency() {
        UploadScheduler scheduler = new UploadSchedulerImpl(10, rateLimitController);

        Stopwatch stopwatch = Stopwatch.createStarted();
        AtomicInteger completed = new AtomicInteger();
        AtomicBoolean success = new AtomicBoolean(true);

        for (int i = 0; i < 100; i++) {
            int val = i;
            scheduler.scheduleUpload(destination, "01234567890", i, new byte[i + 1], new BackupUploadCompletion() {
                @Override
                public void completed(String key) {
                    synchronized (completed) {
                        if (!key.equals("blocks" + PATH_SEPARATOR + "01" + PATH_SEPARATOR + "23"
                                + PATH_SEPARATOR + "4567890" + PATH_SEPARATOR + val)) {
                            success.set(false);
                        }
                        completed.incrementAndGet();
                        completed.notify();
                    }
                }
            });
        }

        synchronized (completed) {
            while (completed.get() < 100) {
                try {
                    completed.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        assertThat(stopwatch.elapsed(TimeUnit.MILLISECONDS), Matchers.greaterThan(900L));
        assertThat(stopwatch.elapsed(TimeUnit.MILLISECONDS), Matchers.lessThan(2000L));
        assertThat(success.get(), Is.is(true));
    }

    @Test
    public void testOverallRateLimit() {
        rateLimitController = new RateLimitController(BackupLimits.builder().maximumUploadBytesPerSecond(1000L).build());
        UploadScheduler scheduler = new UploadSchedulerImpl(100, rateLimitController);

        Stopwatch stopwatch = Stopwatch.createStarted();
        AtomicInteger completed = new AtomicInteger();
        AtomicBoolean success = new AtomicBoolean(true);

        for (int i = 0; i < 100; i++) {
            scheduler.scheduleUpload(destination, i + "", i, new byte[10], new BackupUploadCompletion() {
                @Override
                public void completed(String key) {
                    synchronized (completed) {
                        if (key == null) {
                            success.set(false);
                        }
                        completed.incrementAndGet();
                        completed.notify();
                    }
                }
            });
        }

        synchronized (completed) {
            while (completed.get() < 100) {
                try {
                    completed.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        assertThat(stopwatch.elapsed(TimeUnit.MILLISECONDS), Matchers.greaterThan(900L));
        assertThat(stopwatch.elapsed(TimeUnit.MILLISECONDS), Matchers.lessThan(1500L));
        assertThat(success.get(), Is.is(true));
    }

    @Test
    public void testDestinationRateLimit() {
        UploadScheduler scheduler = new UploadSchedulerImpl(100, rateLimitController);
        destination.setLimits(BackupLimits.builder().maximumUploadBytesPerSecond(1000L).build());

        BackupDestination destination2 = new BackupDestination();
        destination2.setType("DELAY");
        destination2.setLimits(BackupLimits.builder().maximumUploadBytesPerSecond(1000L).maximumDownloadBytesPerSecond(1L).build());

        Stopwatch stopwatch = Stopwatch.createStarted();
        AtomicInteger completed = new AtomicInteger();
        AtomicBoolean success = new AtomicBoolean(true);

        BackupUploadCompletion completion = new BackupUploadCompletion() {
            @Override
            public void completed(String key) {
                synchronized (completed) {
                    if (key == null) {
                        success.set(false);
                    }
                    completed.incrementAndGet();
                    completed.notify();
                }
            }
        };
        for (int i = 0; i < 100; i++) {
            scheduler.scheduleUpload(destination, i + "", i, new byte[10], completion);
            scheduler.scheduleUpload(destination2, i + "", i, new byte[10], completion);
        }

        synchronized (completed) {
            while (completed.get() < 200) {
                try {
                    completed.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        assertThat(stopwatch.elapsed(TimeUnit.MILLISECONDS), Matchers.greaterThan(900L));
        assertThat(stopwatch.elapsed(TimeUnit.MILLISECONDS), Matchers.lessThan(1500L));
        assertThat(success.get(), Is.is(true));
    }

    @Test
    public void testBothRateLimit() {
        rateLimitController = new RateLimitController(BackupLimits.builder().maximumUploadBytesPerSecond(1000L).build());
        UploadScheduler scheduler = new UploadSchedulerImpl(100, rateLimitController);
        destination.setLimits(BackupLimits.builder().maximumUploadBytesPerSecond(1000L).build());

        BackupDestination destination2 = new BackupDestination();
        destination2.setType("DELAY");
        destination2.setLimits(BackupLimits.builder().maximumUploadBytesPerSecond(1000L).maximumDownloadBytesPerSecond(1L).build());

        Stopwatch stopwatch = Stopwatch.createStarted();
        AtomicInteger completed = new AtomicInteger();
        AtomicBoolean success = new AtomicBoolean(true);

        BackupUploadCompletion completion = new BackupUploadCompletion() {
            @Override
            public void completed(String key) {
                synchronized (completed) {
                    if (key == null) {
                        success.set(false);
                    }
                    completed.incrementAndGet();
                    completed.notify();
                }
            }
        };
        for (int i = 0; i < 50; i++) {
            scheduler.scheduleUpload(destination, i + "", i, new byte[10], completion);
            scheduler.scheduleUpload(destination2, i + "", i, new byte[10], completion);
        }

        synchronized (completed) {
            while (completed.get() < 100) {
                try {
                    completed.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        assertThat(stopwatch.elapsed(TimeUnit.MILLISECONDS), Matchers.greaterThan(900L));
        assertThat(stopwatch.elapsed(TimeUnit.MILLISECONDS), Matchers.lessThan(1500L));
        assertThat(success.get(), Is.is(true));
    }

    @IOPlugin("DELAY")
    public static class DelayIOProvider implements IOProvider {
        public DelayIOProvider(BackupDestination destination) {
        }

        @Override
        public String upload(String suggestedKey, byte[] data) throws IOException {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return suggestedKey;
        }

        @Override
        public byte[] download(String key) throws IOException {
            throw new RuntimeException("Shouldn't get here");
        }

        @Override
        public void delete(String key) throws IOException {

        }

        @Override
        public void checkCredentials(boolean readonly) throws IOException {

        }
    }

}