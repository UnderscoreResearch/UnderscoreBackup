package com.underscoreresearch.backup.ui.web.methods.service;

import lombok.extern.slf4j.Slf4j;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


import static com.underscoreresearch.backup.ui.web.methods.service.BestRegionGet.determineBestRegion;

@Slf4j
class BestRegionGetTest {

    @Test
    void determineBestRegionTest() {
        String str = determineBestRegion();
        String region = System.getenv("AWS_DEFAULT_REGION");
        Assertions.assertNotNull(str);
        if (region != null) {
            MatcherAssert.assertThat(region, Matchers.startsWith(str));
        }
    }
}