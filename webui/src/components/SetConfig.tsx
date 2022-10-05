import * as React from "react";
import {BackupDefaults, BackupFilter, BackupSet, BackupSetRoot, GetLocalFiles} from "../api";
import FileTreeView from './FileTreeView'
import DividerWithText from "../3rdparty/react-js-cron-mui/components/DividerWithText";
import Cron from "../3rdparty/react-js-cron-mui";
import {EditableList} from "./EditableList";
import {Checkbox, FormControlLabel, FormGroup, Paper, Tab, Tabs, TextField} from "@mui/material";
import {DestinationProp} from "./Destinations";
import Box from "@mui/material/Box";
import Retention from "./Retention";

export interface SetState {
    valid: boolean,
    set: BackupSet,
    tab: number
}

export interface SetProps {
    set: BackupSet,
    destinations: DestinationProp[],
    defaults: BackupDefaults,
    setUpdated: (valid: boolean, val: BackupSet) => void
}

interface TabPanelProps {
    children?: React.ReactNode;
    index: number;
    value: number;
}

function TabPanel(props: TabPanelProps) {
    const {children, value, index, ...other} = props;

    return (
        <div
            role="tabpanel"
            hidden={value !== index}
            {...other}
        >
            {value === index && (
                <Box
                    component="div"
                    sx={{
                        '& .MuiTextField-root': {m: 1},
                    }}
                    style={{marginTop: "1em"}}
                >
                    {children}
                </Box>
            )}
        </div>
    );
}

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

function expandRoots(set: BackupSet, defaults: BackupDefaults): BackupSet {
    for (let i = 0; i < set.roots.length; i++) {
        const root = set.roots[i];
        if (root.filters)
            root.filters = expandFilters(root.filters);
        if (root.path !== "/" && !root.path.endsWith(defaults.pathSeparator))
            root.path += defaults.pathSeparator;
    }
    return set;
}


function createExclusionControl(item: string, itemUpdated: (item: string) => void): React.ReactElement {
    return <TextField variant="standard"
                      fullWidth={true}
                      defaultValue={item}
                      onBlur={(e) => itemUpdated(e.target.value)}
    />
}

export default function SetConfig(props: SetProps) {
    const [state, setState] = React.useState(() => {
        return {
            tab: 0,
            set: expandRoots(props.set, props.defaults)
        } as SetState
    });

    function updateState(newState: SetState) {
        setState(newState);
        props.setUpdated(newState.set.destinations.length > 0 && newState.set.roots.length > 0, newState.set);
    }

    function changedSchedule(value: string) {
        if (state.set.schedule !== undefined) {
            updateState({
                ...state,
                set: {
                    ...state.set,
                    schedule: value
                }
            })
        }
    }

    function exclusionsChanged(items: string[]) {
        updateState({
            ...state,
            set: {
                ...state.set,
                exclusions: items.filter(t => t)
            }
        })
    }

    function changeTab(event: React.SyntheticEvent, newValue: number) {
        setState({
            ...state,
            tab: newValue
        });
    }

    function fileSelectionChanged(roots: BackupSetRoot[]) {
        updateState({
            ...state,
            set: {
                ...state.set,
                roots: roots
            }
        });
    }

    return <Paper sx={{p: 2}}>
        <Tabs value={state.tab} onChange={changeTab}>
            <Tab label="Contents"/>
            <Tab label="Schedule"/>
            <Tab label="Advanced"/>
        </Tabs>
        <TabPanel value={state.tab} index={0}>
            <FileTreeView
                fileFetcher={GetLocalFiles}
                defaults={props.defaults}
                roots={state.set.roots}
                stateValue={""}
                onChange={fileSelectionChanged}
            />
        </TabPanel>
        <TabPanel value={state.tab} index={1}>
            <FormControlLabel control={<Checkbox
                checked={state.set.schedule !== undefined}
                onChange={(e) => updateState({
                    ...state,
                    set: {
                        ...state.set,
                        schedule: e.target.checked ? "0 3 * * *" : undefined
                    }
                })}
            />} label="Run on schedule"/>

            <Cron disabled={state.set.schedule === undefined}
                  value={state.set.schedule ? state.set.schedule : "0 3 * * *"} setValue={changedSchedule}
                  clockFormat='12-hour-clock'
                  clearButton={false}/>
            <DividerWithText>Retention</DividerWithText>
            <Retention retention={state.set.retention} retentionUpdated={(e) => updateState({
                ...state,
                set: {
                    ...state.set,
                    retention: e
                }
            })}/>
        </TabPanel>
        <TabPanel index={state.tab} value={2}>
            <DividerWithText>Destinations</DividerWithText>
            <FormGroup>
                {props.destinations.map(dest => <FormControlLabel
                    key={dest.id}
                    label={dest.destination.endpointUri}
                    control={
                        <Checkbox
                            defaultChecked={state.set.destinations.includes(dest.id)}
                            onChange={(e) => {
                                let newList = [...state.set.destinations];
                                if (e.target.checked) {
                                    newList.push(dest.id);
                                } else {
                                    newList = newList.filter(id => id !== dest.id);
                                }
                                updateState({
                                    ...state,
                                    set: {
                                        ...state.set,
                                        destinations: newList
                                    }
                                });
                            }
                            }
                        />
                    }/>)}
            </FormGroup>
            <DividerWithText>Excluded files</DividerWithText>
            {
                EditableList<string>({
                    createItem: createExclusionControl,
                    items: (state.set.exclusions ? state.set.exclusions : []) as string[],
                    onItemChanged: exclusionsChanged,
                    createNewItem: () => ""
                })
            }
        </TabPanel>
    </Paper>;
}