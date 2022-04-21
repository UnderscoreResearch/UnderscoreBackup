import {DisplayError} from "../App";

function determineBaseApi() : string {
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

type BackupTimeUnit = "SECONDS" | "MINUTES" | "HOURS" | "DAYS" | "WEEKS" | "MONTHS" | "YEARS";

export interface BackupTimespan {
    duration: number,
    unit: BackupTimeUnit
}

export type PropertyMap = {
    [key : string] : string
}

export interface BackupRetentionAdditional {
    validAfter: BackupTimespan,
    frequency?: BackupTimespan
}

export interface BackupRetention {
    retainDeleted?: BackupTimespan,
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
    exclusions: string[],
    schedule?: string,
    destinations: string[],
    retention?: BackupRetention
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
    retention?: number,
    properties?: PropertyMap,
    limits?: BackupLimits
}

export interface BackupGlobalLimits {
    maximumUploadBytesPerSecond?: number,
    maximumDownloadBytesPerSecond?: number,
    maximumUploadThreads?: number,
    maximumDownloadThreads?: number
}

export type DestinationMap = {
    [key : string] : BackupDestination
}

export interface BackupConfiguration {
    sets: BackupSet[],
    destinations: DestinationMap
    manifest: BackupManifest,
    properties?: PropertyMap,
    limits?: BackupGlobalLimits
}

export interface BackupFile {
    added?: number,
    lastChanged?: number,
    length?: number,
    path: string
}

export interface BackupDefaults {
    set: BackupSet,
    pathSeparator: string,
    defaultRestoreFolder: string
}

export interface StatusLine {
    reporter: string,
    code: string,
    message: string,
    value?: number,
    valueString?: string
}

export interface StatusResponse {
    status: StatusLine[]
}

export interface BackupRestoreRequest {
    passphrase: string,
    destination?: string,
    files: BackupSetRoot[],
    overwrite: boolean,
    timestamp?: number
}

function reportError(errors: any) {
    console.log(errors);
    DisplayError(errors.toString());
}

async function MakeCall(api: string, init?: RequestInit) : Promise<any | undefined> {
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

export async function GetConfiguration() : Promise<BackupConfiguration | undefined> {
    return await MakeCall("configuration");
}

export async function GetDefaults() : Promise<BackupDefaults> {
    const defaults = await MakeCall("defaults");
    if (!defaults) {
        return {
            defaultRestoreFolder: "/",
            pathSeparator: "/",
            set: {
                id: "home",
                roots: [],
                destinations: [],
                exclusions: []
            }
        };
    }
    return defaults;
}

export async function GetRemoteConfiguration() : Promise<BackupConfiguration | undefined> {
    return await MakeCall("remote-configuration");
}

export async function GetLocalFiles(path: string) : Promise<BackupFile[] | undefined> {
    return await MakeCall("local-files/" + encodeURIComponent(path));
}

export async function GetBackupFiles(path: string, timestamp?: Date) : Promise<BackupFile[] | undefined> {
    let url = "backup-files/" + encodeURIComponent(path);
    if (timestamp) {
        url += "?timestamp=" + timestamp.getTime();
    }
    return await MakeCall(url);
}

export async function GetActivity(temporal: boolean) : Promise<StatusLine[] | undefined> {
    const ret = await MakeCall("activity?temporal=" + (temporal ? "true" : "false")) as StatusResponse;
    if (ret) {
        return ret.status;
    }
    return undefined;
}

export async function GetDestinationFiles(path: string, destination: string) : Promise<BackupFile[] | undefined> {
    return await MakeCall("destination-files/" + encodeURIComponent(path) + "?destination=" + encodeURIComponent(destination));
}

export async function GetAuthEndpoint() : Promise<string | undefined> {
    const ret = await MakeCall("auth-endpoint");
    if (ret) {
        return ret.endpoint;
    }
    return undefined;
}

export async function GetEncryptionKey(passphrase: string) : Promise<boolean> {
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

export function GetFileDownloadUrl(file: BackupFile) : string {
    return baseApi + "download" + file.path;
}

export async function PutEncryptionKey(passphrase: string) : Promise<boolean> {
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

export async function PostConfiguration(config : BackupConfiguration) : Promise<boolean> {
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

export async function PostRemoteRestore(passphrase : string) : Promise<boolean> {
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

export async function PostRestartSets(sets?: string[]) : Promise<boolean> {
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

export async function PostRestore(request: BackupRestoreRequest) : Promise<boolean> {
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