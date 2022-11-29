import * as React from 'react';
import {useEffect} from 'react';
import {
    Button,
    Checkbox,
    FormControl,
    FormControlLabel,
    InputAdornment,
    InputLabel,
    List,
    ListItem,
    MenuItem,
    Popover,
    Select,
    Stack,
    TextField
} from "@mui/material";
import Paper from "@mui/material/Paper";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import FileTreeView, {formatLastChange, formatSize, pathName} from "./FileTreeView"
import {
    BackupFile,
    BackupSetRoot,
    BackupState,
    DownloadBackupFile,
    GetBackupFiles,
    GetBackupVersions,
    GetSearchBackup
} from '../api';
import {DateTimePicker} from '@mui/x-date-pickers/DateTimePicker';
import {AdapterDateFns} from '@mui/x-date-pickers/AdapterDateFns';
import {LocalizationProvider} from '@mui/x-date-pickers/LocalizationProvider';
import Search from '@mui/icons-material/Search';
import DividerWithText from "../3rdparty/react-js-cron-mui/components/DividerWithText";
import {DisplayMessage} from "../App";
import IconButton from "@mui/material/IconButton";
import {Clear} from "@mui/icons-material";
import Divider from "@mui/material/Divider";

export interface RestorePropsChange {
    passphrase: string,
    timestamp?: Date,
    roots: BackupSetRoot[],
    source: string,
    destination?: string,
    overwrite: boolean,
    includeDeleted?: boolean
}

export interface RestoreProps {
    passphrase?: string,
    timestamp?: Date,
    defaultDestination: string,
    destination?: string,
    overwrite: boolean,
    state: BackupState,
    roots: BackupSetRoot[],
    sources: string[],
    source: string,
    activeSource: boolean,
    onChange: (state: RestorePropsChange) => void,
    onSubmit: () => void,
    validatedPassphrase: boolean,
    includeDeleted?: boolean
}

export interface RestoreState {
    passphrase: string,
    timestamp?: Date,
    current: boolean,
    search: string,
    source: string,
    showingSearch: string,
    roots: BackupSetRoot[],
    overwrite: boolean,
    destination?: string,
    includeDeleted?: boolean
}

function defaultState(props: RestoreProps) {
    return {
        passphrase: props.passphrase ? props.passphrase : "",
        roots: props.roots,
        timestamp: props.timestamp,
        search: "",
        source: props.source ? props.source : "-",
        showingSearch: "",
        current: !props.timestamp,
        overwrite: props.overwrite,
        destination: props.destination ? props.destination : props.defaultDestination,
        includeDeleted: props.includeDeleted
    }
}

export default function Restore(props: RestoreProps) {
    const [state, setState] = React.useState<RestoreState>(defaultState(props));

    function updateState(newState: RestoreState) {
        if (newState.passphrase.length > 0) {
            props.onChange({
                passphrase: newState.passphrase,
                source: props.activeSource ? props.source : newState.source,
                timestamp: newState.current ? undefined : newState.timestamp,
                overwrite: newState.overwrite,
                destination: newState.destination,
                includeDeleted: newState.includeDeleted,
                roots: newState.roots
            });
        }
        setState(newState);
    }

    useEffect(() => {
        setState(defaultState(props))
    }, [props.source === "" ? "1" : ""])

    function updateSelection(newRoots: BackupSetRoot[]) {
        updateState({
            ...state,
            roots: newRoots
        });
    }

    function handleChangedDate(newValue: Date | null) {
        updateState({
            ...state,
            timestamp: newValue ? newValue : undefined
        });
    }

    async function fetchTooltipContents(path: string): Promise<((anchor: HTMLElement, open: boolean, handleClose: () => void)
        => JSX.Element) | undefined> {
        const files = await GetBackupVersions(path);
        if (files) {
            return function (anchor: HTMLElement, open: boolean, handleClose: () => void) {
                return <Popover
                    open={open}
                    anchorEl={open ? anchor : null}
                    onClose={handleClose}
                    anchorOrigin={{
                        vertical: 'bottom',
                        horizontal: 'right',
                    }}
                >
                    <Paper sx={{
                        p: 2
                    }}>
                        <Typography style={{fontWeight: "bold"}}>
                            {pathName(files[0].path)} Versions
                        </Typography>
                        <List>
                            {
                                files.map((file: BackupFile) => {
                                    return <ListItem key={file.added}>
                                        <Typography sx={{fontSize: 14}}>
                                            {formatSize(file.length)} ({formatLastChange(file.lastChanged)})
                                        </Typography>
                                        <Button style={{marginLeft: "8px"}}
                                                variant="contained"
                                                disabled={!file.length || file.length > 1024 * 1024 * 1024}
                                                onClick={(e) => {
                                                    DownloadBackupFile(file.path,
                                                        file.added ? file.added : 0,
                                                        state.passphrase);
                                                    DisplayMessage("Download started", "success");
                                                }
                                                }>Download</Button>
                                    </ListItem>
                                })
                            }
                        </List>
                    </Paper>
                </Popover>
            }
        }
        return undefined;
    }

    function fetchContents(path: string) {
        if (state.showingSearch) {
            return GetSearchBackup(state.showingSearch, state.includeDeleted ? true : false,
                state.current ? undefined : state.timestamp);
        } else {
            return GetBackupFiles(path,
                state.includeDeleted ? true : false,
                state.current ? undefined : state.timestamp);
        }
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
                    <FormControl fullWidth={true} style={{margin: "8px"}}>
                        <InputLabel id="source-id-label">Source</InputLabel>
                        {props.activeSource ?
                            <Select
                                labelId="source-id-label"
                                value={state.source}
                                label="Source"
                                disabled={true}
                            >
                                <MenuItem key={props.source} value={props.source}>{props.source}</MenuItem>
                            </Select>
                            :
                            <Select
                                labelId="source-id-label"
                                value={state.source}
                                label="Source"
                                id={"restoreSource"}
                                onChange={(e) => {
                                    updateState({
                                        ...state,
                                        source: e.target.value
                                    })
                                }
                                }
                            >
                                <MenuItem key="-" value={"-"}>Local</MenuItem>
                                {props.sources && props.sources.length > 0 && <Divider>Other Sources</Divider>}
                                {props.sources.sort().map(str => {
                                    return <MenuItem key={str} value={str}>{str}</MenuItem>;
                                })}
                            </Select>
                        }
                    </FormControl>
                    <TextField label="Passphrase" variant="outlined"
                               fullWidth={true}
                               required={true}
                               id={"restorePassphrase"}
                               value={state.passphrase}
                               error={!state.passphrase}
                               type="password"
                               onKeyDown={(e) => {
                                   if (e.key === "Enter") {
                                       props.onSubmit();
                                   }
                               }}
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
                        <FormControlLabel control={<Checkbox checked={state.includeDeleted} onChange={(e) => {
                            updateState({
                                ...state,
                                includeDeleted: e.target.checked
                            });
                        }
                        }/>} label="Include deleted files" style={{marginLeft: "8px"}}/>
                    </div>
                </LocalizationProvider>
            </Paper>
            <Paper sx={{
                p: 2
            }}>
                <DividerWithText>Contents selection</DividerWithText>
                <TextField
                    fullWidth={true}
                    label="Search repository using Regular Expressions"
                    variant={"standard"}
                    value={state.search}
                    onChange={(event) => setState({
                        ...state,
                        search: event.target.value
                    })}
                    onKeyDown={(e) => {
                        if (e.key === "Enter") {
                            setState({...state, showingSearch: state.search});
                        }
                    }}
                    style={{marginBottom: "8px"}}
                    InputProps={{
                        endAdornment: (
                            <InputAdornment position={"end"}>
                                {
                                    state.showingSearch &&
                                    <IconButton onClick={(e) => setState({...state, showingSearch: "", search: ""})}>
                                        <Clear/>
                                    </IconButton>
                                }
                                <IconButton onClick={(e) => setState({...state, showingSearch: state.search})}>
                                    <Search/>
                                </IconButton>
                            </InputAdornment>
                        )
                    }}
                />
                <FileTreeView roots={state.roots}
                              state={props.state}
                              hideRoot={!!state.showingSearch}
                              rootName={state.showingSearch ? "Not found" : ""}
                              rootNameProcessing={state.showingSearch ? "Searching..." : ""}
                              onFileDetailPopup={fetchTooltipContents}
                              stateValue={state.current + (!state.timestamp ? "" : state.timestamp.getTime().toString())
                                  + state.includeDeleted + state.showingSearch}
                              fileFetcher={(path) => fetchContents(path)}
                              onChange={updateSelection}/>
            </Paper>
            <Paper sx={{
                p: 2
            }}>
                <DividerWithText>Restore location</DividerWithText>
                <div style={{display: "flex", alignContent: "center", marginTop: "0.5em"}}>
                    <FormControlLabel control={<Checkbox
                        disabled={state.destination === "-" || state.destination === "="}
                        id={"originalLocation"}
                        checked={!state.destination} onChange={(e) => {
                        if (e.target.checked) {
                            updateState({
                                ...state,
                                destination: undefined
                            });
                        } else {
                            updateState({
                                ...state,
                                destination: props.state.defaultRestoreFolder
                            });
                        }
                    }}/>} label="Original location" style={{whiteSpace: "nowrap", marginRight: "1em"}}/>
                    <TextField variant="outlined"
                               label={"Custom Location"}
                               fullWidth={true}
                               disabled={!state.destination || state.destination === "-" || state.destination === "="}
                               defaultValue={state.destination && state.destination !== "-" && state.destination !== "="
                                   ? state.destination : props.state.defaultRestoreFolder}
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
                        id={"onlyVerifyLocal"}
                        onChange={(e) => {
                            if (e.target.checked) {
                                updateState({
                                    ...state,
                                    destination: "-"
                                });
                            } else {
                                updateState({
                                    ...state,
                                    destination: props.state.defaultRestoreFolder
                                });
                            }
                        }}/>} label="Only verify backup" style={{whiteSpace: "nowrap", marginRight: "1em"}}/>
                    <FormControlLabel
                        control={<Checkbox disabled={state.destination !== "-" && state.destination !== "="}
                                           checked={state.destination === "="}
                                           id={"compareAgainstLocal"}
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
                                           }}/>} label="Compare against local files"
                        style={{whiteSpace: "nowrap", marginRight: "1em"}}/>

                </div>
            </Paper>
        </Stack>
    }
}