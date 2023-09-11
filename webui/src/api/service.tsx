import {AdditionalKeys, makeApiCall} from "./index";

export interface UsageEntry {
    time: number,
    usage: number
}

export interface SourceResponse {
    sourceId: string,
    name: string,
    created: number,
    identity: string,
    destination?: string,
    encryptionMode?: string,
    key?: string,
    secretRegion?: string,
    lastUsage?: number,
    dailyUsage: UsageEntry[],
    periodUsage: UsageEntry[],
}

export interface ShareResponse {
    shareId: string,
    name: string
}

export interface SupportBundleRequest {
    includeLogs: boolean,
    includeConfig: boolean,
    includeMetadata: boolean,
    includeKey: boolean
}

export interface ListSourcesResponse {
    sources: SourceResponse[];
}

export interface ListSharesResponse {
    shares: ShareResponse[];
}

export async function generateToken(code: string, codeVerifier: string, sourceName?: string): Promise<string | undefined> {
    const ret = await makeApiCall("service/token", {
        method: 'POST',
        body: JSON.stringify({
            code: code,
            codeVerifier: codeVerifier,
            sourceName: sourceName
        })
    });

    if (ret === undefined)
        return undefined;

    return ret.token;
}

export async function deleteToken(): Promise<boolean> {
    const ret = await makeApiCall("service/token", {
        method: 'DELETE'
    });

    return ret !== undefined;
}

export async function listSources(excludeSelf: boolean): Promise<ListSourcesResponse> {
    return await makeApiCall("service/sources?excludeSelf=" + (excludeSelf ? "true" : "false"), {
        method: 'GET'
    });
}

export async function getSource(): Promise<ListSourcesResponse> {
    return await makeApiCall("service/sources?onlySelf=true", {
        method: 'GET'
    });
}

export async function listShares(): Promise<ListSharesResponse> {
    return await makeApiCall("service/shares", {
        method: 'GET'
    });
}

export async function createSource(name: string): Promise<SourceResponse> {
    return await makeApiCall("service/sources", {
        method: 'POST',
        body: JSON.stringify({
            name: name
        })
    });
}

export async function createSupportBundle(contents: SupportBundleRequest): Promise<string | undefined> {
    const ret = await makeApiCall("service/support", {
        method: 'POST',
        body: JSON.stringify(contents)
    });
    if (ret && ret.location) {
        return ret.location;
    }
    return undefined;
}

export async function updateSource(name: string, sourceId?: string, password?: string, force?: boolean): Promise<boolean> {
    return !!await makeApiCall("service/sources", {
        method: 'PUT',
        body: JSON.stringify({
            sourceId: sourceId,
            name: name,
            password: password,
            force: force
        })
    });
}

export async function getBestRegion(): Promise<string | undefined> {
    const ret = await makeApiCall("service/best-region", {
        method: 'GET'
    });
    if (!ret) {
        return undefined;
    }
    return ret.region;
}

export async function createSecret(password: string, region: string, email: string): Promise<AdditionalKeys | undefined> {
    return await makeApiCall("service/secrets", {
        method: 'PUT',
        body: JSON.stringify({
            email: email,
            region: region,
            password: password
        })
    });
}

export async function deleteSecret(): Promise<AdditionalKeys | undefined> {
    return await makeApiCall("service/secrets", {
        method: 'DELETE',
        body: JSON.stringify({})
    });
}

export async function availableSecret(region: string, email: string): Promise<{ available: boolean } | undefined> {
    return await makeApiCall("service/secrets", {
        method: 'POST',
        body: JSON.stringify({
            region: region,
            email: email
        })
    });
}

export async function restoreSecret(region: string, sourceId: string, email: string, code: string,
                                    codeVerifier: string, password: string): Promise<{
    installing: boolean
} | undefined> {
    return await makeApiCall("service/secrets", {
        method: 'POST',
        body: JSON.stringify({
            email: email,
            sourceId: sourceId,
            codeVerifier: codeVerifier,
            code: code,
            region: region,
            password: password
        })
    });
}
