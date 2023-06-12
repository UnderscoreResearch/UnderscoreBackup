package com.underscoreresearch.backup.cli.web.service;

import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rs.RsText;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.ProvisionException;
import com.underscoreresearch.backup.cli.web.JsonWrap;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionKey;

@Slf4j
public class BestRegionGet extends JsonWrap {
    private static final Map<String, List<URL>> REGIONS;
    private static final int ITERATIONS = 5;
    private static ObjectWriter WRITER = MAPPER.writerFor(BestRegion.class);

    static {
        try {
            REGIONS = ImmutableMap.of(
                    "us-west", Lists.newArrayList(new URL("https://s3.us-west-1.wasabisys.com"), new URL("https://s3.us-east-1.wasabisys.com")),
                    "eu-central", Lists.newArrayList(new URL("https://s3.eu-central-2.wasabisys.com"), new URL("https://s3.eu-west-1.wasabisys.com")),
                    "ap-southeast", Lists.newArrayList(new URL("https://s3.ap-southeast-1.wasabisys.com"), new URL("https://s3.ap-northeast-1.wasabisys.com"))
            );
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public BestRegionGet() {
        super(new Implementation());
    }

    static String determineBestRegion() {
        Map<String, AtomicLong> result = REGIONS.entrySet().stream().map((entry) -> (Maps.immutableEntry(entry.getKey(),
                new AtomicLong()))).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<String, AtomicInteger> successCount = REGIONS.entrySet().stream().map((entry) -> (Maps.immutableEntry(entry.getKey(),
                new AtomicInteger()))).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        ExecutorService pool = Executors.newFixedThreadPool(ITERATIONS
                * REGIONS.values().stream().map(List::size).reduce(0, Integer::sum));
        try {
            List<Future> futures = new ArrayList<>();
            for (int i = 0; i < ITERATIONS; i++) {
                for (Map.Entry<String, List<URL>> entry : REGIONS.entrySet()) {
                    final String region = entry.getKey();
                    for (URL endpoint : entry.getValue()) {
                        futures.add(pool.submit(() -> {
                            try {
                                Stopwatch timer = Stopwatch.createStarted();
                                HttpURLConnection con = (HttpURLConnection) endpoint.openConnection();
                                con.setConnectTimeout(3000);
                                con.setReadTimeout(3000);
                                con.setRequestMethod("OPTIONS");
                                if (con.getResponseCode() < 500) {
                                    result.get(region).addAndGet(timer.elapsed(TimeUnit.MILLISECONDS));
                                    successCount.get(region).incrementAndGet();
                                }
                            } catch (IOException exc) {
                            }
                        }));
                    }
                }
            }
            futures.forEach(item -> {
                try {
                    item.get();
                } catch (InterruptedException | ExecutionException e) {
                }
            });

            double bestLatency = 0;
            String bestRegion = null;
            for (Map.Entry<String, AtomicLong> entry : result.entrySet()) {
                double success = successCount.get(entry.getKey()).get();
                double latency = entry.getValue().get() / success;
                log.info("{} had average latency of {}ms and succeeded {} times", entry.getKey(), latency, success);
                if (success >= ITERATIONS * 0.75 && (bestRegion == null || bestLatency > latency)) {
                    bestRegion = entry.getKey();
                    bestLatency = latency;
                }
            }
            return bestRegion;
        } finally {
            pool.shutdownNow();
        }
    }

    @Data
    @AllArgsConstructor
    public static class BestRegion {
        private String region;
    }

    private static class Implementation implements Take {
        @Override
        public Response act(Request req) throws Exception {
            try {
                return new RsText(WRITER.writeValueAsString(new BestRegion(determineBestRegion())));
            } catch (Throwable exc) {
                log.warn("Failed to write best region", exc);
            }
            return messageJson(404, "Failed to fetch current activity");
        }

        private boolean hasKey() {
            try {
                InstanceFactory.getInstance(EncryptionKey.class);
                return true;
            } catch (ProvisionException exc) {
                return false;
            }
        }
    }
}
