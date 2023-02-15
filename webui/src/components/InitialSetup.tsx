import * as React from "react";
import {Fragment, useEffect} from "react";
import Destination from "./Destination";
import {BackupConfiguration, BackupDestination, BackupManifest, BackupState} from "../api";
import Paper from "@mui/material/Paper";
import {
    Alert,
    Button,
    Checkbox,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    FormControlLabel,
    Grid,
    List,
    ListItem,
    MenuItem,
    Select,
    SelectChangeEvent,
    Stack,
    TextField
} from "@mui/material";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import UIAuthentication from "./UIAuthentication";
import ServiceAuthentication from "./ServiceAuthentication";
import {Check as CheckIcon} from "@mui/icons-material";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import {createSource, listSources, SourceResponse, updateSource} from "../api/service";
import DividerWithText from "../3rdparty/react-js-cron-mui/components/DividerWithText";
import {authorizationRedirect, hash} from "../api/utils";
import base64url from "base64url";
import {useLocation} from "react-router-dom";
import queryString from "query-string";
import PasswordStrengthBar from "../3rdparty/react-password-strength-bar";

export type InitialPage = "service" | "source" | "destination" | "key";

export interface InitialSetupProps {
    originalConfig: BackupConfiguration,
    currentConfig: BackupConfiguration,
    backendState: BackupState,
    rebuildAvailable: boolean,
    page: InitialPage,
    initialSource: (source?: SourceResponse) => void,
    updatedToken: () => Promise<void>,
    onPageChange: (page: InitialPage) => void,
    configUpdated: (valid: boolean, configuration: BackupConfiguration, password?: string, secretRegion?: string) => void
}

interface InitialSetupState {
    busy: boolean,
    manifest: BackupManifest,
    sourceName: string,
    password: string,
    passwordReclaim: string,
    passwordConfirm: string,
    passwordValid: boolean,
    saveSecret: boolean,
    haveSecret?: boolean,
    secretRegion: string,
    showChangePassword: boolean,
    sourceList: SourceResponse[] | undefined,
    selectedSource: SourceResponse | undefined
}

const freeFeatures = [
    "Safe encryption key recovery",
    "Easily manage backup sources",
    "Create and accept sharing invites"
]

const premiumFeatures = [
    "Service provided backup storage",
    "Multiple region support to satisfy latency and data governance requirements",
    "Easy and secure zero trust sharing without additional credential setup"
]

function formatDate(lastUsage: number | undefined) {
    if (lastUsage) {
        const d = new Date(0);
        d.setUTCSeconds(lastUsage);
        return ", last updated " + d.toLocaleDateString();
    }
    return "";
}

export function formatNumber(num: number) {
    return num.toLocaleString();
}

function getSecretRegionFromDestination(configuration: BackupConfiguration) {
    if (configuration.destinations
        && configuration.destinations[configuration.manifest.destination]
        && configuration.destinations[configuration.manifest.destination].type === "UB") {
        return configuration.destinations[configuration.manifest.destination].endpointUri;
    }
    return "us-west";
}

export default function InitialSetup(props: InitialSetupProps) {
    const location = useLocation();
    const [state, setState] = React.useState(() => ({
        busy: false,
        passwordValid: false,
        password: "",
        passwordReclaim: "",
        passwordConfirm: "",
        sourceName: props.backendState.sourceName,
        manifest: props.currentConfig && props.currentConfig.manifest ? props.currentConfig.manifest : {
            destination: "do",
            scheduleRandomize: {
                duration: 1,
                unit: "HOURS"
            }
        },
        showChangePassword: false,
        saveSecret: !!window.localStorage.getItem("email"),
        secretRegion: getSecretRegionFromDestination(props.currentConfig),
        selectedSource: undefined,
        sourceList: undefined
    } as InitialSetupState))

    async function fetchSources() {
        if (!state.sourceList) {
            const sources = await listSources(false);
            setState((oldState) => ({
                ...oldState,
                sourceList: sources.sources
            }));
        }
    }

    function getSecretRegion() {
        if (props.backendState.serviceSourceId && window.localStorage.getItem("email") && state.saveSecret) {
            return state.secretRegion;
        }
        return undefined;
    }

    const query = queryString.parse(location.search);
    if (query.refresh) {
        window.location.reload();
    }

    useEffect(() => {
        if (props.page === "service" && props.backendState.serviceConnected) {
            if (props.backendState.serviceSourceId) {
                if (props.currentConfig.destinations[props.currentConfig.manifest.destination]) {
                    props.onPageChange("key");
                    props.configUpdated(false, props.currentConfig,
                        getSecretRegion());
                } else {
                    props.onPageChange("destination");
                }
            } else {
                props.onPageChange("source");
            }
        }
    }, [props.backendState.serviceConnected, props.backendState.serviceSourceId]);

    useEffect(() => {
        const newRegion = getSecretRegionFromDestination(props.currentConfig);
        if (newRegion) {
            setState((oldState) => ({
                ...oldState,
                secretRegion: newRegion
            }));
        }
    }, [props.currentConfig.destinations])

    useEffect(() => {
        if (props.page === "source") {
            fetchSources();
        }
    }, [props.page]);

    function configurationUpdate(valid: boolean, val?: BackupDestination) {
        if (valid) {
            var manifest = {
                destination: "d0",
                optimizeSchedule: "0 0 1 * *"
            };
            if (props.currentConfig && props.currentConfig.manifest) {
                manifest = {
                    ...props.currentConfig.manifest,
                    ...manifest
                }
            }
            const config = {
                ...props.currentConfig,
                destinations: {
                    "d0": val as BackupDestination
                },
                manifest: manifest
            };

            props.configUpdated(true, config,
                getSecretRegion());
        } else {
            props.configUpdated(false, props.currentConfig,
                getSecretRegion())
        }
    }

    let destinationId: string | null = window.sessionStorage.getItem("destinationId");
    if (destinationId) {
        const pendingDestination = JSON.parse(window.sessionStorage.getItem("destination") as string);
        props.currentConfig.destinations[destinationId] = pendingDestination;
    }

    function updateState(newState: InitialSetupState) {
        setState(newState);
        if (((props.rebuildAvailable || state.selectedSource) && newState.password) ||
            (newState.password === newState.passwordConfirm && newState.passwordValid)) {
            props.configUpdated(true, {
                    ...props.currentConfig,
                    manifest: state.manifest
                }, newState.password,
                getSecretRegion());
        } else {
            props.configUpdated(false, {
                    ...props.currentConfig,
                    manifest: state.manifest
                }, newState.password,
                getSecretRegion());
        }
    }

    async function newSource() {
        setState((oldState) => ({
            ...oldState,
            busy: true
        }));
        try {
            await createSource(state.sourceName);
            props.updatedToken();
        } finally {
            props.initialSource(undefined);
            props.onPageChange("destination");
            setState((oldState) => ({
                ...oldState,
                selectedSource: undefined,
                busy: false,
            }));
        }
    }

    async function adoptSource(sourceId: string, name: string) {
        setState((oldState) => ({
            ...oldState,
            busy: true
        }));

        const source = state.sourceList?.find(item => item.sourceId === sourceId);

        try {
            if (source) {
                if (source.destination && source.encryptionMode && source.key) {
                    props.initialSource(source);
                    setState({
                        ...state,
                        selectedSource: source
                    });
                } else {
                    await updateSource(name, sourceId);
                    props.onPageChange("destination");
                }
            }
        } finally {
            setState((oldState) => ({
                ...oldState,
                busy: false,
            }));
        }
    }

    function reclaimPassword() {
        window.sessionStorage.setItem("newPassword", state.passwordReclaim);
        authorizationRedirect("/?refresh=true",
            `emailHash=${
                encodeURIComponent(hash(base64url.decode(window.localStorage.getItem("email") as string)))}&sourceId=${
                state.selectedSource?.sourceId}&region=${
                state.selectedSource?.secretRegion}`,
            `sourceId=${encodeURIComponent(state.selectedSource?.sourceId as string)}&region=${
                encodeURIComponent(state.selectedSource?.secretRegion as string)}`);
    }

    function handleChangePasswordClose() {
        setState({
            ...state,
            showChangePassword: false
        });
    }

    function updatedPasswordScore(newScore: number) {
        setState((oldState) => ({
            ...oldState,
            passwordValid: newScore >= 2
        }));
    }

    if (props.page === "service") {
        return <Stack spacing={2} style={{width: "100%"}}>
            <Paper sx={{
                p: 2,
            }}>
                <Typography variant="h3" component="div" marginBottom={"16px"}>
                    Connect with online service?
                </Typography>
                <Typography variant="h6" component="div">
                    Included for free
                </Typography>
                <List>
                    {freeFeatures.map(item =>
                        <ListItem key={freeFeatures.indexOf(item)}>
                            <ListItemIcon
                                sx={{
                                    minWidth: "34px",
                                    color: "success.main",
                                }}
                            >
                                <CheckIcon/>
                            </ListItemIcon>
                            <ListItemText>
                                {item}
                            </ListItemText>
                        </ListItem>)
                    }
                </List>
                <Typography variant="h6" component="div">
                    Additional features with paid subscription
                </Typography>
                <List>
                    {premiumFeatures.map(item =>
                        <ListItem key={premiumFeatures.indexOf(item)}>
                            <ListItemIcon
                                sx={{
                                    minWidth: "34px",
                                    color: "success.main",
                                }}
                            >
                                <CheckIcon/>
                            </ListItemIcon>
                            <ListItemText>
                                {item}
                            </ListItemText>
                        </ListItem>)
                    }
                </List>
                <Grid container spacing={2} alignItems={"center"}>
                    <ServiceAuthentication includeSkip={true} backendState={props.backendState}
                                           updatedToken={() => props.updatedToken()}
                                           needSubscription={false}
                                           onSkip={() =>
                                               props.backendState.serviceConnected ?
                                                   props.onPageChange("source") :
                                                   props.onPageChange("destination")}/>
                </Grid>
            </Paper>
        </Stack>;
    } else if (props.page === "source") {
        return <Stack spacing={2} style={{width: "100%"}}>
            <Paper sx={{
                p: 2,
            }}>
                {state.sourceList && state.sourceList.length > 0 ?
                    <>
                        <Typography variant="h3" component="div" marginBottom={"16px"}>
                            Adopt existing source?
                        </Typography>
                        <Typography variant="body1" component="div">
                            <p>
                                Would you like to adopt an existing backup source or start a new one?
                            </p>
                            <Alert severity="warning">
                                Adopting an existing source will stop any backup currently backing up to that source.
                            </Alert>
                        </Typography>
                    </>
                    :
                    <Typography variant="h3" component="div" marginBottom={"16px"}>
                        Name your backup source
                    </Typography>
                }

                <Grid container spacing={2} alignItems={"center"} marginTop={"8px"}>
                    {state.sourceList ?
                        <>
                            {state.sourceList.length > 0 &&
                                <Grid item xs={12}>
                                    <Typography variant="h6" component="div" marginBottom={"16px"}>
                                        Existing backup sources
                                    </Typography>
                                </Grid>
                            }
                            {state.sourceList.map(source =>
                                <Fragment key={source.sourceId}>
                                    <Grid item md={9} xs={12}>
                                        <b>{source.name}</b>

                                        {(!source.destination || !source.key || !source.encryptionMode) ?
                                            <> (No configuration)</>
                                            :
                                            <>
                                                {source.dailyUsage.length > 0 && source.dailyUsage[0].usage ?
                                                    <> ({formatNumber(source.dailyUsage[0].usage)} GB{formatDate(source.lastUsage)})</>
                                                    :
                                                    <> (No service data)</>
                                                }
                                            </>
                                        }
                                    </Grid>
                                    <Grid item md={3} xs={12}>
                                        <Button fullWidth={true} disabled={state.busy} variant="contained"
                                                id="newSource"
                                                onClick={() => adoptSource(source.sourceId, source.name)}>
                                            Adopt
                                        </Button>
                                    </Grid>
                                </Fragment>
                            )}
                        </>
                        :
                        <Grid item xs={12}>
                            <Box textAlign={"center"}>
                                <CircularProgress/>
                            </Box>
                        </Grid>
                    }
                    <Grid item xs={12}>
                        <Typography variant="h6" component="div" marginBottom={"16px"}>
                            Create a new backup source
                        </Typography>
                    </Grid>
                    <Grid item md={9} xs={12}>
                        <TextField
                            fullWidth={true}
                            value={state.sourceName}
                            onChange={e => setState({
                                ...state,
                                sourceName: e.target.value
                            })}
                        />
                    </Grid>
                    <Grid item md={3} xs={12}>
                        <Button fullWidth={true} disabled={state.busy} variant="contained" id="newSource"
                                onClick={() => newSource()}>
                            <>New Source</>
                        </Button>
                    </Grid>
                </Grid>
            </Paper>
        </Stack>;
    } else if (props.page === "destination") {
        return <Stack spacing={2} style={{width: "100%"}}>
            <Paper sx={{
                p: 2,
            }}>
                <Typography variant="h3" component="div">
                    Specify backup destination for metadata
                </Typography>
                <Typography variant="body1" component="div">
                    <p>
                        Now you need to specify is where the metadata for your backup should be placed.
                    </p>
                </Typography>
                <Typography variant="body1" component="div">
                    <p>
                        If you are planning to restore from an existing backup you need to specify the
                        same metadata location as your backup was pointing to before.
                    </p>
                </Typography>
                <Destination manifestDestination={true}
                             destinationUpdated={configurationUpdate}
                             id={"d0"}
                             backendState={props.backendState}
                             destination={
                                 props.currentConfig &&
                                 props.currentConfig.manifest &&
                                 props.currentConfig.manifest.destination &&
                                 props.currentConfig.destinations
                                     ? props.currentConfig.destinations[props.currentConfig.manifest.destination]
                                     : {type: "UB", endpointUri: ""}
                             }/>
            </Paper>
        </Stack>;
    } else if (props.rebuildAvailable || state.selectedSource) {
        return <Stack spacing={2} style={{width: "100%"}}>
            <Paper sx={{
                p: 2
            }}>
                <Typography variant="h3" component="div">
                    Restoring from backup
                </Typography>
                <Typography variant="body1" component="div">
                    <p>
                        The destination you have chosen already contains a backup. To start the restore
                        you need to provide the original password used to create the backup.
                    </p>
                </Typography>
                <Alert severity="info">
                    The metadata for the backup will need to be downloaded that might take
                    a few minutes before you can start actually restoring data.
                </Alert>
                <Box component="div"
                     sx={{
                         '& .MuiTextField-root': {m: 1},
                     }}
                     style={{marginTop: 4, marginLeft: "-8px", marginRight: "8px"}}>
                    <TextField label="Password" variant="outlined"
                               fullWidth={true}
                               required={true}
                               id={"restorePassword"}
                               value={state.password}
                               error={!state.password}
                               type="password"
                               onChange={(e) => updateState({
                                   ...state,
                                   password: e.target.value
                               })}/>
                </Box>
                {state.selectedSource && state.selectedSource.secretRegion && window.localStorage.getItem("email") &&
                    <Box component="div" textAlign={"center"}>
                        <Button variant="contained" id="reclaimPassword" color={"error"}
                                onClick={() => setState({...state, showChangePassword: true})}>
                            Recover private key
                        </Button>
                    </Box>
                }
            </Paper>
            <Dialog open={state.showChangePassword} onClose={handleChangePasswordClose}>
                <DialogTitle>New Password</DialogTitle>
                <DialogContent>
                    <DialogContentText>
                        Choose a new password used to protect your backup.
                    </DialogContentText>

                    <PasswordStrengthBar password={state.passwordReclaim} onChangeScore={(newScore) => updatedPasswordScore(newScore)}/>
                    <Box
                        component="div"
                        sx={{
                            '& .MuiTextField-root': {m: 1},
                        }}
                        style={{marginTop: 4, marginLeft: "-8px", marginRight: "8px"}}
                    >
                        <TextField label="New Password" variant="outlined"
                                   fullWidth={true}
                                   required={true}
                                   value={state.passwordReclaim}
                                   error={!state.passwordReclaim}
                                   id={"passwordReclaim"}
                                   type="password"
                                   onChange={(e) => setState({
                                       ...state,
                                       passwordReclaim: e.target.value
                                   })}/>
                        <TextField label="Confirm New Password" variant="outlined"
                                   fullWidth={true}
                                   required={true}
                                   helperText={state.passwordConfirm !== state.passwordReclaim ? "Does not match" : null}
                                   value={state.passwordConfirm}
                                   error={state.passwordConfirm !== state.passwordReclaim || !state.passwordReclaim}
                                   id={"passwordReclaimSecond"}
                                   type="password"
                                   onChange={(e) => setState({
                                       ...state,
                                       passwordConfirm: e.target.value
                                   })}/>
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={handleChangePasswordClose}>Cancel</Button>
                    <Button disabled={!state.passwordValid || state.passwordReclaim !== state.passwordConfirm}
                            onClick={() => reclaimPassword()} id={"submitPasswordChange"}>OK</Button>
                </DialogActions>
            </Dialog>
        </Stack>
    } else {
        return <Stack spacing={2} style={{width: "100%"}}>
            <Paper sx={{p: 2}}>
                <Typography variant="h3" component="div">
                    Enter backup password
                </Typography>
                <Typography variant="body1" component="div">
                    <p>
                        Enter the password with which your backup will be protected.
                    </p>
                </Typography>
                <Alert severity="warning">Please keep your password safe.
                    There is no way to recover a lost password unless you enable password recovery!</Alert>

                <PasswordStrengthBar password={state.password} onChangeScore={(newScore) => updatedPasswordScore(newScore)}/>

                <Box
                    component="div"
                    sx={{
                        '& .MuiTextField-root': {m: 1},
                    }}
                    style={{marginTop: "4px", marginLeft: "-8px", marginRight: "8px"}}
                >
                    <TextField label="Password" variant="outlined"
                               fullWidth={true}
                               required={true}
                               value={state.password}
                               error={!state.password}
                               id={"passwordFirst"}
                               type="password"
                               onChange={(e) => updateState({
                                   ...state,
                                   password: e.target.value
                               })}/>

                    <TextField label="Confirm Password" variant="outlined"
                               fullWidth={true}
                               required={true}
                               helperText={state.passwordConfirm !== state.password ? "Does not match" : null}
                               value={state.passwordConfirm}
                               error={state.passwordConfirm !== state.password || !state.password}
                               id={"passwordSecond"}
                               type="password"
                               onChange={(e) => updateState({
                                   ...state,
                                   passwordConfirm: e.target.value
                               })}/>
                </Box>
            </Paper>

            <Paper sx={{p: 2}}>
                <Grid container spacing={2} alignItems={"center"}>
                    <Grid item xs={12}>
                        <DividerWithText>Password recovery</DividerWithText>
                    </Grid>
                    <Grid item md={6} xs={12}>
                        <FormControlLabel control={<Checkbox
                            disabled={!props.backendState.serviceSourceId || !window.localStorage.getItem("email")}
                            checked={state.saveSecret && !!window.localStorage.getItem("email")}
                            onChange={(e) => updateState({
                                ...state,
                                saveSecret: e.target.checked
                            })}
                        />} label="Enable encryption password recovery from"/>

                    </Grid>
                    <Grid item md={6} xs={12}>
                        <Select style={{marginRight: "8px"}}
                                fullWidth={true}
                                value={state.secretRegion}
                                disabled={!props.backendState.serviceSourceId || !state.saveSecret}
                                label="Region"
                                onChange={(event: SelectChangeEvent) => {
                                    const newState = {}
                                    setState((oldState) => ({
                                        ...state,
                                        secretRegion: event.target.value as string,
                                    }));
                                }}>
                            <MenuItem value={"us-west"}>North America (Oregon)</MenuItem>
                            <MenuItem value={"eu-central"}>Europe (Frankfurt)</MenuItem>
                            <MenuItem value={"ap-southeast"}>Asia (Singapore)</MenuItem>
                        </Select>
                    </Grid>
                    <Grid item xs={12}>
                        { !!window.localStorage.getItem("email") ?
                            <Alert severity="warning">Enabling password recover will store your private
                                encryption key with online service!</Alert>
                            :
                            <Alert severity="warning">This option is only available if you are using a
                                service account and complete the setup in a single browser session!
                                { !!props.backendState.serviceSourceId &&
                                    <span> To enable go back to the beginning of the setup, disconnect from the service and reconnect!</span>
                                }
                            </Alert>
                        }
                    </Grid>
                </Grid>
            </Paper>

            <UIAuthentication manifest={state.manifest} onChange={(manifest => updateState({
                ...state,
                manifest: manifest
            }))}/>
        </Stack>
    }
}