import * as React from "react";
import {BackupDefaults, BackupFilter, BackupRetentionAdditional, BackupSet, BackupSetRoot, GetLocalFiles} from "../api";
import SetTreeView from './SetTreeView'
import DividerWithText from "../3rdparty/react-js-cron-mui/components/DividerWithText";
import Cron from "../3rdparty/react-js-cron-mui";
import {EditableList} from "./EditableList";
import {Checkbox, FormControlLabel, FormGroup, Paper, Tab, Tabs, TextField} from "@mui/material";
import {DestinationProp} from "./Destinations";
import Timespan from "./Timespan";
import Box from "@mui/material/Box";

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

function expandFilters(filters: BackupFilter[]) : BackupFilter[]
{
    let ret : BackupFilter[] = [];
    filters.forEach(filter => {
        if (filter.paths.length > 1) {
            filter.paths.forEach(path => {
                let children : BackupFilter[]|undefined;
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

function expandRoots(set : BackupSet, defaults: BackupDefaults) : BackupSet {
    for (let i = 0; i < set.roots.length; i++) {
        const root = set.roots[i];
        if (root.filters)
            root.filters = expandFilters(root.filters);
        if (!root.path.endsWith(defaults.pathSeparator))
            root.path += defaults.pathSeparator;
    }
    return set;
}


function createExclusionControl(item, itemUpdated: (item: string) => void): React.ReactElement {
    return <TextField variant="standard"
                      fullWidth={true}
                      defaultValue={item}
                      onBlur={(e) => itemUpdated(e.target.value)}
    />
}

function AdditionalTimespans(props: {
    items: BackupRetentionAdditional[],
    state: SetState,
    updateState: (newState: SetState) => void
}) {
    function createItem(item: BackupRetentionAdditional, onItemUpdate: (item: BackupRetentionAdditional) => void)
        : React.ReactElement {
        return <div style={{display: "flex"}}>
            <Timespan
                timespan={item.validAfter}
                noFoever={true}
                onChange={(newTimespace) => {
                    if (newTimespace) {
                        onItemUpdate({
                            validAfter: newTimespace,
                            frequency: item.frequency
                        });
                    }
                }}
                title={"After"}/>
            <Timespan timespan={item.frequency}
                      onChange={(newTimespace) => {
                          if (newTimespace) {
                              onItemUpdate({
                                  validAfter: item.validAfter,
                                  frequency: newTimespace
                              });
                          }
                      }}
                      title={"keep at most one version per "}/>
        </div>
    }

    function updateAdditionalState(items: BackupRetentionAdditional[]): void {
        props.updateState({
            ...props.state,
            set: {
                ...props.state.set,
                retention: {
                    ...props.state.set.retention,
                    older: items
                }
            }
        });
    }

    function createNewItem(): BackupRetentionAdditional {
        const olderState = props.state.set.retention.older;
        if (olderState && olderState.length > 0) {
            const lastItem = olderState[olderState.length - 1];
            return {
                frequency: lastItem.frequency,
                validAfter: {
                    unit: lastItem.validAfter.unit,
                    duration: lastItem.validAfter.duration * 2
                }
            }
        }
        return {
            frequency: {
                unit: "MONTHS",
                duration: 1
            },
            validAfter: {
                unit: "MONTHS",
                duration: 1
            }
        }
    }

    return EditableList<BackupRetentionAdditional>({
        createItem: createItem,
        items: props.items,
        onItemChanged: updateAdditionalState,
        createNewItem: createNewItem
    });
}

export default function SetConfig(props : SetProps) {
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
        updateState({
            ...state,
            set: {
                ...state.set,
                schedule: value
            }
        })
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
            <SetTreeView
                fileFetcher={GetLocalFiles}
                defaults={props.defaults}
                roots={state.set.roots}
                stateValue={""}
                onChange={fileSelectionChanged}
            />
        </TabPanel>
        <TabPanel value={state.tab} index={1}>
            <Cron value={state.set.schedule} setValue={changedSchedule} clockFormat='12-hour-clock'
                  clearButton={false}/>
            <DividerWithText>Retention</DividerWithText>
            <Timespan timespan={state.set.retention.defaultFrequency}
                      onChange={(newTimespace) => updateState({
                          ...state,
                          set: {
                              ...state.set,
                              retention: {
                                  ...state.set.retention,
                                  defaultFrequency: newTimespace
                              }
                          }
                      })}
                      title={"Initially keep at most one version per "}/>
            <AdditionalTimespans items={state.set.retention.older ? state.set.retention.older : []}
                                 state={state}
                                 updateState={updateState}/>

            <Timespan timespan={state.set.retention.retainDeleted}
                      onChange={(newTimespace) => updateState({
                          ...state,
                          set: {
                              ...state.set,
                              retention: {
                                  ...state.set.retention,
                                  retainDeleted: newTimespace
                              }
                          }
                      })}
                      title={"Keep deleted files for "}/>
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
            <DividerWithText>Excluded Files</DividerWithText>
            {
                EditableList<string>({
                    createItem: createExclusionControl,
                    items: state.set.exclusions as string[],
                    onItemChanged: exclusionsChanged,
                    createNewItem: () => ""
                })
            }
        </TabPanel>
    </Paper>;
}