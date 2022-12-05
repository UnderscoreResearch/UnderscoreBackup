import {BackupFileSpecification, BackupFilter, BackupSet, BackupState} from "./index";

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