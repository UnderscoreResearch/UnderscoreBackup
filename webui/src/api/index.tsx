import {DisplayMessage} from "../App";

function determineBaseApi(): string {
    if (window.location.pathname.startsWith("/fixed/")) {
        return "http://localhost:12345/fixed/api/";
    } else {
        let basePath = window.location.pathname;
        let ind = basePath.indexOf("/", 1);
        if (ind >= 0) {
            basePath = basePath.substring(0, ind + 1);
        }
        return window.location.protocol + "//" + location.host + basePath + "api/";
    }
}

const baseApi = determineBaseApi();

export interface BackupFilter {
    paths: string[],
    type: "INCLUDE" | "EXCLUDE",
    children?: BackupFilter[]
}

type BackupTimeUnit = "IMMEDIATE" | "SECONDS" | "MINUTES" | "HOURS" | "DAYS" | "WEEKS" | "MONTHS" | "YEARS" | "FOREVER";

export interface BackupTimespan {
    duration: number,
    unit: BackupTimeUnit
}

export type PropertyMap = {
    [key: string]: string
}

export interface BackupRetentionAdditional {
    validAfter: BackupTimespan,
    frequency?: BackupTimespan
}

export interface BackupRetention {
    retainDeleted?: BackupTimespan,
    maximumVersions?: number,
    defaultFrequency?: BackupTimespan,
    older?: BackupRetentionAdditional[]
}

export interface BackupSetRoot {
    path: string,
    filters?: BackupFilter[]
}

export interface BackupSet {
    id: string,
    roots: BackupSetRoot[],
    exclusions?: string[],
    schedule?: string,
    destinations: string[],
    retention?: BackupRetention,
    continuous?: boolean
}

export interface BackupFileSpecification {
    roots: BackupSetRoot[],
    exclusions?: string[]
}

export interface BackupLimits {
    maximumUploadBytesPerSecond?: number,
    maximumDownloadBytesPerSecond?: number
}

export interface BackupManifest {
    destination: string,
    maximumUnsyncedSize?: number,
    maximumUnsyncedSeconds?: number
    hideNotifications?: boolean,
    optimizeSchedule?: string,
    pauseOnBattery?: boolean,
    trimSchedule?: string,
    scheduleRandomize?: BackupTimespan,
    versionCheck?: boolean,
    configUser?: string
    configPassword?: string
    interactiveBackup?: boolean,
    initialSetup?: boolean,
}

export interface BackupDestination {
    type: string,
    encryption?: string,
    errorCorrection?: string,
    endpointUri: string,
    principal?: string,
    credential?: string,
    maxRetention?: BackupTimespan,
    properties?: PropertyMap,
    limits?: BackupLimits
}

export interface BackupShare {
    name: string,
    targetEmail?: string,
    destination: BackupDestination,
    contents: BackupFileSpecification
}

export interface BackupGlobalLimits {
    maximumUploadBytesPerSecond?: number,
    maximumDownloadBytesPerSecond?: number,
    maximumUploadThreads?: number,
    maximumDownloadThreads?: number
}

export type DestinationMap = {
    [key: string]: BackupDestination
}

export type ShareMap = {
    [key: string]: BackupShare
}

export interface BackupConfiguration {
    sets: BackupSet[],
    destinations: DestinationMap,
    additionalSources?: DestinationMap,
    manifest: BackupManifest,
    properties?: PropertyMap,
    limits?: BackupGlobalLimits,
    missingRetention?: BackupRetention,
    shares?: ShareMap
}

export interface BackupFile {
    added?: number,
    lastChanged?: number,
    length?: number,
    path: string
}

interface ReleaseFileItem {
    name: string,
    size: number,
    url: string
}

export interface ReleaseResponse {
    releaseDate: number;
    name: string;
    version: string;
    body: string;
    changeLog: string;
    download?: ReleaseFileItem;
}

export interface BackupState {
    pathSeparator: string,
    version: string,
    defaultRestoreFolder: string,
    defaultSet: BackupSet,
    serviceConnected: boolean,
    serviceSourceId?: string,
    activeSubscription: boolean,
    validDestinations: boolean,
    sourceName: string,
    source?: string,
    siteUrl: string,
    newVersion?: ReleaseResponse,
    repositoryReady: boolean
}

export interface StatusLine {
    reporter: string,
    code: string,
    message: string,
    value?: number,
    totalValue?: number,
    valueString?: string
}

export interface AdditionalKey {
    publicKey: string,
    privateKey: string
}

export interface AdditionalKeys {
    keys: AdditionalKey[]
}

export interface StatusResponse {
    status: StatusLine[]
}

export interface BackupRestoreRequest {
    password: string,
    destination?: string,
    files: BackupSetRoot[],
    overwrite: boolean,
    timestamp?: number,
    includeDeleted?: boolean
}

export interface ActiveShares {
    activeShares: string[],
    shareEncryptionNeeded: boolean
}

function reportError(errors: any) {
    console.log(errors);
    DisplayMessage(errors.toString());
}

export async function makeApiCall(api: string, init?: RequestInit, silentError?: boolean): Promise<any | undefined> {
    try {
        const response = await fetch(baseApi + api, init);
        if (!response.ok) {
            if (!silentError) {
                try {
                    if (response.status !== 404) {
                        let json = await response.json();
                        if (json.message) {
                            reportError(json.message);
                        } else {
                            reportError(response.statusText);
                        }
                    }
                } catch (error) {
                    reportError(response.statusText);
                }
            }
            return undefined;
        }
        return await response.json();
    } catch (error) {
        if (!silentError) {
            reportError(error);
        }
        return undefined;
    }
}

export async function getConfiguration(): Promise<BackupConfiguration | undefined> {
    return await makeApiCall("configuration");
}

export function getDefaultState(): BackupState {
    return {
        defaultRestoreFolder: "/",
        pathSeparator: "/",
        version: "",
        sourceName: "",
        siteUrl: "https://underscorebackup.com",
        serviceConnected: false,
        activeSubscription: false,
        validDestinations: false,
        defaultSet: {
            id: "home",
            roots: [],
            destinations: [],
            exclusions: []
        },
        repositoryReady: true
    };
}

export async function getState(): Promise<BackupState> {
    const defaults = await makeApiCall("state");
    if (!defaults) {
        return getDefaultState();
    }
    return defaults;
}

export async function getLocalFiles(path: string): Promise<BackupFile[] | undefined> {
    return await makeApiCall("local-files/" + encodeURIComponent(path));
}

export async function getBackupFiles(path: string, includeDeleted: boolean, timestamp?: Date): Promise<BackupFile[] | undefined> {
    let url = "backup-files/" + encodeURIComponent(path) + "?include-deleted=" + (includeDeleted ? "true" : "false");
    if (timestamp) {
        url += "&timestamp=" + timestamp.getTime();
    }
    return await makeApiCall(url);
}

export async function searchBackup(query: string, includeDeleted: boolean, timestamp?: Date): Promise<BackupFile[] | undefined> {
    let url = "search-backup?q=" + encodeURIComponent(query) + "&include-deleted=" + (includeDeleted ? "true" : "false");
    if (timestamp) {
        url += "&timestamp=" + timestamp.getTime();
    }
    return await makeApiCall(url);
}

export async function getBackupVersions(path: string): Promise<BackupFile[] | undefined> {
    let url = "backup-versions/" + encodeURIComponent(path);
    return await makeApiCall(url);
}

export async function getActivity(temporal: boolean): Promise<StatusLine[] | undefined> {
    const ret = await makeApiCall(
        "activity?temporal=" + (temporal ? "true" : "false"),
        undefined, true) as StatusResponse;
    if (ret) {
        return ret.status;
    }
    return undefined;
}

export async function rebuildAvailable(destination: string): Promise<boolean> {
    return !!(await makeApiCall("rebuild-available?destination=" + encodeURIComponent(destination)));
}

export async function createAuthEndpoint(): Promise<string | undefined> {
    const ret = await makeApiCall("auth-endpoint");
    if (ret) {
        return ret.endpoint;
    }
    return undefined;
}

function getBackupDownloadLink(file: string, added: number): string {
    const ind = file.lastIndexOf('/');
    const firstPathPart = file.substring(0, ind);
    const secondPath = file.substring(ind + 1);

    return baseApi + "backup-download/"
        + encodeURIComponent(firstPathPart) + "/" + encodeURIComponent(secondPath) + "?timestamp="
        + added;
}

export function downloadBackupFile(file: string, added: number, password: string) {
    const request = new XMLHttpRequest();

    request.responseType = "blob";
    request.open("post", getBackupDownloadLink(file, added), true);
    request.setRequestHeader('content-type', 'application/json;charset=UTF-8');
    request.send(JSON.stringify({
        "password": password
    }));

    request.onreadystatechange = function () {
        if (this.readyState == 4 && this.status == 200) {
            const imageURL = window.URL.createObjectURL(this.response);

            const anchor = document.createElement("a");
            anchor.href = imageURL;
            anchor.download = file;
            document.body.appendChild(anchor);
            anchor.click();
        }
    }
}

export async function getEncryptionKey(password?: string): Promise<boolean> {
    const ret = await makeApiCall("encryption-key", {
        method: 'POST',
        headers: {
            'content-type': 'application/json;charset=UTF-8',
        },
        body: JSON.stringify({
            "password": password
        })
    });

    if (ret === undefined) {
        return false;
    }
    return ret.isSpecified;
}

export async function selectSource(source: string, password?: string): Promise<string | undefined> {
    try {
        const response = await fetch(baseApi + "sources/" + encodeURIComponent(source), {
            method: 'POST',
            headers: {
                'content-type': 'application/json;charset=UTF-8',
            },
            body: JSON.stringify({
                "password": password
            })
        });
        if (!response.ok) {
            try {
                const json = await response.json();
                if (json.message) {
                    reportError(json.message);
                } else {
                    reportError(response.statusText);
                }
                if (response.status === 406)
                    return "destinations";
            } catch (error) {
                reportError(response.statusText);
            }
            return undefined;
        }
        return "restore";
    } catch (error) {
        reportError(error);
        return undefined;
    }
}

export async function createEncryptionKey(password: string): Promise<boolean> {
    const ret = await makeApiCall("encryption-key", {
        method: 'PUT',
        headers: {
            'content-type': 'application/json;charset=UTF-8',
        },
        body: JSON.stringify({
            "password": password
        })
    });

    return ret !== undefined;
}

export async function createAdditionalEncryptionKey(password: string, privateKey?: string): Promise<AdditionalKey | undefined> {
    return await makeApiCall("encryption-key/additional", {
        method: 'PUT',
        headers: {
            'content-type': 'application/json;charset=UTF-8',
        },
        body: JSON.stringify({
            "password": password,
            "privateKey": privateKey
        })
    });
}

export async function listAdditionalEncryptionKeys(password: string): Promise<AdditionalKeys | undefined> {
    return await makeApiCall("encryption-key/additional", {
        method: 'POST',
        headers: {
            'content-type': 'application/json;charset=UTF-8',
        },
        body: JSON.stringify({
            "password": password
        })
    });
}

export async function listActiveShares(): Promise<ActiveShares | undefined> {
    return await makeApiCall("shares", undefined, true);
}

export async function activateShares(password: string): Promise<AdditionalKeys | undefined> {
    return await makeApiCall("shares", {
        method: 'POST',
        headers: {
            'content-type': 'application/json;charset=UTF-8',
        },
        body: JSON.stringify({
            "password": password
        })
    });
}

export async function resetSettings(): Promise<boolean> {
    const ret = await makeApiCall("", {
        method: 'DELETE'
    });

    return ret !== undefined;
}

export async function changeEncryptionKey(password: string, newPassword: string): Promise<boolean> {
    const ret = await makeApiCall("encryption-key/change", {
        method: 'POST',
        headers: {
            'content-type': 'application/json;charset=UTF-8',
        },
        body: JSON.stringify({
            "password": password,
            "newPassword": newPassword
        })
    });

    return ret !== undefined;
}

export async function postConfiguration(config: BackupConfiguration): Promise<boolean> {
    const ret = await makeApiCall("configuration", {
        method: 'POST',
        headers: {
            'content-type': 'application/json;charset=UTF-8',
        },
        body: JSON.stringify(config, null, 2)
    });

    if (ret === undefined) {
        return false;
    }
    return ret;
}

export async function startRemoteRestore(password: string): Promise<boolean> {
    const ret = await makeApiCall("remote-configuration/rebuild", {
        method: 'POST',
        headers: {
            'content-type': 'application/json;charset=UTF-8',
        },
        body: JSON.stringify({
            password: password
        })
    });

    if (ret === undefined) {
        return false;
    }
    return ret;
}

export async function restartSets(sets?: string[]): Promise<boolean> {
    const ret = await makeApiCall("sets/restart", {
        method: 'POST',
        headers: {
            'content-type': 'application/json;charset=UTF-8',
        },
        body: JSON.stringify({
            sets: sets
        })
    });

    if (ret === undefined) {
        return false;
    }
    return ret;
}

export async function initiateRestore(request: BackupRestoreRequest): Promise<boolean> {
    const ret = await makeApiCall("restore", {
        method: 'POST',
        headers: {
            'content-type': 'application/json;charset=UTF-8',
        },
        body: JSON.stringify(request)
    });

    if (ret === undefined) {
        return false;
    }
    return ret;
}