package com.underscoreresearch.backup.cli.web;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import org.takes.HttpException;
import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rq.RqHref;
import org.takes.rs.RsWithBody;
import org.takes.tk.TkWrap;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Slf4j
public final class StrippedPrefixClasspath extends TkWrap {

    public StrippedPrefixClasspath(final String strip, final String add) {
        super(
                new Take() {
                    @Override
                    public Response act(final Request request) throws IOException {
                        String path = new RqHref.Base(request).href().path();
                        try {
                            if (path.startsWith(strip)) {
                                path = path.substring(strip.length());
                                if (path.equals(PATH_SEPARATOR)) {
                                    path = "";
                                }
                            }
                            path = add + path;
                            final InputStream input = this.getClass()
                                    .getResourceAsStream(path);
                            if (input == null) {
                                throw new HttpException(
                                        HttpURLConnection.HTTP_NOT_FOUND,
                                        String.format("File %s not found", path)
                                );
                            }
                            return new RsWithBody(input);
                        } catch (IOException | RuntimeException exc) {
                            log.warn("Failed to fetch {}", path, exc);
                            throw exc;
                        }
                    }
                }
        );
    }

}
