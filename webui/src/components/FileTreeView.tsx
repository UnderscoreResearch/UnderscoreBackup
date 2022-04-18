import React from "react";
import {BackupFile} from "../api"
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import {TreeItem, TreeView} from '@mui/lab';
import {Checkbox, CircularProgress, FormControlLabel} from "@mui/material";
import './FileTreeView.css'

interface FileRenderTree {
    id: string;
    name: string;
    hasChildren: boolean,
    length?: number,
    lastChanged?: number,
    children?: FileRenderTree[];
}

interface RecursiveTreeViewState {
    root: FileRenderTree,
    selected: string[],
    expanded: string[],
    stateValue: props.stateValue,
    unselected: string[],
    pending: string[]
}

export interface RecursiveTreeViewProps {
    selected: string[],
    unselected: string[],
    stateValue: string,
    fileFetcher: (node: string) => Promise<BackupFile[] | undefined>,
    selectionChanged?: (selectedId: string, checked: boolean) => void
};

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

function FileRowComponent(props: {
    nodes: FileRenderTree,
    state: RecursiveTreeViewState,
    onChange: (checked: boolean, nodes: FileRenderTree) => void
}) {
    let size;
    let lastChanged;
    let name;

    if (props.nodes.id === "/") {
        name = <span><b>{props.nodes.name}</b></span>;
        size = <span><b>File Size</b></span>
        lastChanged = <span><b>Last Updated</b></span>;
    } else {
        size = formatSize(props.nodes.length);
        lastChanged = formatLastChange(props.nodes.lastChanged);
        name = props.nodes.name;
    }

    return <FormControlLabel
        control={
            <Checkbox
                checked={props.state.selected.includes(props.nodes.id)}
                onChange={event =>
                    props.onChange(event.currentTarget.checked, props.nodes)
                }
                onClick={e => e.stopPropagation()}
            />
        }
        label={
            <span style={{width: "100%"}}>
                <span>{name}</span>
                <span style={{width: "150px", float: "right"}}>{lastChanged}</span>
                <span style={{width: "150px", float: "right"}}>{size}</span>
            </span>
        }
        key={props.nodes.id}
    />
}

function renderTree(nodes: FileRenderTree, state: RecursiveTreeViewState, onChange: (checked: boolean, nodes: FileRenderTree) => void) {
    return <TreeItem
        key={nodes.id}
        nodeId={nodes.id}
        label={
            <FileRowComponent nodes={nodes} state={state} onChange={onChange}/>
        }
    >
        {Array.isArray(nodes.children)
            ? nodes.children.map(node => renderTree(node, state, onChange))
            : (nodes.hasChildren ?
                    <FormControlLabel
                        control={<></>}
                        style={{marginLeft: "1em"}}
                        label={<span><CircularProgress size={"1em"}/> Loading...</span>}/>
                    : null
            )}
    </TreeItem>
}

export default function RecursiveTreeView(props: RecursiveTreeViewProps) {
    function createDefaultState() : RecursiveTreeViewState {
        return {
            root: {
                id: "/",
                name: "Filesystem Root",
                hasChildren: true
            },
            selected: props.selected,
            stateValue: props.stateValue,
            unselected: props.unselected,
            expanded: ["/"],
            pending: []
        };
    }

    const [state, setState] = React.useState<RecursiveTreeViewState>(() => createDefaultState());

    if (state.stateValue !== props.stateValue) {
        setState(createDefaultState());
    }

    function getNodeById(nodes: FileRenderTree, id: string) {
        if (nodes.id === id) {
            return nodes;
        } else if (id.startsWith(nodes.id) || nodes.id === "/") {
            if (Array.isArray(nodes.children)) {
                let result = null;
                nodes.children.every(node => {
                    if (!!getNodeById(node, id)) {
                        result = getNodeById(node, id);
                        return false;
                    }
                    return true;
                });
                return result;
            }
        }

        return null;
    }

    function getChildById(node: FileRenderTree, id: string) {
        let array: string[] = [];

        function getAllChild(nodes: FileRenderTree | null) {
            if (nodes === null) return [];
            array.push(nodes.id);
            if (Array.isArray(nodes.children)) {
                nodes.children.forEach(node => getAllChild(node));
            }
            return array;
        }

        return getAllChild(getNodeById(node, id));
    }

    function getOnChange(checked: boolean, nodes: FileRenderTree) {
        const allNode: string[] = getChildById(state.root, nodes.id);

        let array = Array.from(new Set(checked
            ? [...state.selected, ...allNode]
            : state.selected.filter(value => !allNode.includes(value))));

        if (props.selectionChanged) {
            props.selectionChanged(nodes.id, checked);
        }

        setState({
            ...state,
            selected: array
        });
    }

    async function fetchNodes(node: string, initial: boolean) {
        setState(oldState => {
            return {
                ...oldState,
                pending: [...oldState.pending, node]
        }});
        const files = await props.fileFetcher(node);
        if (files !== undefined) {
            const children = files.map(file => {
                let hasChildren;
                let ind;
                let name;
                let path;
                if (file.path.endsWith('/')) {
                    hasChildren = true;
                    ind = file.path.lastIndexOf('/', file.path.length - 2);
                    name = file.path.substring(ind + 1, file.path.length - 1);
                    path = file.path.substring(0, file.path.length - 1);
                } else {
                    ind = file.path.lastIndexOf('/');
                    name = file.path.substring(ind + 1);
                    path = file.path;
                    hasChildren = false;
                }
                return {
                    id: path,
                    name: name,
                    hasChildren: hasChildren,
                    length: file.length,
                    lastChanged: file.lastChanged
                } as FileRenderTree
            });

            setState((oldState) => {
                var parent = getNodeById(oldState.root, node);
                if (parent) {
                    var selected = oldState.selected;
                    var unselected = oldState.unselected;
                    var childIds = children.map(file => file.id);
                    if (selected.includes(parent.id)) {
                        selected = Array.from(new Set([...selected, ...childIds.filter(t => !unselected.includes(t))]));
                    }
                    unselected = unselected.filter(t => !childIds.includes(t));

                    // This violates immutability of state. Seems to work though.
                    parent.children = children;
                    let autoExpand: string[];
                    let expanded = oldState.expanded;
                    if (initial) {
                        autoExpand = childIds
                            .filter(s => !oldState.pending.includes(s))
                            .filter(s =>
                                props.selected.some(t => t.replace(/\/$/, "").startsWith(s + "/") && t !== s) ||
                                props.unselected.some(t => t.replace(/\/$/, "").startsWith(s + "/") && t !== s));
                        if (autoExpand.length > 0)
                            expanded = Array.from(new Set([...expanded, ...autoExpand]));
                    } else {
                        autoExpand = [];
                    }

                    if (initial) {
                        autoExpand
                            .forEach(s => setTimeout(() => fetchNodes(s, true), 1));
                    }

                    return {
                        ...oldState,
                        expanded: expanded,
                        selected: selected,
                        root: oldState.root,
                        unselected: unselected,
                        pending: oldState.pending.filter(t => t !== node)
                    }
                }
                return oldState;
            })
        }
    }

    function expandedNodes(event: React.SyntheticEvent, nodes: string[]) {
        const expandingNodes = nodes.filter(x => !state.expanded.includes(x));
        setState({
            ...state,
            expanded: nodes
        });
        if (expandingNodes[0]) {
            const childId = expandingNodes[0];
            const node = getNodeById(state.root, childId);
            if (node && node.hasChildren && !node.children) {
                fetchNodes(childId, false);
            }
        }
    }

    React.useEffect(() => {
        fetchNodes("/", true);
    }, [state.stateValue])

    return (
        <TreeView
            className={"file-tree-view"}
            defaultCollapseIcon={<ExpandMoreIcon/>}
            defaultExpandIcon={<ChevronRightIcon/>}
            expanded={state.expanded}
            onNodeToggle={expandedNodes}
            style={{maxHeight: "500px", overflowY: "auto"}}
            sx={{width: "100%"}}
        >
            {renderTree(state.root, state, getOnChange)}
        </TreeView>
    );
}