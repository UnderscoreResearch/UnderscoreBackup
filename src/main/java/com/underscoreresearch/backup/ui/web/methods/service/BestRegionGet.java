package com.underscoreresearch.backup.ui.web.methods.service;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.underscoreresearch.backup.ui.web.BaseWrap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
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

import static com.underscoreresearch.backup.ui.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

/**
 * Endpoint for determining the best service region based on latency tests.
 * This class tests connectivity to various Wasabi S3 endpoints and determines
 * the region with the lowest latency for the current user.
 */
@Slf4j
public class BestRegionGet extends BaseWrap {
    private static final Map<String, List<URI>> REGIONS;
    private static final int ITERATIONS = 5;
    private static final ObjectWriter WRITER = MAPPER.writerFor(BestRegion.class);

    static {
        try {
            REGIONS = ImmutableMap.of(
                    "us-west", Lists.newArrayList(new URI("https://s3.us-west-1.wasabisys.com"), new URI("https://s3.us-east-1.wasabisys.com")),
                    "eu-central", Lists.newArrayList(new URI("https://s3.eu-central-2.wasabisys.com"), new URI("https://s3.eu-west-1.wasabisys.com")),
                    "ap-southeast", Lists.newArrayList(new URI("https://s3.ap-southeast-1.wasabisys.com"), new URI("https://s3.ap-northeast-1.wasabisys.com"))
            );
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Constructor for the BestRegionGet endpoint.
     */
    public BestRegionGet() {
        super(new Implementation());
    }

    /**
     * Determines the best region based on latency tests to various endpoints.
     * Tests each endpoint multiple times and calculates average latency.
     * 
     * @return The region code with the lowest average latency, or null if no region meets the criteria
     */
    static String determineBestRegion() {
        Map<String, AtomicLong> result = REGIONS.keySet().stream().map(urls -> (Maps.immutableEntry(urls,
                new AtomicLong()))).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<String, AtomicInteger> successCount = REGIONS.keySet().stream().map(urls -> (Maps.immutableEntry(urls,
                new AtomicInteger()))).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        try (ExecutorService pool = Executors.newFixedThreadPool(ITERATIONS
                * REGIONS.values().stream().map(List::size).reduce(0, Integer::sum))) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < ITERATIONS; i++) {
                for (Map.Entry<String, List<URI>> entry : REGIONS.entrySet()) {
                    final String region = entry.getKey();
                    for (URI endpoint : entry.getValue()) {
                        futures.add(pool.submit(() -> {
                            try {
                                Stopwatch timer = Stopwatch.createStarted();
                                HttpURLConnection con = (HttpURLConnection) endpoint.toURL().openConnection();
                                con.setConnectTimeout(3000);
                                con.setReadTimeout(3000);
                                con.setUseCaches(false);
                                con.setRequestMethod("OPTIONS");
                                if (con.getResponseCode() < 500) {
                                    result.get(region).addAndGet(timer.elapsed(TimeUnit.MILLISECONDS));
                                    successCount.get(region).incrementAndGet();
                                }
                            } catch (IOException ignored) {
                            }
                        }));
                    }
                }
            }
            futures.forEach(item -> {
                try {
                    item.get();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    log.error("Failed to test fetch", e);
                }
            });

            double bestLatency = 0;
            String bestRegion = null;
            for (Map.Entry<String, AtomicLong> entry : result.entrySet()) {
                double success = successCount.get(entry.getKey()).get();
                double latency = entry.getValue().get() / success;
                log.info("\"{}\" had average latency of {}ms and succeeded {} times", entry.getKey(), latency, success);
                if (success >= ITERATIONS * 0.75 && (bestRegion == null || bestLatency > latency)) {
                    bestRegion = entry.getKey();
                    bestLatency = latency;
                }
            }
            return bestRegion;
        }
    }

    /**
     * Data class representing the best region response.
     */
    @Data
    @AllArgsConstructor
    public static class BestRegion {
        private String region;
    }

    /**
     * Implementation of the Take interface that handles the HTTP request.
     */
    private static class Implementation implements Take {
        /**
         * Processes the HTTP request and returns the best region.
         * 
         * @param req The HTTP request
         * @return Response containing the best region or an error message
         */
        @Override
        public Response act(Request req) {
            try {
                return encryptResponse(req, WRITER.writeValueAsString(new BestRegion(determineBestRegion())));
            } catch (Throwable exc) {
                log.warn("Failed to determine best region", exc);
            }
            return messageJson(400, "Failed to determine best region");
        }
    }
}
