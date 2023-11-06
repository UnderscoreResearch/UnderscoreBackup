import {DisplayMessage} from "../App";
import {generateKeyPair, sharedKey} from "curve25519-js";
import base64url from "base64url";
import argon2 from "./argon2";
import {base32} from "rfc4648";
import aesjs from "aes-js";
import crypto from "crypto";

// Bunch of API interfaces.

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
    additionalDestinations?: string[],
    maximumUnsyncedSize?: number,
    maximumUnsyncedSeconds?: number
    hideNotifications?: boolean,
    optimizeSchedule?: string,
    pauseOnBattery?: boolean,
    trimSchedule?: string,
    scheduleRandomize?: BackupTimespan,
    versionCheck?: boolean,
    automaticUpgrade?: boolean,
    authenticationRequired?: boolean,
    interactiveBackup?: boolean,
    initialSetup?: boolean,
    ignorePermissions?: boolean
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
    deleted?: number,
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
    includeDeleted?: boolean,
    skipPermissions?: boolean
}

export interface ActiveShares {
    activeShares: string[],
    shareEncryptionNeeded: boolean
}

function reportError(errors: any, err?: (error: string) => boolean) {
    if (err && err(errors)) {
        return;
    }
    console.log(errors);
    DisplayMessage(errors.toString());
}

// From down here is the part that deals with basic calling, authentication and encryption of the API.

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

function determineBasePath(): string {
    if (window.location.pathname.startsWith("/fixed/")) {
        return "/fixed/api/";
    } else {
        let basePath = window.location.pathname;
        let ind = basePath.indexOf("/", 1);
        if (ind >= 0) {
            basePath = basePath.substring(0, ind + 1);
        }
        return basePath + "api/";
    }
}

const baseApi = determineBaseApi();
const basePrefix = determineBasePath();

const exchangeKeyPair = generateKeyPair(crypto.randomBytes(32));
const publicKey = base64url.encode(Buffer.from(exchangeKeyPair.public)).replace("=", "");
let apiSharedKey: string | undefined = undefined;
let apiSharedKeyBytes: Uint8Array | undefined = undefined;
let keySalt: string | undefined = undefined;
let keyData: string | undefined = undefined;
let encryptionPublicKey: string | undefined = undefined;
let hasSuccessfulAuth = false;
let authAwaits: (() => void)[] = [];
let nonce = 1;

export async function fetchAuth() {
    const response = await fetch(baseApi + "auth", {
        method: 'POST',
        headers: {
            'content-type': 'application/json;charset=UTF-8',
        },
        body: JSON.stringify({
            "publicKey": publicKey
        })
    });
    if (!response.ok) {
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
        return undefined;
    }

    const json = await response.json();
    const serverApiKey = Buffer.from(json.publicKey, "base64");
    apiSharedKeyBytes = Buffer.from(sharedKey(exchangeKeyPair.private, serverApiKey));
    apiSharedKey = base32.stringify(apiSharedKeyBytes, {pad: false});

    // If you get a keySalt, you need to ask the user for a password and use it and potential keyData
    // to derive the public encryption key from the private key which you must use that to also sign all API requests.

    if (json.keySalt) {
        keySalt = json.keySalt;
        keyData = json.keyData;
    }
}

function postAwaits() {
    const oldAwait = authAwaits;
    authAwaits = [];
    oldAwait.forEach((awaiter) => awaiter());
}

fetchAuth().then(() => {
    postAwaits();
});

/**
 * This method is used to wait for the authentication to complete so we only call auth once even though there
 * might be several API calls that are waiting for the authentication to complete.
 * @param wait Should you wait or just return true or false immediately (Used by activity polling for instance)
 */
async function authCompleted(wait: boolean): Promise<boolean> {
    if (apiSharedKey) {
        return true;
    }
    if (!wait) {
        return false;
    }
    await new Promise<void>((resolve) => {
        authAwaits.push(resolve);
    });
    return true;
}

export async function submitPrivateKeyPassword(password: string) {
    if (keySalt) {
        let argonData = await argon2.hash({
            pass: password,
            salt: base32.parse(keySalt, {loose: true}),
            time: 64,
            mem: 8192,
            hashLen: 32,
            parallelism: 2,
            type: argon2.ArgonType.Argon2i
        })

        let data;
        if (keyData) {
            const keyDataBytes = base32.parse(keyData, {loose: true});
            const ret = new Uint8Array(argonData.hash);

            for (let i = 0; i < ret.length; i++) {
                ret[i] = (argonData.hash[i] ^ keyDataBytes[i]);
            }

            data = ret;
        } else {
            data = argonData.hash;
        }

        data[0] = (data[0] | 7);
        data[31] = (data[31] & 63);
        data[31] = (data[31] | 128);

        const encryptionKeySecret = generateKeyPair(data);
        encryptionPublicKey = base32.stringify(Buffer.from(encryptionKeySecret.public), {pad: false});
    } else {
        encryptionPublicKey = undefined;
    }
    postAwaits();
}

export async function needPrivateKeyPassword(wait: boolean): Promise<boolean> {
    const completedAuth = await authCompleted(wait);
    return !completedAuth || (!!keySalt && !encryptionPublicKey);
}

function calculateSHA256(data: Buffer) {
    const digest = crypto.createHash('sha256').update(data).digest();
    return base64url(digest).replace("=", "");
}

function createSignedHash(method: string, api: string, sharedKey: string, currentNonce: number) {
    const auth = method + ":" + basePrefix + api + ":" + sharedKey + ":" + currentNonce;
    return calculateSHA256(Buffer.from(auth, 'utf-8'));
}

function generateAuthHeader(method: string, api: string) {
    const currentNonce = ++nonce
    const key = createSignedHash(method, api, apiSharedKey as string, currentNonce);
    if (encryptionPublicKey) {
        const encryptionKey = createSignedHash(method, api, encryptionPublicKey as string, currentNonce);
        return `${publicKey} ${currentNonce} ${key} ${encryptionKey}`;
    } else {
        return `${publicKey} ${currentNonce} ${key}`;
    }
}

function decryptData(data: ArrayBuffer, expectedHash: string) {
    const iv = new Uint8Array(data.slice(0, 16));
    const encrypted = new Uint8Array(data.slice(16));

    var aesCbc = new aesjs.ModeOfOperation.cbc(apiSharedKeyBytes as Uint8Array, iv);
    const ret = aesjs.padding.pkcs7.strip(aesCbc.decrypt(encrypted));
    const hash = calculateSHA256(Buffer.from(ret))
    if (expectedHash !== hash) {
        throw new Error("Invalid response hash");
    }
    return ret;
}

function encryptData(data: string) {
    const iv = crypto.randomBytes(16);
    const byteData = Buffer.from(data, 'utf8');
    var aesCbc = new aesjs.ModeOfOperation.cbc(apiSharedKeyBytes as Uint8Array, iv);
    const encrypted = aesCbc.encrypt(aesjs.padding.pkcs7.pad(byteData));
    return {
        data: Buffer.concat([iv, encrypted]),
        hash: calculateSHA256(byteData),
    };
}

export async function performFetch(api: string, init?: RequestInit, silentError?: boolean) {
    if (await needPrivateKeyPassword(!silentError)) {
        return null;
    }

    const useInit: RequestInit =
        {
            ...init
        };

    let exchangeHeader = generateAuthHeader((init && init.method ? init.method : "GET"), api);

    useInit.headers = {
        ...useInit?.headers,
        "x-keyexchange": exchangeHeader,
        "Content-Type": "x-application/encrypted-json"
    };

    if (useInit.body) {
        const data = encryptData(useInit.body as string);
        useInit.body = data.data;
        useInit.headers = {
            ...useInit.headers,
            "x-payload-hash": data.hash
        }
    }

    const response = await fetch(baseApi + api, useInit);
    if (!response.ok) {
        if (response.status === 401) {
            apiSharedKey = undefined;
            fetchAuth().then(() => {
                postAwaits();
            });
            if (encryptionPublicKey) {
                encryptionPublicKey = undefined;
                if (!hasSuccessfulAuth)
                    reportError("Invalid password");
            }
            hasSuccessfulAuth = false;
            return null;
        }
    } else {
        hasSuccessfulAuth = true;
    }

    return response;
}

/**
 * Make an API call with encryption and auth.
 * @param api The API to call.
 * @param init Any API information.
 * @param silentError
 * @param err
 * @returns The decrypted JSON of the result or undefined on an error or null if the auth is not ready.
 */
export async function makeApiCall(api: string, init?: RequestInit, silentError?: boolean,
                                  err?: (error: string) => boolean): Promise<any | undefined | null> {
    try {
        const response = await performFetch(api, init, silentError);
        if (!response) {
            return response;
        }
        if (!response.ok) {
            if (!silentError) {
                try {
                    if (response.status !== 404) {
                        let json = await response.json();
                        if (json.message) {
                            reportError(json.message, err);
                        } else {
                            reportError(response.statusText, err);
                        }
                    }
                } catch (error) {
                    reportError(response.statusText, err);
                }
            }
            return undefined;
        }

        if (response.headers.get("Content-Type") === "x-application/encrypted-json") {
            const data = await response.arrayBuffer();
            const decryptedData = decryptData(data, response.headers.get("x-payload-hash") as string);
            return JSON.parse(Buffer.from(decryptedData).toString('utf8'));
        }

        // Only purely informational messages are allowed to be passed in clear text.
        const data = await response.json();
        if (!data.message || Object.keys(data).length > 1) {
            reportError(new Error("Expected encrypted payload"), err);
        } else {
            return data;
        }
    } catch (error) {
        if (!silentError) {
            reportError(error, err);
        }
    }
    return undefined;
}

// Below are the actual API call methods.

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

export async function deleteBackupFiles(path: string): Promise<BackupFile[] | undefined> {
    let url = "backup-files/" + encodeURIComponent(path);
    return await makeApiCall(url, {
        method: 'DELETE'
    });
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

export async function getActivity(temporal: boolean): Promise<StatusLine[] | undefined | null> {
    const ret = await makeApiCall(
        "activity?temporal=" + (temporal ? "true" : "false"),
        undefined, true) as StatusResponse;
    if (ret) {
        return ret.status;
    }
    return ret;
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
    request.setRequestHeader('content-type', 'x-application/encrypted-json');
    request.setRequestHeader('x-keyexchange', generateAuthHeader("POST",
        "backup-download/" + encodeURIComponent(file) + "/" + encodeURIComponent(added.toString())));
    const data = encryptData(JSON.stringify({
        "password": password
    }));
    request.setRequestHeader("x-payload-hash", data.hash);
    request.send(data.data);

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
        body: JSON.stringify({
            "password": password
        })
    });

    if (ret === undefined) {
        return false;
    }
    return ret.isSpecified;
}

export async function selectSource(source: string, password?: string): Promise<string | undefined | null> {
    try {
        const response = await performFetch("sources/" + encodeURIComponent(source), {
            method: 'POST',
            body: JSON.stringify({
                "password": password,
            })
        });

        if (!response) {
            return response;
        }

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
        body: JSON.stringify({
            "password": password
        })
    });

    return ret !== undefined;
}

export async function createAdditionalEncryptionKey(password: string, privateKey?: string): Promise<AdditionalKey | undefined> {
    return await makeApiCall("encryption-key/additional", {
        method: 'PUT',
        body: JSON.stringify({
            "password": password,
            "privateKey": privateKey
        })
    });
}

export async function listAdditionalEncryptionKeys(password: string): Promise<AdditionalKeys | undefined> {
    return await makeApiCall("encryption-key/additional", {
        method: 'POST',
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
        body: JSON.stringify({
            "password": password
        })
    });
}

export async function repairRepository(password: string): Promise<AdditionalKeys | undefined> {
    return await makeApiCall("repair", {
        method: 'POST',
        body: JSON.stringify({
            "password": password
        })
    });
}

export async function trimRepository(): Promise<AdditionalKeys | undefined> {
    return await makeApiCall("trim", {
        method: 'POST',
        body: JSON.stringify({})
    });
}

export async function optimizeRepository(): Promise<AdditionalKeys | undefined> {
    return await makeApiCall("optimize", {
        method: 'POST',
        body: JSON.stringify({})
    });
}

export async function defragRepository(): Promise<AdditionalKeys | undefined> {
    return await makeApiCall("defrag", {
        method: 'POST',
        body: JSON.stringify({})
    });
}

export async function validateBlocks(): Promise<AdditionalKeys | undefined> {
    return await makeApiCall("validate-blocks", {
        method: 'POST',
        body: JSON.stringify({})
    });
}

export async function resetSettings(): Promise<boolean> {
    const ret = await makeApiCall("", {
        method: 'DELETE'
    });

    return ret !== undefined;
}

export async function changeEncryptionKey(password: string, newPassword: string,
                                          regeneratePrivateKey: boolean): Promise<boolean> {
    const ret = await makeApiCall("encryption-key/change", {
        method: 'POST',
        body: JSON.stringify({
            "password": password,
            "newPassword": newPassword,
            "regeneratePrivateKey": regeneratePrivateKey
        })
    });

    return ret !== undefined;
}

export async function postConfiguration(config: BackupConfiguration): Promise<boolean> {
    const ret = await makeApiCall("configuration", {
        method: 'POST',
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
        body: JSON.stringify(request)
    });

    if (ret === undefined) {
        return false;
    }
    return ret;
}