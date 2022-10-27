package com.underscoreresearch.backup.manifest;

import java.io.IOException;

public interface ShareManifestManager extends BaseManifestManager {

    void completeActivation() throws IOException;

    void addUsedDestinations(String destination) throws IOException;
}