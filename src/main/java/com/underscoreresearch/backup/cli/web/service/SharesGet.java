package com.underscoreresearch.backup.cli.web.service;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.underscoreresearch.backup.cli.web.BaseImplementation;
import com.underscoreresearch.backup.cli.web.BaseWrap;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.encryption.EncryptionIdentity;
import com.underscoreresearch.backup.manifest.ServiceManager;
import com.underscoreresearch.backup.service.api.model.ShareResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.takes.Request;
import org.takes.Response;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static com.underscoreresearch.backup.cli.web.PsAuthedContent.encryptResponse;
import static com.underscoreresearch.backup.cli.web.service.CreateSecretPut.encryptionIdentity;
import static com.underscoreresearch.backup.manifest.implementation.ServiceManagerImpl.sendApiFailureOn;
import static com.underscoreresearch.backup.utils.SerializationUtils.MAPPER;

public class SharesGet extends BaseWrap {
    private static final ObjectWriter WRITER = MAPPER.writerFor(ListSharesResponse.class);

    public SharesGet() {
        super(new Implementation());
    }

    @Data
    @AllArgsConstructor
    private static class ListSharesResponse {
        private List<PublicShareResponse> shares;
    }

    @Data
    @AllArgsConstructor
    private static class PublicShareResponse {
        private String shareId;
        private String name;
    }

    private static class Implementation extends BaseImplementation {
        @Override
        public Response actualAct(Request req) throws Exception {

            try {
                EncryptionIdentity key = encryptionIdentity();
                ServiceManager serviceManager = InstanceFactory.getInstance(ServiceManager.class);
                final List<ShareResponse> shares = serviceManager.getShares();
                ListSharesResponse ret = new ListSharesResponse(shares.stream()
                        .filter((share) -> share.getPrivateKeys().stream()
                                .anyMatch((privateKey) -> privateKey.getPublicKey().equals(key.getSharingKeys().getPublicKeyString()) || privateKey.getPublicKey().equals(key.getSharingKeys().getKeyIdentifier())))
                        .map(share -> new PublicShareResponse(share.getSourceId() + "." + share.getShareId(), share.getName()))
                        .collect(Collectors.toList()));
                return encryptResponse(req, WRITER.writeValueAsString(ret));
            } catch (IOException exc) {
                return sendApiFailureOn(exc);
            }
        }
    }
}
