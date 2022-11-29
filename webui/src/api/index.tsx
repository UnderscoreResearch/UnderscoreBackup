import {DisplayMessage} from "../App";

function determineBaseApi(): string {
    if (window.location.pathname.startsWith("/fixed/")) {
        return "http://localhost:12345/fixed/api/";
    } else {
        var basePath = window.location.pathname;
        var ind = basePath.indexOf("/", 1);
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

type BackupTimeUnit = "SECONDS" | "MINUTES" | "HOURS" | "DAYS" | "WEEKS" | "MONTHS" | "YEARS" | "FOREVER";

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
    retention?: BackupRetention
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
    localLocation?: string,
    maximumUnsyncedSize?: number,
    maximumUnsyncedSeconds?: number
    hideNotifications?: boolean,
    optimizeSchedule?: string,
    pauseOnBattery?: boolean,
    trimSchedule?: string,
    configUser?: string
    configPassword?: string
    interactiveBackup?: boolean
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

export interface BackupState {
    pathSeparator: string,
    version: string,
    defaultRestoreFolder: string,
    defaultSet: BackupSet,
    validDestinations: boolean,
    source?: string
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
    passphrase: string,
    destination?: string,
    files: BackupSetRoot[],
    overwrite: boolean,
    timestamp?: number,
    includeDeleted?: boolean
}

export interface ActiveShares {
    activeShares: string[]
}

function reportError(errors: any) {
    console.log(errors);
    DisplayMessage(errors.toString());
}

async function MakeCall(api: string, init?: RequestInit): Promise<any | undefined> {
    try {
        const response = await fetch(baseApi + api, init);
        if (!response.ok) {
            try {
                if (response.status !== 404) {
                    var json = await response.json();
                    if (json.message) {
                        reportError(json.message);
                    } else {
                        reportError(response.statusText);
                    }
                }
            } catch (error) {
                reportError(response.statusText);
            }
            return undefined;
        }
        return await response.json();
    } catch (error) {
        reportError(error);
        return undefined;
    }
}

export async function GetConfiguration(): Promise<BackupConfiguration | undefined> {
    return await MakeCall("configuration");
}

export async function GetState(): Promise<BackupState> {
    const defaults = await MakeCall("state");
    if (!defaults) {
        return {
            defaultRestoreFolder: "/",
            pathSeparator: "/",
            version: "",
            validDestinations: false,
            defaultSet: {
                id: "home",
                roots: [],
                destinations: [],
                exclusions: []
            }
        };
    }
    return defaults;
}

export async function GetLocalFiles(path: string): Promise<BackupFile[] | undefined> {
    return await MakeCall("local-files/" + encodeURIComponent(path));
}

export async function GetBackupFiles(path: string, includeDeleted: boolean, timestamp?: Date): Promise<BackupFile[] | undefined> {
    let url = "backup-files/" + encodeURIComponent(path) + "?include-deleted=" + (includeDeleted ? "true" : "false");
    if (timestamp) {
        url += "&timestamp=" + timestamp.getTime();
    }
    return await MakeCall(url);
}

export async function GetSearchBackup(query: string, includeDeleted: boolean, timestamp?: Date): Promise<BackupFile[] | undefined> {
    let url = "search-backup?q=" + encodeURIComponent(query) + "&include-deleted=" + (includeDeleted ? "true" : "false");
    if (timestamp) {
        url += "&timestamp=" + timestamp.getTime();
    }
    return await MakeCall(url);
}

export async function GetBackupVersions(path: string): Promise<BackupFile[] | undefined> {
    let url = "backup-versions/" + encodeURIComponent(path);
    return await MakeCall(url);
}

export async function GetActivity(temporal: boolean): Promise<StatusLine[] | undefined> {
    const ret = await MakeCall("activity?temporal=" + (temporal ? "true" : "false")) as StatusResponse;
    if (ret) {
        return ret.status;
    }
    return undefined;
}

export async function GetDestinationFiles(path: string, destination: string): Promise<BackupFile[] | undefined> {
    return await MakeCall("destination-files/" + encodeURIComponent(path) + "?destination=" + encodeURIComponent(destination));
}

export async function GetAuthEndpoint(): Promise<string | undefined> {
    const ret = await MakeCall("auth-endpoint");
    if (ret) {
        return ret.endpoint;
    }
    return undefined;
}

function GetBackupDownloadLink(file: string, added: number): string {
    const ind = file.lastIndexOf('/');
    const firstPathPart = file.substring(0, ind);
    const secondPath = file.substring(ind + 1);

    return baseApi + "backup-download/"
        + encodeURIComponent(firstPathPart) + "/" + encodeURIComponent(secondPath) + "?timestamp="
        + added;
}

export function DownloadBackupFile(file: string, added: number, passphrase: string) {
    const request = new XMLHttpRequest();

    request.responseType = "blob";
    request.open("post", GetBackupDownloadLink(file, added), true);
    request.setRequestHeader('content-type', 'application/json;charset=UTF-8');
    request.send(JSON.stringify({
        "passphrase": passphrase
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

export async function GetEncryptionKey(passphrase?: string): Promise<boolean> {
    const ret = await MakeCall("encryption-key", {
        method: 'POST',
        headers: {
            'content-type': 'application/json;charset=UTF-8',
        },
        body: JSON.stringify({
            "passphrase": passphrase
        })
    });

    if (ret === undefined) {
        return false;
    }
    return ret.specified;
}

export async function PostSelectSource(source: string, passphrase?: string): Promise<string | undefined> {
    try {
        const response = await fetch(baseApi + "sources/" + encodeURIComponent(source), {
            method: 'POST',
            headers: {
                'content-type': 'application/json;charset=UTF-8',
            },
            body: JSON.stringify({
                "passphrase": passphrase
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

export async function PutEncryptionKey(passphrase: string): Promise<boolean> {
    const ret = await MakeCall("encryption-key", {
        method: 'PUT',
        headers: {
            'content-type': 'application/json;charset=UTF-8',
        },
        body: JSON.stringify({
            "passphrase": passphrase
        })
    });

    if (ret === undefined) {
        return false;
    }
    return true;
}

export async function PutAdditionalEncryptionKey(passphrase: string, privateKey?: string): Promise<AdditionalKey | undefined> {
    return await MakeCall("encryption-key/additional", {
        method: 'PUT',
        headers: {
            'content-type': 'application/json;charset=UTF-8',
        },
        body: JSON.stringify({
            "passphrase": passphrase,
            "privateKey": privateKey
        })
    });
}

export async function PostAdditionalEncryptionKeys(passphrase: string): Promise<AdditionalKeys | undefined> {
    return await MakeCall("encryption-key/additional", {
        method: 'POST',
        headers: {
            'content-type': 'application/json;charset=UTF-8',
        },
        body: JSON.stringify({
            "passphrase": passphrase
        })
    });
}

export async function GetActiveShares(): Promise<ActiveShares | undefined> {
    return await MakeCall("shares");
}

export async function PostActivateShares(passphrase: string): Promise<AdditionalKeys | undefined> {
    return await MakeCall("shares", {
        method: 'POST',
        headers: {
            'content-type': 'application/json;charset=UTF-8',
        },
        body: JSON.stringify({
            "passphrase": passphrase
        })
    });
}

export async function DeleteReset(): Promise<boolean> {
    const ret = await MakeCall("", {
        method: 'DELETE'
    });

    if (ret === undefined) {
        return false;
    }
    return true;
}

export async function PostChangeEncryptionKey(passphrase: string, newPassphrase: string): Promise<boolean> {
    const ret = await MakeCall("encryption-key/change", {
        method: 'POST',
        headers: {
            'content-type': 'application/json;charset=UTF-8',
        },
        body: JSON.stringify({
            "passphrase": passphrase,
            "newPassphrase": newPassphrase
        })
    });

    if (ret === undefined) {
        return false;
    }
    return true;
}

export async function PostConfiguration(config: BackupConfiguration): Promise<boolean> {
    const ret = await MakeCall("configuration", {
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

export async function PostRemoteRestore(passphrase: string): Promise<boolean> {
    const ret = await MakeCall("remote-configuration/rebuild", {
        method: 'POST',
        headers: {
            'content-type': 'application/json;charset=UTF-8',
        },
        body: JSON.stringify({
            passphrase: passphrase
        })
    });

    if (ret === undefined) {
        return false;
    }
    return ret;
}

export async function PostRestartSets(sets?: string[]): Promise<boolean> {
    const ret = await MakeCall("sets/restart", {
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

export async function PostRestore(request: BackupRestoreRequest): Promise<boolean> {
    const ret = await MakeCall("restore", {
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