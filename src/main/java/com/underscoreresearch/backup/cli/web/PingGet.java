package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.configuration.CommandLineModule.CONFIG_FILE_LOCATION;
import static com.underscoreresearch.backup.configuration.CommandLineModule.KEY_FILE_NAME;
import static com.underscoreresearch.backup.configuration.CommandLineModule.MANIFEST_LOCATION;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

import java.io.File;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.takes.Request;
import org.takes.Response;
import org.takes.rs.RsWithHeaders;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.Lists;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.utils.ActivityAppender;

@Slf4j
public class PingGet extends JsonWrap {
    public PingGet() {
        super(new Implementation());
    }

    private static class Implementation extends BaseImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {
            return new RsWithHeaders(messageJson(200, "Ok"), getCorsHeaders());
        }
    }

    public static String[] getCorsHeaders() {
        return new String[] {
                "Access-Control-Allow-Origin: " + getSiteUrl(),
                "Access-Control-Allow-Methods: GET, OPTIONS",
        };
    }

    public static String getSiteUrl() {
        return ("true".equals(System.getenv("BACKUP_DEV")) ? "https://dev.underscorebackup.com" : "https://underscorebackup.com");
    }
}
