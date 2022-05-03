import * as React from 'react';
import {useEffect} from 'react';
import {FixedSizeList as List} from "react-window";
import AutoSizer from "react-virtualized-auto-sizer";
import {Checkbox, CircularProgress, FormControlLabel} from "@mui/material";
import IconButton from "@mui/material/IconButton";
import {ChevronRight, KeyboardArrowDown} from "@mui/icons-material";
import {BackupDefaults, BackupFile, BackupFilter, BackupSetRoot} from "../api";
import './FileTreeView.css'

function formatSize(size?: number): string {
    if (size === undefined) {
        return ""
    }
    if (size > 1024 * 1024 * 1024 * 10)
        return (Math.round(size / 1024 / 1024 / 1024 * 10) / 10) + " GB";
    if (size > 1024 * 1024 * 10)
        return (Math.round(size / 1024 / 1024 * 10) / 10) + " MB";
    if (size > 1024 * 10)
        return (Math.round(size / 1024 * 10) / 10) + " KB";
    return size + " B";
}

function formatLastChange(lastChange?: number): string {
    if (lastChange === undefined)
        return ""

    const date = new Date(lastChange);
    if (Date.now() - lastChange < 24 * 60 * 60 * 1000)
        return date.toLocaleTimeString();
    else
        return date.toLocaleDateString();
}

function pathName(path: string): string {
    if (path.endsWith('/')) {
        let ind = path.lastIndexOf('/', path.length - 2);
        return path.substring(ind + 1, path.length - 1);
    } else {
        let ind = path.lastIndexOf('/');
        return path.substring(ind + 1);
    }
}

interface TreeItem {
    path: string,
    level: number,
    name: string,
    hasChildren: boolean,
    expanded: boolean,
    loading: boolean,
    size: string,
    added: string
}

interface SetTreeViewState {
    items: TreeItem[],
    roots: BackupSetRoot[],
    stateValue?: any
}

export interface SetTreeViewPropsNG {
    roots: BackupSetRoot[],
    fileFetcher: (node: string) => Promise<BackupFile[] | undefined>,
    stateValue: "",
    defaults: BackupDefaults,
    onChange: (roots: BackupSetRoot[]) => void
}

interface MatchedFilter {
    filter?: BackupFilter,
    exactMatch: boolean,
    matchingChild: boolean,
    path: string
}

function findFilter(path: string, filters: BackupFilter[] | undefined): MatchedFilter {
    let matchingChild = false;
    if (filters) {
        for (let i = 0; i < filters.length; i++) {
            let filter = filters[i];
            for (let j = 0; j < filter.paths.length; j++) {
                let filterPath = filter.paths[j];
                if (path === filterPath || path + "/" == filterPath) {
                    return {
                        filter: filter,
                        exactMatch: true,
                        matchingChild: false,
                        path: path
                    };
                }
                if ((path).startsWith(filterPath + "/")) {
                    let childFilter = findFilter(path.substring(filterPath.length + 1), filter.children);
                    if (childFilter.filter) {
                        return childFilter;
                    }
                    return {
                        filter: filter,
                        exactMatch: false,
                        matchingChild: childFilter.matchingChild,
                        path: path
                    };
                } else if ((filterPath + "/").startsWith(path)) {
                    matchingChild = true;
                }
            }
        }
    }
    return {
        exactMatch: false,
        matchingChild: matchingChild,
        path: path
    };
}

function shouldExpand(roots: BackupSetRoot[], path: string): boolean {
    const matchingRoot = roots.find(item => {
        if ((path + "/").startsWith(item.path))
            return true;
        if (item.path === path || item.path.startsWith(path + "/"))
            return true;
        return false;
    });

    if (matchingRoot) {
        if (matchingRoot.path === path + "/" || matchingRoot.path === path) {
            if (matchingRoot.filters) {
                return true;
            }
            return false;
        }
        if (matchingRoot.path.startsWith(path + "/")) {
            return true;
        }
        let filter = findFilter(path.substring(matchingRoot.path.length), matchingRoot.filters);
        if (filter.matchingChild || (filter.exactMatch && filter.filter &&
            filter.filter.children && filter.filter.children.length > 0)) {
            return true;
        }
        return false;
    }

    return false;
}

function createFilters(remainingPath: string, checked: boolean, filters?: BackupFilter[]): BackupFilter[] | undefined {
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
    for (let i = 0; i < newFilters.length;) {
        if (newFilters[i].paths[0] === remainingPath) {
            newFilters.splice(i, 1);
            if (newFilters.length > 0) {
                return newFilters;
            }
            return undefined;
        }

        if (start.startsWith(newFilters[i].paths[0])) {
            newFilters[i].children = createFilters(remainingPath.substring(newFilters[i].paths[0].length + 1), checked, newFilters[i].children);
            return newFilters;
        }

        if (newFilters[i].paths[0].startsWith(remainingPath + "/")) {
            newFilters.splice(i, 1);
        } else {
            i++;
        }
    }
    newFilters.push({
            paths: [remainingPath],
            type: type
        }
    );
    return newFilters
}

function physicalRoots(roots: BackupSetRoot[], defaults: BackupDefaults): BackupSetRoot[] {
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

function normalizeRoots(roots: BackupSetRoot[], defaults: BackupDefaults): BackupSetRoot[] {
    return roots.map((root) => {
        let normalizedPath = root.path.replaceAll(defaults.pathSeparator, "/");
        return {
            path: normalizedPath,
            filters: root.filters,
        }
    })
}

export default function FileTreeView(props: SetTreeViewPropsNG) {
    function defaultState() {
        return {
            items: [{
                path: "/",
                level: 0,
                name: "Filesystem Root",
                hasChildren: true,
                expanded: false,
                loading: true,
                size: "",
                added: ""
            }],
            roots: normalizeRoots(props.roots, props.defaults),
            stateValue: props.stateValue
        }
    }

    const [state, setState] = React.useState<SetTreeViewState>(() => defaultState());

    function updateState(newState: SetTreeViewState) {
        setState(newState);
        props.onChange(physicalRoots(newState.roots, props.defaults));
    }

    if (state.stateValue !== props.stateValue) {
        setState(defaultState());
    }

    function isPathSelected(path: string): boolean | undefined {
        const matchingRoot = state.roots.find(item => {
            if ((path + "/").startsWith(item.path))
                return true;
            if (item.path === path || item.path.startsWith(path + "/"))
                return true;
            return false;
        })

        if (matchingRoot) {
            if (matchingRoot.path === path + "/" || matchingRoot.path === path) {
                return true;
            }
            if (matchingRoot.path.startsWith(path)) {
                return undefined;
            }
            let filter = findFilter(path.substring(matchingRoot.path.length), matchingRoot.filters);
            if (filter.filter) {
                if (filter.filter.type === "INCLUDE") {
                    return true;
                }
                if ((filter.exactMatch && filter.filter && filter.filter.children &&
                    filter.filter.children.length > 0) || filter.matchingChild) {
                    return undefined;
                }
                return false;
            }
            return true;
        }

        if (state.roots.length > 0 && path === "/") {
            return undefined;
        }

        return false;
    }

    function pathSelected(path: string, checked: boolean) {
        let roots: BackupSetRoot[];
        if (path == "/") {
            path = "";
        }

        if (checked) {
            roots = state.roots.filter(root => !root.path.startsWith(path + "/") && root.path !== path + "/");
        } else {
            roots = state.roots.filter(root => !root.path.startsWith(path + "/") && root.path !== path + "/");
        }

        let notFound = true;
        roots = roots.map(root => {
            if (path.startsWith(root.path)) {
                notFound = false;
                return {
                    path: root.path,
                    filters: createFilters(path.substring(root.path.length), checked, root.filters)
                }
            }
            return root;
        });
        if (checked && notFound) {
            roots.push({
                path: path + "/"
            });
        }

        updateState({
            ...state,
            roots: roots
        });
    }

    async function loadPath(path: string) {
        let items = await props.fileFetcher(path);

        setState((oldState) => {
            let newItems = [...oldState.items];
            let index = oldState.items.findIndex(item => item.path === path);
            if (index >= 0) {
                const newItem = {
                    ...oldState.items[index],
                    loading: false,
                    expanded: true
                };
                if (items !== undefined) {
                    const treeItems: TreeItem[] = items.map(item => {
                        let hasChildren = item.path.endsWith("/");
                        let path = item.path.length > 1 && hasChildren ? item.path.substring(0, item.path.length - 1) : item.path;
                        let expand = false;
                        if (hasChildren) {
                            expand = shouldExpand(state.roots, path);
                            if (expand)
                                loadPath(path);
                        }
                        return {
                            path: path,
                            level: newItem.level + 1,
                            name: pathName((item.path)),
                            expanded: false,
                            loading: expand,
                            hasChildren: hasChildren,
                            size: formatSize(item.length),
                            added: formatLastChange(item.lastChanged)
                        };
                    });
                    newItems.splice(index + 1, 0, ...treeItems)
                }
                newItems[index] = newItem;
            }

            return {
                ...oldState,
                items: newItems
            }
        })
    }

    function Row(props: { index: number, style: React.CSSProperties }) {
        const item = state.items[props.index];

        const selected = isPathSelected(item.path);

        return <div style={{
            ...props.style,
            width: "100%",
            display: "flex"
        }}>
            <div style={{width: (16 * item.level) + "px"}}/>
            <div style={{width: "48px"}}>
                {
                    item.hasChildren ?
                        (
                            item.loading ?
                                <div style={{width: "43px", display: "flex", height: "100%", alignItems: "center"}}>
                                    <CircularProgress size={24}/>
                                </div>
                                :
                                <IconButton color="inherit" aria-label="Expand" component="span" onClick={() => {

                                    const newItems = [...state.items];
                                    newItems[props.index] = {
                                        ...item,
                                        expanded: !item.expanded,
                                        loading: !item.expanded
                                    };

                                    if (item.expanded) {
                                        let i;
                                        for (i = props.index + 1; i < state.items.length && state.items[i].level > item.level; i++) {
                                            // Nop
                                        }
                                        newItems.splice(props.index + 1, i - props.index - 1);
                                    } else {
                                        loadPath(item.path);
                                    }

                                    updateState({
                                        ...state,
                                        items: newItems
                                    });
                                }}>
                                    {
                                        item.expanded ?
                                            <KeyboardArrowDown/>
                                            : <ChevronRight/>
                                    }
                                </IconButton>
                        )
                        :
                        ""
                }
            </div>
            <FormControlLabel style={{width: "100%"}}
                              control={
                                  <Checkbox
                                      indeterminate={selected === undefined}
                                      checked={selected === true}
                                      onChange={event => pathSelected(item.path, event.target.checked)}

                                      onClick={e => e.stopPropagation()}
                                  />
                              }
                              label={
                                  <span style={{width: "100%", display: "flex", alignItems: "center"}}>
                <span style={{width: "100%"}}>{item.name}</span>
                <span style={{width: "150px", textAlign: "right"}}>{item.size}</span>
                <span style={{width: "150px", textAlign: "right"}}>{item.added}</span>
            </span>
                              }
            />
        </div>
    };

    useEffect(() => {
        loadPath("/")
    }, [])

    return (
        <div>
            <span style={{width: "100%", display: "flex", alignItems: "center", fontWeight: "bold"}}>
                <span style={{marginLeft: "72px", width: "100%", textAlign: "center"}}>Filename</span>
                <span style={{width: "150px", textAlign: "right", marginRight: "8px"}}>File Size</span>
                <span style={{width: "150px", textAlign: "right", marginRight: "32px"}}>Last Updated</span>
            </span>
            <div style={{height: Math.min((state.items.length * 40 + 1), 500) + "px"}}>
                <AutoSizer>
                    {({height, width}: { height: number; width: number }) => (
                        <List
                            className="List"
                            height={height}
                            itemCount={state.items.length}
                            itemSize={40}
                            width={width}
                        >
                            {Row}
                        </List>
                    )}
                </AutoSizer>
            </div>
        </div>
    );
}
