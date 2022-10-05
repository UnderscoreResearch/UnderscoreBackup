import * as React from 'react';
import {styled} from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import MuiDrawer from '@mui/material/Drawer';
import Box from '@mui/material/Box';
import MuiAppBar, {AppBarProps as MuiAppBarProps} from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import Slide from '@mui/material/Slide';
import Typography from '@mui/material/Typography';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import MenuIcon from '@mui/icons-material/Menu';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import {
    BackupConfiguration,
    BackupDefaults,
    BackupDestination,
    BackupSet,
    BackupSetRoot,
    DestinationMap,
    GetActivity,
    GetConfiguration,
    GetDefaults,
    GetDestinationFiles,
    GetEncryptionKey,
    PostConfiguration,
    PostRemoteRestore,
    PostRestartSets,
    PostRestore,
    PutEncryptionKey,
    StatusLine
} from './api';
import NavigationMenu from './components/NavigationMenu';
import Settings from './components/Settings';
import {Route, Routes, useNavigate,} from "react-router-dom";
import Status from "./components/Status";
import Destinations, {DestinationProp} from "./components/Destinations";
import Sets from "./components/Sets";
import {Backdrop, Button, CircularProgress} from "@mui/material";
import InitialSetup from "./components/InitialSetup";
import Restore, {RestorePropsChange} from "./components/Restore";
import lodashObject from 'lodash';

const drawerWidth: number = 240;

interface AppBarProps extends MuiAppBarProps {
    open?: boolean;
}

const AppBar = styled(MuiAppBar, {
    shouldForwardProp: (prop) => prop !== 'open',
})<AppBarProps>(({theme, open}) => ({
    zIndex: theme.zIndex.drawer + 1,
    transition: theme.transitions.create(['width', 'margin'], {
        easing: theme.transitions.easing.sharp,
        duration: theme.transitions.duration.leavingScreen,
    }),
    ...(open && {
        marginLeft: drawerWidth,
        width: `calc(100% - ${drawerWidth}px)`,
        transition: theme.transitions.create(['width', 'margin'], {
            easing: theme.transitions.easing.sharp,
            duration: theme.transitions.duration.enteringScreen,
        }),
    }),
}));

const Drawer = styled(MuiDrawer, {shouldForwardProp: (prop) => prop !== 'open'})(
    ({theme, open}) => ({
        '& .MuiDrawer-paper': {
            position: 'relative',
            whiteSpace: 'nowrap',
            width: drawerWidth,
            transition: theme.transitions.create('width', {
                easing: theme.transitions.easing.sharp,
                duration: theme.transitions.duration.enteringScreen,
            }),
            boxSizing: 'border-box',
            ...(!open && {
                overflowX: 'hidden',
                transition: theme.transitions.create('width', {
                    easing: theme.transitions.easing.sharp,
                    duration: theme.transitions.duration.leavingScreen,
                }),
                width: theme.spacing(7),
                [theme.breakpoints.up('sm')]: {
                    width: theme.spacing(9),
                },
            }),
        },
    }),
);

interface MainAppState {
    open: boolean,
    rebuildAvailable: boolean,
    hasKey: boolean,
    loading: boolean,
    unresponsive: boolean,
    initialLoad: boolean,
    originalConfiguration: BackupConfiguration,
    currentConfiguration: BackupConfiguration,
    passphrase?: string,
    validatedPassphrase: boolean,
    initialValid: boolean,
    destinationsValid: boolean,
    setsValid: boolean,
    restoreDestination?: string,
    restoreRoots: BackupSetRoot[],
    restoreTimestamp?: Date,
    restoreIncludeDeleted?: boolean,
    restoreOverwrite: boolean,
    defaults?: BackupDefaults,
    activity: StatusLine[]
}

var lastActivity: StatusLine[] = [];

function defaultState(): MainAppState {
    const roots: BackupSetRoot[] = [];

    function defaultConfig(): BackupConfiguration {
        return {
            destinations: {},
            sets: [],
            manifest: {
                destination: ''
            }
        }
    }

    const activity: StatusLine[] = [];

    return {
        open: true,
        rebuildAvailable: false,
        hasKey: false,
        loading: true,
        unresponsive: false,
        initialLoad: true,
        originalConfiguration: defaultConfig(),
        currentConfiguration: defaultConfig(),
        restoreRoots: roots,
        restoreOverwrite: false,
        initialValid: false,
        destinationsValid: true,
        setsValid: true,
        passphrase: undefined,
        validatedPassphrase: false,
        activity: activity
    };
}

export default function MainApp() {
    const [state, setState] = React.useState(() => defaultState());

    const page = window.location.href.endsWith("restore") ? "restore" : "backup";
    const navigate = useNavigate();

    const toggleDrawer = () => {
        setState({
            ...state,
            ...{open: !state.open}
        })
    };

    async function fetchConfig() {
        var newState = {} as MainAppState;
        newState.loading = false;
        newState.initialLoad = false;
        const newConfig = await GetConfiguration();
        try {
            if (newConfig !== undefined) {
                var newRebuildAvailable = false;
                if (Object.keys(newConfig.destinations).length > 0) {
                    const hasKey = await GetEncryptionKey(state.passphrase);
                    if (hasKey && state.passphrase) {
                        newState.validatedPassphrase = true;
                    }
                    if (!hasKey && newConfig.manifest.destination) {
                        const existingFiles = new Set<String>();
                        const destinationFiles = await GetDestinationFiles("/", newConfig.manifest.destination);
                        if (destinationFiles !== undefined) {
                            destinationFiles.forEach((e) => existingFiles.add(e.path));
                        }
                        if (existingFiles.has("/configuration.json") && existingFiles.has("/publickey.json")) {
                            newRebuildAvailable = true;
                        }
                    } else {
                        newState.hasKey = true;
                    }
                }
                newState.originalConfiguration = newConfig;
                newState.currentConfiguration = JSON.parse(JSON.stringify(newConfig));
                newState.rebuildAvailable = newRebuildAvailable;
                newState.initialValid = true;
                newState.defaults = await GetDefaults();
                if (newState.currentConfiguration.sets.length == 0) {
                    newState.currentConfiguration.sets.push({...newState.defaults.set});
                }
            }
        } finally {
            setState((oldState) => ({
                ...oldState,
                ...newState
            }));
        }
    }

    async function applyConfig(newState?: MainAppState) {
        var currentState = newState !== undefined ? newState : state;
        setState((oldState) => {
            return {
                ...oldState,
                loading: true
            }
        })
        if (await PostConfiguration(currentState.currentConfiguration)) {
            await fetchActivity();
            await fetchConfig();
        } else {
            setState((oldState) => ({
                ...oldState,
                ...{
                    loading: false
                }
            } as MainAppState));
        }
    }

    async function fetchActivity() {
        const activity = await GetActivity(false);
        if (activity === undefined) {
            setState((oldState) => {
                return {
                    ...oldState,
                    unresponsive: true
                }
            });
        } else if (state.unresponsive || JSON.stringify(activity) !== JSON.stringify(lastActivity)) {
            lastActivity = activity;

            setState((oldState) => ({
                ...oldState,
                activity: activity,
                unresponsive: false
            } as MainAppState));
        }
    }

    async function applyPassphrase() {
        if (state.passphrase) {
            setState((oldState) => ({
                ...oldState,
                ...{
                    loading: true
                }
            } as MainAppState));

            if (state.hasKey) {
                let success = await GetEncryptionKey(state.passphrase);
                setState((oldState) => ({
                    ...oldState,
                    ...{
                        validatedPassphrase: success,
                        loading: false
                    }
                } as MainAppState));
            } else {
                var success;
                if (state.rebuildAvailable) {
                    success = await PostRemoteRestore(state.passphrase);
                } else {
                    success = await PutEncryptionKey(state.passphrase);
                }
                if (success) {
                    await fetchConfig();
                } else {
                    setState((oldState) => ({
                        ...oldState,
                        ...{
                            loading: false
                        }
                    } as MainAppState));
                }
            }
        }
    }

    function updateInitialConfig(valid: boolean, newConfig: BackupConfiguration, passphrase?: string) {
        setState({
            ...state,
            initialValid: valid,
            currentConfiguration: newConfig,
            passphrase: passphrase
        })
    }

    function updateSets(valid: boolean, sets: BackupSet[]) {
        setState({
            ...state,
            currentConfiguration: {
                ...(state.currentConfiguration),
                sets: sets
            },
            setsValid: valid
        });
    }

    function updateDestinations(valid: boolean, destinations: DestinationProp[]) {
        let newVal: DestinationMap = {};
        destinations.forEach((item) => {
            newVal[item.id] = item.destination;
        })

        setState({
            ...state,
            currentConfiguration: {
                ...state.currentConfiguration,
                destinations: newVal
            },
            destinationsValid: valid
        });
    }

    function getDestinationList(): DestinationProp[] {
        const keys = Object.keys(state.currentConfiguration.destinations);
        keys.sort();

        return keys.map(key => {
            return {
                destination: state.currentConfiguration.destinations[key] as BackupDestination,
                id: key
            }
        });
    }

    function updateConfig(newConfig: BackupConfiguration): void {
        setState({
            ...state,
            currentConfiguration: newConfig
        });
    }

    function updateRestore(newState: RestorePropsChange): void {
        setState({
            ...state,
            passphrase: newState.passphrase,
            restoreDestination: newState.destination,
            restoreRoots: newState.roots,
            restoreTimestamp: newState.timestamp,
            restoreIncludeDeleted: newState.includeDeleted,
            restoreOverwrite: newState.overwrite
        });
    }

    async function startRestore() {
        setState({
            ...state,
            loading: true
        });
        await PostRestore({
            passphrase: state.passphrase ? state.passphrase : "",
            destination: state.restoreDestination,
            files: state.restoreRoots,
            overwrite: state.restoreOverwrite,
            timestamp: state.restoreTimestamp ? state.restoreTimestamp.getTime() : undefined,
            includeDeleted: state.restoreIncludeDeleted ? state.restoreIncludeDeleted : false
        });

        await fetchActivity();

        setState((oldState) => {
            return {
                ...oldState,
                loading: false,
                restoreOverwrite: false,
                restoreTimestamp: undefined,
                restoreRoots: [],
                restoreDestination: undefined
            }
        });

        navigate("status");
    }

    React.useEffect(() => {
        fetchActivity();
        fetchConfig();
        const timer = setInterval(fetchActivity, 2000);
        return () => clearInterval(timer);
    }, []);

    React.useEffect(() => {
        setState((oldState) => oldState);
    }, [location.pathname]);

    let currentProgress;
    let acceptButtonTitle: string = "";
    let cancelButtonTitle = "";
    let allowRestore = true;
    let allowBackup = true;
    let contents;

    function calculateStatus() {
        if (!state.activity || state.activity.length == 0) {
            currentProgress = "Currently Inactive";
        } else if (state.activity.some(item => item.code.startsWith("UPLOAD_PENDING"))) {
            currentProgress = "Initializing Backup";
        } else if (state.activity.some(item => item.code.startsWith("BACKUP_"))) {
            currentProgress = "Backup In Progress";
        } else if (state.activity.some(item => item.code.startsWith("TRIMMING_"))) {
            currentProgress = "Trimming Repository";
        } else if (state.activity.some(item => item.code.startsWith("VALIDATE_"))) {
            currentProgress = "Validating Repository";
        } else if (state.activity.some(item => item.code.startsWith("RESTORE_"))) {
            currentProgress = "Restore In Progress";
            allowRestore = false;
            allowBackup = false;
        } else if (state.activity.some(item => item.code.startsWith("REPLAY_"))) {
            currentProgress = "Replaying From Backup";
            allowRestore = false;
            allowBackup = false;
        } else if (state.activity.some(item => item.code.startsWith("OPTIMIZING_"))) {
            currentProgress = "Optimizing Log";
            allowRestore = false;
            allowBackup = false;
        } else {
            currentProgress = "Currently Inactive"
        }
    }

    function completedSetup() {
        return Object.keys(state.originalConfiguration.destinations).length > 0 && state.hasKey;
    }

    if (state.unresponsive) {
        currentProgress = "Application Unresponsive";
        contents = <div/>
    } else if (state.initialLoad) {
        currentProgress = "Loading";
        contents = <div/>
    } else if (completedSetup() && state.defaults) {
        calculateStatus();
        if (page === "restore") {
            acceptButtonTitle = "Restore";
        } else if (allowBackup) {
            acceptButtonTitle = "Save";
        } else if (currentProgress == "Restore In Progress") {
            acceptButtonTitle = "Cancel Restore";
        }

        contents = <Routes>
            <Route path="/" element={state.originalConfiguration.sets.length > 0 ?
                <Status status={state.activity}/> :
                <Sets sets={state.currentConfiguration.sets}
                      defaults={state.defaults}
                      destinations={getDestinationList()}
                      configurationUpdated={updateSets}/>
            }/>
            <Route path="status" element={<Status status={state.activity}/>}/>
            <Route path="settings"
                   element={<Settings config={state.currentConfiguration} onChange={updateConfig}/>}/>
            <Route path="restore" element={<Restore passphrase={state.passphrase}
                                                    destination={state.restoreDestination}
                                                    defaultDestination={state.defaults.defaultRestoreFolder}
                                                    defaults={state.defaults}
                                                    roots={state.restoreRoots}
                                                    overwrite={state.restoreOverwrite}
                                                    timestamp={state.restoreTimestamp}
                                                    validatedPassphrase={state.validatedPassphrase}
                                                    onSubmit={applyChanges}
                                                    onChange={updateRestore}/>}/>
            <Route path="destinations" element={<Destinations destinations={getDestinationList()}
                                                              dontDelete={[state.currentConfiguration.manifest.destination]}
                                                              configurationUpdated={updateDestinations}/>}/>
            <Route path="sets" element={<Sets sets={state.currentConfiguration.sets}
                                              defaults={state.defaults}
                                              destinations={getDestinationList()}
                                              configurationUpdated={updateSets}/>
            }/>
        </Routes>;
    } else {
        allowBackup = false;
        allowRestore = false;
        acceptButtonTitle = "Next";
        currentProgress = "Initial Setup";
        contents = <InitialSetup
            originalConfig={state.originalConfiguration}
            currentConfig={state.currentConfiguration}
            configUpdated={(valid, newConfig, passphrase) => updateInitialConfig(valid, newConfig, passphrase)}
            rebuildAvailable={state.rebuildAvailable}/>
    }


    let valid = false;
    let hasChanges = false;
    if (!allowBackup) {
        valid = true;
        if (!state.hasKey && state.originalConfiguration.destinations && Object.keys(state.originalConfiguration.destinations).length > 0) {
            cancelButtonTitle = "Back";
        }
    } else {
        valid = state.initialValid && state.destinationsValid && state.setsValid;
        if (lodashObject.isEqual(state.originalConfiguration, state.currentConfiguration)) {
            if (state.hasKey || !state.passphrase) {
                valid = false;
            }
        } else {
            hasChanges = true;
            if (completedSetup()) {
                cancelButtonTitle = "Revert";
            }
            allowRestore = false;
        }
    }

    if (!valid) {
        if (page === "restore") {
            const validConfig = state.hasKey && state.originalConfiguration.sets.length > 0;
            if (!state.validatedPassphrase) {
                valid = validConfig && !(!state.passphrase);
                acceptButtonTitle = "Validate Passphrase";
            } else {
                valid = validConfig && state.restoreRoots.length > 0;
                acceptButtonTitle = "Start Restore";
            }
        } else {
            if (currentProgress == "Currently Inactive" && state.originalConfiguration.sets.length > 0 && !hasChanges) {
                valid = true;
                acceptButtonTitle = "Start Backup";
            } else if (currentProgress === "Backup In Progress"
                || currentProgress === "Initializing Backup"
                || currentProgress === "Trimming Repository"
                || currentProgress === "Validating Repository") {
                valid = true;
                acceptButtonTitle = "Pause Backup";
            }
        }
    }

    function changeBackup(start: boolean) {
        if (start && state.currentConfiguration.manifest.interactiveBackup) {
            PostRestartSets();
        } else {
            const newState = {
                ...state,
                currentConfiguration: {
                    ...state.currentConfiguration,
                    manifest: {
                        ...state.currentConfiguration.manifest,
                        interactiveBackup: start
                    }
                }
            }
            setState(newState);
            applyConfig(newState);
        }
    }

    function applyChanges() {
        if (valid) {
            if (!allowBackup) {
                if (!state.validatedPassphrase && state.passphrase) {
                    applyPassphrase();
                } else {
                    applyConfig();
                }
                if (!completedSetup()) {
                    navigate("sets");
                }
            } else if (page === "restore") {
                if (!state.validatedPassphrase && state.passphrase) {
                    applyPassphrase();
                } else {
                    startRestore();
                }
            } else {
                if (!lodashObject.isEqual(state.originalConfiguration, state.currentConfiguration)) {
                    applyConfig();
                } else {
                    if (completedSetup()) {
                        if (acceptButtonTitle == "Start Backup") {
                            changeBackup(true);
                        } else if (acceptButtonTitle == "Pause Backup") {
                            changeBackup(false);
                        }
                    } else if (!lodashObject.isEqual(state.originalConfiguration, state.currentConfiguration)) {
                        applyConfig();
                    }
                }
            }
        }
        return false;
    }

    function applyCancel() {
        if (completedSetup()) {
            location.reload();
        } else {
            setState({
                ...state,
                originalConfiguration: {
                    ...state.originalConfiguration,
                    destinations: {}
                }
            });
        }
    }

    return (
        <Box sx={{display: 'flex'}}>
            <CssBaseline/>
            <AppBar position="absolute" open={state.open}>
                <Toolbar
                    sx={{
                        pr: '24px', // keep right padding when drawer closed
                    }}
                >
                    <IconButton
                        edge="start"
                        color="inherit"
                        aria-label="open drawer"
                        onClick={toggleDrawer}
                        sx={{
                            marginRight: '36px',
                            ...(state.open && {display: 'none'}),
                        }}
                    >
                        <MenuIcon/>
                    </IconButton>
                    <Typography
                        id={"currentProgress"}
                        component="h1"
                        variant="h6"
                        color="inherit"
                        noWrap
                        sx={{
                            flexGrow: 1
                        }}
                    >
                                <span style={{
                                    ...(state.open && {display: 'none'})
                                }
                                }>Underscore Backup -&nbsp;
                                </span>
                        {currentProgress}
                    </Typography>
                    <Box sx={{flexGrow: 1, display: {xs: 'none', md: 'flex'}}}>
                    </Box>

                    {cancelButtonTitle &&
                        <Box sx={{flexGrow: 0, marginRight: "2em"}}>
                            <Button sx={{my: 2, color: 'white', display: 'block'}}
                                    id={"cancelButton"}
                                    onClick={() => applyCancel()}>
                                {cancelButtonTitle}
                            </Button>
                        </Box>
                    }

                    {acceptButtonTitle &&
                        <Box sx={{flexGrow: 0}}>
                            <Button sx={{my: 2, color: 'white', display: 'block'}}
                                    variant="contained"
                                    color={acceptButtonTitle !== "Cancel Restore" ? "success" : "error"}
                                    disabled={!valid}
                                    id={"acceptButton"}
                                    onClick={() => applyChanges()}>
                                {acceptButtonTitle}
                            </Button>
                        </Box>
                    }
                </Toolbar>
            </AppBar>
            <Drawer variant="permanent" open={state.open}>
                <Toolbar
                    sx={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'flex-end',
                        px: [1],
                    }}
                >
                    <Typography
                        component="h1"
                        variant="h6"
                        color="inherit"
                        noWrap
                        sx={{flexGrow: 1}}
                    >
                        Underscore Backup
                    </Typography>
                    <IconButton onClick={toggleDrawer}>
                        <ChevronLeftIcon/>
                    </IconButton>
                </Toolbar>
                <Divider/>
                {
                    !state.initialLoad &&
                    <NavigationMenu config={state.originalConfiguration}
                                    allowRestore={allowRestore}
                                    allowBackup={allowBackup}
                                    unresponsive={state.unresponsive}
                                    hasKey={state.hasKey}/>

                }

                <Slide direction="right" in={state.open}>
                    <Typography
                        color="darkgray"
                        noWrap
                        fontSize={14}
                        style={{position: "absolute", bottom: "8px", right: "8px", width: "220px", textAlign: "right"}}
                    >
                        {state.defaults &&
                            <span>Version <span style={{fontWeight: "bold"}}>{state.defaults.version}</span></span>
                        }
                    </Typography>
                </Slide>
            </Drawer>
            <Box
                component="main"
                sx={{
                    flexGrow: 1,
                    height: '100vh',
                    overflow: 'hidden',
                }}>
                <Toolbar/>
                <Box
                    component="main"
                    sx={{
                        backgroundColor: (theme) =>
                            theme.palette.mode === 'light'
                                ? theme.palette.grey[100]
                                : theme.palette.grey[900],
                        height: '100vh',
                        overflow: 'auto',
                    }}>
                    <form
                        onSubmit={(e) => {
                            e.preventDefault()
                        }}
                        noValidate
                        autoComplete="off"
                    >
                        <Box sx={{margin: 6}}>
                            {contents}
                        </Box>
                    </form>
                    <div style={{height: "32px"}}/>
                </Box>
            </Box>
            <Backdrop
                sx={{color: '#fff', zIndex: (theme) => theme.zIndex.drawer + 1}}
                open={state.loading || state.unresponsive}
            >
                <CircularProgress color="inherit" size={"10em"}/>
            </Backdrop>
        </Box>
    );
}