import React from "react";
import FileTreeView from './FileTreeView'
import {BackupDefaults, BackupFile, BackupFilter, BackupSetRoot} from "../api";

export interface SetTreeViewState {
    selected: string[],
    unselected: string[],
    stateValue?: any,
    roots: BackupSetRoot[]
}

export interface SetTreeViewProps {
    roots: BackupSetRoot[],
    fileFetcher: (node: string) => Promise<BackupFile[] | undefined>,
    stateValue: "",
    defaults: BackupDefaults,
    onChange: (roots: BackupSetRoot[]) => void
}

function populateFilter(filters: BackupFilter[] | undefined, root: string, selected: string[], unselected: string[]) {
    let fixedRoot = root + "/";
    if (filters) {
        filters.forEach(filter => {
            let list: string[];
            if (filter.type === "EXCLUDE") {
                list = unselected;
            } else {
                list = selected;
            }
            filter.paths.map(path => {
                let totalPath = fixedRoot + path;
                list.push(totalPath);
                if (filter.children) {
                    populateFilter(filter.children, totalPath, selected, unselected);
                }
            });
        })
    }
}

function createSelected(roots : BackupSetRoot[]) : { selected: string[], unselected: string[]  } {
    let selected: string[] = [];
    let unselected: string[] = [];
    roots.forEach(root => {
        var fixedRoot = root.path;
        if (fixedRoot.length > 1 && fixedRoot.endsWith("/"))
            fixedRoot = fixedRoot.substring(0, fixedRoot.length - 1);
        selected.push(fixedRoot);
        if (root.filters) {
            populateFilter(root.filters, fixedRoot, selected, unselected);
        }
    });

    return {
        selected: selected,
        unselected: unselected
    }
}

function createFilters(remainingPath: string, checked: boolean, filters?: BackupFilter[]) : BackupFilter[] {
    let type: "INCLUDE" | "EXCLUDE" = checked ? "INCLUDE" : "EXCLUDE";
    if (!filters) {
        return [
            {
                paths: [remainingPath],
                type: type
            }
        ]
    }

    const start = remainingPath + "/";
    let newFilters = [...filters];
    for (let i = 0; i < newFilters.length; i++) {
        if (newFilters[i].paths[0] === remainingPath) {
            newFilters.splice(i, 1);
            return newFilters;
        }
        if (start.startsWith(newFilters[i].paths[0])) {
            newFilters[i].children = createFilters(remainingPath.substring(newFilters[i].paths[0].length + 1), checked, newFilters[i].children);
            return newFilters;
        }
    }
    newFilters.push({
            paths: [remainingPath],
            type: type
        }
    );
    return newFilters
}

function normalizeRoots(roots: BackupSetRoot[], defaults: BackupDefaults) : BackupSetRoot[] {
    return roots.map((root) => {
        let normalizedPath = root.path.replaceAll(defaults.pathSeparator, "/");
        return {
            path: normalizedPath,
            filters: root.filters,
        }
    })
}

function physicalRoots(roots: BackupSetRoot[], defaults: BackupDefaults) : BackupSetRoot[] {
    const rootedWithSeparator = defaults.set.roots[0].path.startsWith(defaults.pathSeparator);
    return roots.map((root) => {
        let physicalPath;
        if (root.path !== "/") {
            physicalPath = root.path.replaceAll("/", defaults.pathSeparator);
            if (physicalPath.startsWith(defaults.pathSeparator) && !rootedWithSeparator) {
                physicalPath = physicalPath.substring(1);
            }
        } else {
            physicalPath = root.path;
        }
        return {
            path: physicalPath,
            filters: root.filters,
        }
    })
}

export default function SetTreeView(props: SetTreeViewProps) {
    function defaultState() : SetTreeViewState {
        const normalizedRoots = normalizeRoots(props.roots, props.defaults);
        return {
            ...createSelected(normalizedRoots),
            stateValue: props.stateValue,
            roots: normalizedRoots
        }
    }

    const [state, setState] = React.useState<SetTreeViewState>(() => defaultState());

    if (state.stateValue !== props.stateValue) {
        setState(defaultState());
    }

    function updateState(newState: SetTreeViewState) {
        setState(newState);
        props.onChange(physicalRoots(newState.roots, props.defaults));
    }

    function fileSelectionChanged(selectedId: string, checked: boolean) {
        let roots: BackupSetRoot[];
        if (selectedId == "/") {
            selectedId = "";
        }

        if (checked) {
            roots = state.roots.filter(root => !root.path.startsWith(selectedId + "/") && root.path !== selectedId + "/");
        } else {
            roots = state.roots.filter(root => !root.path.startsWith(selectedId + "/") && root.path !== selectedId + "/");
        }

        let notFound = true;
        roots = roots.map(root => {
            if (selectedId.startsWith(root.path)) {
                notFound = false;
                return {
                    path: root.path,
                    filters: createFilters(selectedId.substring(root.path.length), checked, root.filters)
                }
            }
            return root;
        });
        if (checked && notFound) {
            roots.push({
                path: selectedId + "/"
            });
        }

        updateState({
            ...state,
            roots: roots
        });
    }

    return <FileTreeView selected={state.selected}
                         stateValue={state.stateValue}
                         unselected={state.unselected}
                         fileFetcher={props.fileFetcher}
                         selectionChanged={fileSelectionChanged}
    />
}