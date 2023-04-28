package com.underscoreresearch.backup.manifest.implementation;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.service.api.BackupApi;
import com.underscoreresearch.backup.service.api.invoker.ApiException;
import com.underscoreresearch.backup.service.api.model.FileListResponse;

class ServiceManagerImplTest {
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
            public boolean shouldRetryMissing(String region) {
                return true;
            }
        });
    }
}