import {BackupFileSpecification, BackupFilter, BackupSet, BackupState} from "./index";
import crypto, {randomBytes} from "crypto";
import base64url from "base64url";

function expandFilters(filters: BackupFilter[]): BackupFilter[] {
    let ret: BackupFilter[] = [];
    filters.forEach(filter => {
        if (filter.paths.length > 1) {
            filter.paths.forEach(path => {
                let children: BackupFilter[] | undefined;
                if (filter.children) {
                    children = expandFilters(JSON.parse(JSON.stringify(filter.children)));
                }
                ret.push({
                    paths: [path],
                    children: children,
                    type: filter.type
                });
            });
        } else {
            ret.push(filter);
        }
    })
    return ret;
}

export function expandRoots(contents: BackupSet | BackupFileSpecification,
                            state: BackupState): BackupSet | BackupFileSpecification {
    if (contents && contents.roots) {
        for (let i = 0; i < contents.roots.length; i++) {
            const root = contents.roots[i];
            if (root.filters)
                root.filters = expandFilters(root.filters);
            if (root.path !== "/" && !root.path.endsWith(state.pathSeparator))
                root.path += state.pathSeparator;
        }
    } else {
        contents = {
            ...contents,
            roots: []
        }
    }
    return contents;
}

export function hash(data: string) {
    return base64url(crypto.createHash('sha256').update(data).digest())
        .replace("=", "");
}

export function authorizationRedirect(siteUrl: string, location: string, additionalArgs: string, additionalReturnArgs: string) {
    const code = base64url(randomBytes(32));
    const nonce = base64url(randomBytes(16));
    window.localStorage.setItem("redirectSource", location);
    window.localStorage.setItem("redirectCodeVerifier", code);
    window.localStorage.setItem("redirectNonce", nonce);

    const redirectSource = window.location.href.substring(0, window.location.href.lastIndexOf('/'))
        + `/authorizeaccept?nonce=${encodeURIComponent(nonce)}&${additionalReturnArgs}`;

    window.location.href = `${siteUrl}/authorize?clientId=DEFAULT_CLIENT&challenge=${
        encodeURIComponent(hash(code))}&redirectUrl=${
        encodeURIComponent(redirectSource)}&` + additionalArgs;
}

