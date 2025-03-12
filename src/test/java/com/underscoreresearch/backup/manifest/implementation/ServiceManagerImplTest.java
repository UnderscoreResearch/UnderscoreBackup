package com.underscoreresearch.backup.manifest.implementation;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.service.api.BackupApi;
import com.underscoreresearch.backup.service.api.invoker.ApiException;
import com.underscoreresearch.backup.service.api.model.FileListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceManagerImplTest {
    @BeforeEach
    public void setup() {
        InstanceFactory.initialize(new String[]{"--no-log", "--password", "test", "--config-data",
                        "{}", "--encryption-key-data",
                        "{\"publicKey\":\"OXYESQETTP4X4NJVUR3HTTL4OAZLVYUIFTBOEZ5ZILMJOLU4YB4A\",\"salt\":\"M7KL5D46VLT2MFXLC67KIPIPIROH2GX4NT3YJVAWOF4XN6FMMTSA\"}"},
                null, null);
    }

    @Test
    public void testIOExceptionRetry() throws ApiException {
        BackupApi api = Mockito.mock(BackupApi.class);
        Mockito.when(api.listLogFiles("a", "b", "c"))
                .thenThrow(new ApiException(new IOException())).thenReturn(new FileListResponse());
        ServiceManagerImpl.callApi(api, "us-west", (client) -> client.listLogFiles("a", "b", "c"));
    }

    @Test
    public void test500ExceptionRetry() throws ApiException {
        BackupApi api = Mockito.mock(BackupApi.class);
        Mockito.when(api.listLogFiles("a", "b", "c"))
                .thenThrow(new ApiException(500, "Doh")).thenReturn(new FileListResponse());
        ServiceManagerImpl.callApi(api, "us-west", (client) -> client.listLogFiles("a", "b", "c"));
    }

    @Test
    public void test400ExceptionNonRetry() throws ApiException {
        BackupApi api = Mockito.mock(BackupApi.class);
        Mockito.when(api.listLogFiles("a", "b", "c"))
                .thenThrow(new ApiException(400, "Doh")).thenReturn(new FileListResponse());
        assertThrows(ApiException.class, () -> ServiceManagerImpl.callApi(api, "us-west", (client) -> client.listLogFiles("a", "b", "c")));
    }

    @Test
    public void test404ExceptionNonRetry() throws ApiException {
        BackupApi api = Mockito.mock(BackupApi.class);
        Mockito.when(api.listLogFiles("a", "b", "c"))
                .thenThrow(new ApiException(404, "Doh")).thenReturn(new FileListResponse());
        assertThrows(ApiException.class, () -> ServiceManagerImpl.callApi(api, "us-west", (client) -> client.listLogFiles("a", "b", "c")));
    }

    @Test
    public void test404ExceptionRetry() throws ApiException {
        BackupApi api = Mockito.mock(BackupApi.class);
        Mockito.when(api.listLogFiles("a", "b", "c"))
                .thenThrow(new ApiException(404, "Doh")).thenReturn(new FileListResponse());
        ServiceManagerImpl.callApi(api, "us-west", new ServiceManager.ApiFunction<FileListResponse>() {
            @Override
            public FileListResponse call(BackupApi client) throws ApiException {
                return client.listLogFiles("a", "b", "c");
            }

            @Override
            public boolean shouldRetryMissing(String region, ApiException apiException) {
                return true;
            }
        });
    }
}