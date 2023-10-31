import * as React from "react";
import {BackupSet, BackupSetRoot, getLocalFiles, restartSets} from "../api";
import FileTreeView from './FileTreeView'
import DividerWithText from "../3rdparty/react-js-cron-mui/components/DividerWithText";
import Cron from "../3rdparty/react-js-cron-mui";
import {Checkbox, FormControlLabel, FormGroup, Paper, Tab, Tabs, TextField, Tooltip} from "@mui/material";
import {DestinationProp, useApplication} from "../utils/ApplicationContext";
import Box from "@mui/material/Box";
import Retention from "./Retention";
import {expandRoots} from "../api/utils";
import ExclusionList from "./ExclusionList";
import IconButton from "@mui/material/IconButton";
import {PlayArrow} from "@mui/icons-material";
import {useActivity} from "../utils/ActivityContext";

export interface SetState {
    set: BackupSet,
    tab: number
}

export interface SetProps {
    set: BackupSet,
    allowReset: boolean,
    destinations: DestinationProp[],
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

export default function SetConfig(props: SetProps) {
    const appContext = useApplication();
    const activityContext = useActivity();
    const [state, setState] = React.useState(() => {
        return {
            tab: 0,
            set: expandRoots(props.set, appContext.backendState) as BackupSet
        } as SetState
    });

    function updateState(newState: SetState) {
        setState(newState);
        props.setUpdated(newState.set.destinations.length > 0 &&
            newState.set.roots.length > 0 &&
            !!newState.set.id,
            newState.set);
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
        <div style={{float: "right", height: "100%", paddingTop:  "8px"}}>
            <TextField variant="standard"
                       required={true}
                       placeholder={"Set name"}
                       value={state.set.id}
                       error={!state.set.id}
                       onChange={(e) => updateState({
                           ...state,
                           set: {
                               ...state.set,
                               id: e.target.value
                           }
                       })}
            />
            {props.allowReset &&
                <Tooltip
                    title="Backup this set now">
                    <IconButton style={{marginLeft: "8px"}} onClick={() => {
                        appContext.busyOperation(async () => {
                            await restartSets([props.set.id]);
                            await activityContext.update();
                        });
                    }
                    }><PlayArrow/></IconButton>
                </Tooltip>
            }
        </div>
        <Tabs value={state.tab} onChange={changeTab}>
            <Tab label="Contents"/>
            <Tab label="Schedule"/>
            <Tab label="Advanced"/>
        </Tabs>
        <TabPanel value={state.tab} index={0}>
            <FileTreeView
                fileFetcher={getLocalFiles}
                backendState={appContext.backendState}
                roots={state.set.roots}
                stateValue={""}
                onChange={fileSelectionChanged}
            />
        </TabPanel>
        <TabPanel value={state.tab} index={1}>
            <div style={{marginLeft: "8px"}}>
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
                      clearButton={false}/>

                <FormControlLabel control={<Checkbox
                    disabled={state.set.schedule === undefined}
                    checked={state.set.continuous && state.set.schedule !== undefined}
                    onChange={() => updateState({
                        ...state,
                        set: {
                            ...state.set,
                            continuous: !state.set.continuous
                        }
                    })}
                />} label="Continuously listen for file changes"/>
            </div>
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
            <FormGroup style={{marginLeft: "8px"}}>
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
            <ExclusionList exclusions={state.set.exclusions} exclusionsChanged={exclusionsChanged}/>
        </TabPanel>
    </Paper>;
}