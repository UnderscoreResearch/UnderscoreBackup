import React, {useEffect} from 'react';
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
    BackupDestination,
    BackupFile,
    BackupSetRoot,
    downloadBackupFile,
    getBackupFiles,
    getBackupVersions,
    searchBackup
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
import {listShares, listSources, ShareResponse, SourceResponse} from "../api/service";
import {ApplicationContext, DestinationProp, useApplication} from "../utils/ApplicationContext";
import {useButton} from "../utils/ButtonContext";

export interface RestorePropsChange {
    password: string,
    timestamp?: Date,
    roots: BackupSetRoot[],
    destination?: string,
    overwrite: boolean,
    source?: string,
    includeDeleted?: boolean
}

export interface RestoreProps {
    password?: string,
    timestamp?: Date,
    destination?: string,
    overwrite: boolean,
    roots: BackupSetRoot[],
    includeDeleted?: boolean
    restoreUpdated: (state: RestorePropsChange) => void
}

export interface RestoreState {
    password: string,
    timestamp?: Date,
    current: boolean,
    search: string,
    source: string,
    showingSearch: string,
    roots: BackupSetRoot[],
    overwrite: boolean,
    destination?: string,
    includeDeleted?: boolean,
    serviceSources?: SourceResponse[]
    serviceShares?: ShareResponse[]
}

function defaultState(appContext: ApplicationContext, props: RestoreProps): RestoreState {
    return {
        password: props.password ? props.password : "",
        roots: props.roots,
        timestamp: props.timestamp,
        search: "",
        source: appContext.selectedSource ? appContext.selectedSource : "-",
        showingSearch: "",
        current: !props.timestamp,
        overwrite: props.overwrite,
        destination: props.destination ? props.destination : appContext.backendState.defaultRestoreFolder,
        includeDeleted: props.includeDeleted,
        serviceSources: appContext.backendState.serviceConnected ? undefined : [],
        serviceShares: appContext.backendState.serviceConnected ? undefined : []
    }
}

function sourceList(appContext: ApplicationContext): DestinationProp[] {
    if (appContext.originalConfiguration && appContext.originalConfiguration.additionalSources) {
        const keys = Object.keys(appContext.originalConfiguration.additionalSources);
        keys.sort();

        return keys.map(key => {
            return {
                // @ts-ignore
                destination: appContext.originalConfiguration.additionalSources[key] as BackupDestination,
                id: key
            }
        });
    }
    return [];
}

export default function Restore(props: RestoreProps) {
    const appContext = useApplication();
    const buttonContext = useButton();
    const [state, setState]
        = React.useState(() => defaultState(appContext, props));

    function updateState(newState: RestoreState) {
        if (newState.password.length > 0) {
            props.restoreUpdated({
                password: newState.password,
                timestamp: newState.current ? undefined : newState.timestamp,
                overwrite: newState.overwrite,
                source: newState.source,
                destination: newState.destination,
                includeDeleted: newState.includeDeleted,
                roots: newState.roots
            });
        }
        setState(newState);
    }

    async function fetchSources() {
        if (!state.serviceSources) {
            const sources = await listSources(true);
            if (sources && sources.sources) {
                setState((oldState) => ({
                    ...oldState,
                    serviceSources: sources.sources
                }));
            }
        }
    }

    async function fetchShares() {
        if (!state.serviceShares) {
            const shares = await listShares();
            if (shares && shares.shares) {
                setState((oldState) => ({
                    ...oldState,
                    serviceShares: shares.shares
                }));
            }
        }
    }

    useEffect(() => {
        setState({
            ...defaultState(appContext, props),
            serviceSources: state.serviceSources,
            serviceShares: state.serviceShares
        });
    }, [appContext.selectedSource === "" ? "1" : ""])

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

    if (state.source !== "-" && (!state.serviceSources || !state.serviceShares)) {
        fetchSources();
        fetchShares();
    }

    async function fetchTooltipContents(path: string): Promise<((anchor: HTMLElement, open: boolean, handleClose: () => void)
        => JSX.Element) | undefined> {
        const files = await getBackupVersions(path);
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
                                                onClick={() => {
                                                    downloadBackupFile(file.path,
                                                        file.added ? file.added : 0,
                                                        state.password);
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
            return searchBackup(state.showingSearch, !!state.includeDeleted,
                state.current ? undefined : state.timestamp);
        } else {
            return getBackupFiles(path,
                !!state.includeDeleted,
                state.current ? undefined : state.timestamp);
        }
    }

    const localSources = sourceList(appContext);

    if (!props.password || !appContext.validatedPassword) {
        return <Stack spacing={2} style={{width: "100%"}}>
            <Paper sx={{
                p: 2
            }}>
                <Typography variant="body1" component="div">
                    <p>
                        To restore data you need to provide your backup password.
                    </p>
                </Typography>
                <Box component="div"
                     sx={{
                         '& .MuiTextField-root': {m: 1},
                     }}
                     style={{marginTop: 4}}>
                    <FormControl fullWidth={true} style={{margin: "8px"}}>
                        <InputLabel id="source-id-label">Source</InputLabel>
                        {appContext.selectedSource ?
                            <Select
                                labelId="source-id-label"
                                value={state.source}
                                label="Source"
                                disabled={true}
                            >
                                <MenuItem key={appContext.selectedSource}
                                          value={appContext.selectedSource}>{appContext.selectedSourceName}</MenuItem>
                            </Select>
                            :
                            <Select
                                labelId="source-id-label"
                                value={state.source}
                                label="Source"
                                id={"restoreSource"}
                                onChange={
                                    (e) => {
                                        updateState({
                                            ...state,
                                            source: e.target.value,
                                        })
                                    }
                                }
                                onOpen={() => {
                                    fetchSources();
                                    fetchShares();
                                }}
                            >
                                <MenuItem key="-" value={"-"}>Local</MenuItem>
                                {localSources.length > 0 &&
                                    <Divider>Local Sources</Divider>
                                }
                                {localSources.sort().map(str =>
                                    <MenuItem key={str.id} value={str.id}>{str.id}</MenuItem>
                                )}
                                {state.serviceSources && state.serviceSources.length > 0 &&
                                    <Divider>Service Sources</Divider>
                                }
                                {(state.serviceSources ? state.serviceSources : [])
                                    .map((source) =>
                                        <MenuItem key={source.sourceId} value={source.sourceId}>{source.name}</MenuItem>
                                    )}
                                {state.serviceShares && state.serviceShares.length > 0 &&
                                    <Divider>Service Shares</Divider>
                                }
                                {(state.serviceShares ? state.serviceShares : [])
                                    .map((share) =>
                                        <MenuItem key={share.shareId} value={share.shareId}>{share.name}</MenuItem>
                                    )}
                            </Select>
                        }
                    </FormControl>
                    <TextField label="Password" variant="outlined"
                               fullWidth={true}
                               required={true}
                               id={"restorePassword"}
                               value={state.password}
                               error={!state.password}
                               type="password"
                               onKeyDown={(e) => {
                                   if (e.key === "Enter") {
                                       buttonContext.accept();
                                   }
                               }}
                               onChange={(e) => updateState({
                                   ...state,
                                   password: e.target.value
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
                                    <IconButton onClick={() => setState({...state, showingSearch: "", search: ""})}>
                                        <Clear/>
                                    </IconButton>
                                }
                                <IconButton onClick={() => setState({...state, showingSearch: state.search})}>
                                    <Search/>
                                </IconButton>
                            </InputAdornment>
                        )
                    }}
                />
                <FileTreeView roots={state.roots}
                              backendState={appContext.backendState}
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
                                destination: appContext.backendState.defaultRestoreFolder
                            });
                        }
                    }}/>} label="Original location" style={{whiteSpace: "nowrap", marginRight: "1em"}}/>
                    <TextField variant="outlined"
                               label={"Custom Location"}
                               fullWidth={true}
                               disabled={!state.destination || state.destination === "-" || state.destination === "="}
                               defaultValue={state.destination && state.destination !== "-" && state.destination !== "="
                                   ? state.destination : appContext.backendState.defaultRestoreFolder}
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
                                    destination: appContext.backendState.defaultRestoreFolder
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