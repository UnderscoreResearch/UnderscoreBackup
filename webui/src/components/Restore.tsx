import * as React from 'react';
import {Checkbox, FormControlLabel, Stack, TextField} from "@mui/material";
import Paper from "@mui/material/Paper";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import SetTreeView from "./SetTreeView"
import {BackupDefaults, BackupSetRoot, GetBackupFiles} from '../api';
import DateTimePicker from '@mui/lab/DateTimePicker';
import AdapterDateFns from '@mui/lab/AdapterDateFns';
import LocalizationProvider from '@mui/lab/LocalizationProvider';
import DividerWithText from "../3rdparty/react-js-cron-mui/components/DividerWithText";

export interface RestorePropsChange {
    passphrase: string,
    timestamp?: Date,
    roots: BackupSetRoot[],
    destination?: string,
    overwrite: boolean
}

export interface RestoreProps {
    passphrase?: string,
    timestamp?: Date,
    defaultDestination: string,
    destination?: string,
    overwrite: boolean,
    defaults: BackupDefaults,
    roots: BackupSetRoot[],
    onChange: (state: RestorePropsChange) => void,
    validatedPassphrase: boolean
}

export interface RestoreState {
    passphrase: string,
    timestamp?: Date,
    current: boolean,
    roots: BackupSetRoot[],
    overwrite: boolean,
    destination?: string
}

export default function Restore(props: RestoreProps) {
    const [state, setState] = React.useState<RestoreState>({
        passphrase: props.passphrase ? props.passphrase : "",
        roots: props.roots,
        timestamp: props.timestamp,
        current: !props.timestamp,
        overwrite: props.overwrite,
        destination: props.destination ? props.destination : props.defaultDestination
    });

    function updateState(newState: RestoreState) {
        if (newState.passphrase.length > 0) {
            props.onChange({
                passphrase: newState.passphrase,
                timestamp: newState.current ? undefined : newState.timestamp,
                overwrite: newState.overwrite,
                destination: newState.destination,
                roots: newState.roots
            });
        }
        setState(newState);
    }

    function updateSelection(newRoots: BackupSetRoot[]) {
        updateState({
            ...state,
            roots: newRoots
        });
    }

    function handleChangedDate(newValue?: Date) {
        updateState({
            ...state,
            timestamp: newValue
        });
    }

    if (!props.passphrase || !props.validatedPassphrase) {
        return <Stack spacing={2} style={{width: "100%"}}>
            <Paper sx={{
                p: 2
            }}>
                <Typography variant="body1" component="div">
                    <p>
                        To restore data you need to provide your backup passphrase.
                    </p>
                </Typography>
                <Box component="div"
                     sx={{
                         '& .MuiTextField-root': {m: 1},
                     }}
                     style={{marginTop: 4}}>
                    <TextField label="Passphrase" variant="outlined"
                               fullWidth={true}
                               required={true}
                               value={state.passphrase}
                               error={!state.passphrase}
                               type="password"
                               onChange={(e) => updateState({
                                   ...state,
                                   passphrase: e.target.value
                               })}/>
                </Box>
            </Paper>
        </Stack>
    } else {
        return <Stack spacing={2} style={{width: "100%"}}>
            <Paper sx={{
                p: 2
            }}>
                <DividerWithText>Restore from when</DividerWithText>
                <LocalizationProvider dateAdapter={AdapterDateFns}>
                    <div style={{display: "flex", alignContent: "center"}}>
                        <FormControlLabel control={<Checkbox checked={state.current} onChange={(e) => {
                            updateState({
                                ...state,
                                current: e.target.checked
                            });
                        }
                        }/>} label="Most recent"/>
                        <DateTimePicker
                            disabled={state.current}
                            value={state.timestamp ? state.timestamp : new Date()}
                            onChange={handleChangedDate}
                            renderInput={(params) => <TextField {...params} />}
                        />
                    </div>
                </LocalizationProvider>
            </Paper>
            <Paper sx={{
                p: 2
            }}>
                <DividerWithText>Contents selection</DividerWithText>
                <SetTreeView roots={state.roots}
                             defaults={props.defaults}
                             stateValue={state.current || ! state.timestamp ? "" : state.timestamp.getTime().toString()}
                             fileFetcher={(path) => GetBackupFiles(path, state.current ? undefined : state.timestamp)}
                             onChange={updateSelection}/>
            </Paper>
            <Paper sx={{
                p: 2
            }}>
                <DividerWithText>Restore location</DividerWithText>
                <div style={{display: "flex", alignContent: "center", marginTop: "0.5em"}}>
                    <FormControlLabel control={<Checkbox
                        disabled={state.destination === "-" || state.destination === "="}
                        checked={!state.destination} onChange={(e) => {
                        if (e.target.checked) {
                            updateState({
                                ...state,
                                destination: undefined
                            });
                        } else {
                            updateState({
                                ...state,
                                destination: props.defaults.defaultRestoreFolder
                            });
                        }
                    }}/>} label="Original location" style={{whiteSpace: "nowrap", marginRight: "1em"}}/>
                    <TextField variant="outlined"
                               label={"Custom Location"}
                               fullWidth={true}
                               disabled={!state.destination || state.destination === "-" || state.destination === "="}
                               defaultValue={state.destination && state.destination !== "-" && state.destination !== "="
                                   ? state.destination : props.defaults.defaultRestoreFolder}
                               onBlur={(e) => updateState({
                                   ...state,
                                   destination: e.target.value
                               })}
                    />
                    <FormControlLabel control={<Checkbox
                        disabled={state.destination === "-" || state.destination === "="}
                        checked={state.overwrite} onChange={(e) => {
                        updateState({
                            ...state,
                            overwrite: e.target.checked
                        });
                    }}/>} label="Write over existing files" style={{whiteSpace: "nowrap", marginLeft: "1em"}}/>
                </div>
                <div style={{display: "flex", alignContent: "center", marginTop: "0.5em"}}>
                    <FormControlLabel control={<Checkbox
                        checked={state.destination === "-" || state.destination === "="}
                        onChange={(e) => {
                            if (e.target.checked) {
                                updateState({
                                    ...state,
                                    destination: "-"
                                });
                            } else {
                                updateState({
                                    ...state,
                                    destination: props.defaults.defaultRestoreFolder
                                });
                            }
                        }}/>} label="Only verify backup" style={{whiteSpace: "nowrap", marginRight: "1em"}}/>
                    <FormControlLabel control={<Checkbox disabled={state.destination !== "-" && state.destination !== "="}
                                                         checked={state.destination === "="}
                                                         onChange={(e) => {
                                                             if (e.target.checked) {
                                                                 updateState({
                                                                     ...state,
                                                                     destination: "="
                                                                 });
                                                             } else {
                                                                 updateState({
                                                                     ...state,
                                                                     destination: "-"
                                                                 });
                                                             }
                                                         }}/>} label="Compare against local files" style={{whiteSpace: "nowrap", marginRight: "1em"}}/>

                </div>
            </Paper>
        </Stack>
    }
}