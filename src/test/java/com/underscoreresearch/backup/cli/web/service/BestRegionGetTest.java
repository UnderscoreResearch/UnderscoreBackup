package com.underscoreresearch.backup.cli.web.service;

import static com.underscoreresearch.backup.cli.web.service.BestRegionGet.determineBestRegion;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import lombok.extern.slf4j.Slf4j;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

@Slf4j
class BestRegionGetTest {

    @Test
    void determineBestRegionTest() {
        String str = determineBestRegion();
        String region = System.getenv("AWS_DEFAULT_REGION");
        assertNotNull(str);
        if (region != null) {
            assertThat(region, Matchers.startsWith(str));
        }
    }
}