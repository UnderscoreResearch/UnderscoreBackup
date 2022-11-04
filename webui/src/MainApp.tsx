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
    BackupDestination,
    BackupSet,
    BackupSetRoot,
    BackupShare,
    BackupState,
    DestinationMap,
    GetActiveShares,
    GetActivity,
    GetConfiguration,
    GetDestinationFiles,
    GetEncryptionKey,
    GetState,
    PostActivateShares,
    PostConfiguration,
    PostRemoteRestore,
    PostRestartSets,
    PostRestore,
    PostSelectSource,
    PutEncryptionKey,
    ShareMap,
    StatusLine
} from './api';
import NavigationMenu, {NavigationProps} from './components/NavigationMenu';
import Settings from './components/Settings';
import {Route, Routes, useNavigate,} from "react-router-dom";
import Status from "./components/Status";
import Destinations, {DestinationProp} from "./components/Destinations";
import Sets from "./components/Sets";
import {Backdrop, Button, CircularProgress} from "@mui/material";
import InitialSetup from "./components/InitialSetup";
import Restore, {RestorePropsChange} from "./components/Restore";
import lodashObject from 'lodash';
import Sources, {SourceProps} from "./components/Sources";
import Shares, {ShareProps} from "./components/Shares";

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
    activatedShares?: string[],
    destinationsValid: boolean,
    setsValid: boolean,
    sharesValid: boolean,
    sourcesValid: boolean,
    restoreDestination?: string,
    restoreRoots: BackupSetRoot[],
    restoreSource: string,
    restoreTimestamp?: Date,
    restoreIncludeDeleted?: boolean,
    restoreOverwrite: boolean,
    selectedSource?: string,
    backendState?: BackupState,
    activity: StatusLine[],
    navigateState: number,
    navigatedState: number,
    navigateDestination: string
}

var lastActivityState: ActivityState = {};

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
        restoreSource: "",
        initialValid: false,
        destinationsValid: true,
        sourcesValid: true,
        setsValid: true,
        sharesValid: true,
        passphrase: undefined,
        validatedPassphrase: false,
        activity: activity,
        navigateState: 1,
        navigatedState: 1,
        navigateDestination: ""
    };
}

interface DisplayState {
    navigation: NavigationProps,
    statusTitle: string,
    acceptButton: string,
    acceptEnabled: boolean,
    acceptAction?: () => void,
    cancelButton?: string,
    cancelEnabled: boolean,
    cancelAction?: () => void
}

function createDefaultDisplayState(): DisplayState {
    return {
        statusTitle: "Loading",
        acceptEnabled: false,
        acceptButton: "",
        cancelEnabled: false,
        navigation: {
            unresponsive: true,
            loading: true,
            destinations: false,
            firstTime: false,
            sets: false,
            restore: false,
            settings: false,
            share: false,
            sources: false,
            status: false
        }
    }
}

function busyStatus(ret: DisplayState, statusTitle: string) {
    ret.statusTitle = statusTitle;
    ret.navigation = {
        status: true,
        share: false,
        sets: false,
        loading: ret.navigation.loading,
        destinations: false,
        firstTime: false,
        unresponsive: false,
        sources: false,
        settings: false,
        restore: false
    };
};

interface ActivityState {
    unresponsive?: boolean,
    activity?: StatusLine[],
    activatedShares?: string[]
}

export default function MainApp() {
    const [state, setState] = React.useState(() => defaultState());

    const navigate = useNavigate();

    const toggleDrawer = () => {
        setState({
            ...state,
            open: !state.open
        })
    };

    async function updateSelectedSource(state: MainAppState) {
        setState((oldState) => ({
            ...oldState,
            loading: true
        }));
        const backendState = await GetState();
        if (backendState) {
            setState((oldState) => {
                return {
                    ...oldState,
                    backendState: backendState,
                    selectedSource: backendState.source,
                    destinationsValid: backendState.validDestinations && oldState.destinationsValid,
                    loading: false
                }
            });
        } else {
            setState((oldState) => {
                return {
                    ...oldState,
                    unresponsive: true,
                    loading: false
                }
            });
        }
    }

    async function fetchConfig(ignoreState?: boolean): Promise<MainAppState> {
        var newState = {} as MainAppState;
        newState.initialLoad = false;
        const newConfig = await GetConfiguration();
        try {
            if (newConfig !== undefined) {
                var newRebuildAvailable = false;
                if (Object.keys(newConfig.destinations).length > 0) {
                    const hasKey = await GetEncryptionKey(ignoreState ? undefined : state.passphrase);
                    if (hasKey && !ignoreState && state.passphrase) {
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
                        const activeShares = await GetActiveShares();
                        if (activeShares) {
                            newState.activatedShares = activeShares.activeShares;
                        }
                    }
                }
                newState.originalConfiguration = newConfig;
                newState.currentConfiguration = JSON.parse(JSON.stringify(newConfig));
                newState.rebuildAvailable = newRebuildAvailable;
                newState.initialValid = true;
                newState.backendState = await GetState();
                if (newState.backendState) {
                    newState.selectedSource = newState.backendState.source;
                    newState.destinationsValid = newState.backendState.validDestinations;
                } else {
                    newState.selectedSource = undefined;
                    newState.destinationsValid = false;
                }

                if (newState.currentConfiguration.sets.length == 0 && !newState.selectedSource) {
                    newState.currentConfiguration.sets.push({...newState.backendState.defaultSet});
                    newState.currentConfiguration.missingRetention = newState.backendState.defaultSet.retention;
                }
            }
        } finally {
            return newState;
        }
    }

    async function fetchActivity(): Promise<ActivityState> {
        const ret: ActivityState = {};
        const activity = await GetActivity(false);
        if (activity === undefined) {
            ret.unresponsive = true;
        } else {
            ret.activity = activity;
            ret.unresponsive = false;
        }
        if (location.href.endsWith("/share")) {
            const shares = await GetActiveShares();
            if (shares && shares.activeShares !== state.activatedShares) {
                ret.activatedShares = shares.activeShares;
            }
        }
        return ret;
    }

    async function applyConfig(newState?: MainAppState) {
        var currentState = newState !== undefined ? newState : state;
        setState((oldState) => {
            return {
                ...oldState,
                loading: true
            }
        });
        if (await PostConfiguration(currentState.currentConfiguration)) {
            const newConfig = await fetchConfig();

            if (newConfig.selectedSource &&
                currentState.validatedPassphrase &&
                currentState.passphrase &&
                currentState.backendState &&
                !currentState.backendState.validDestinations &&
                newConfig.backendState &&
                newConfig.backendState.validDestinations) {
                await PostSelectSource(newConfig.selectedSource, currentState.passphrase);
            }
            const activity = await fetchActivity();
            setState((oldState) => ({
                ...oldState,
                ...newConfig,
                ...activity,
                loading: false
            }));
        } else {
            setState((oldState) => ({
                ...oldState,
                loading: false
            }));
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

    function updateSources(valid: boolean, sources: SourceProps[]) {
        let newVal: DestinationMap = {};
        sources.forEach((item) => {
            newVal[item.id] = item.destination;
        })

        setState({
            ...state,
            currentConfiguration: {
                ...state.currentConfiguration,
                additionalSources: newVal
            },
            sourcesValid: valid
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

    function sourceExist(key: string): boolean {
        if (state.originalConfiguration && state.originalConfiguration.additionalSources) {
            return !!state.originalConfiguration.additionalSources[key]
        }
        return false;
    }

    function getSourcesList(): SourceProps[] {
        const keys = state.currentConfiguration.additionalSources
            ? Object.keys(state.currentConfiguration.additionalSources)
            : [];
        keys.sort();

        return keys.map(key => {
            return {
                destination: (state.currentConfiguration.additionalSources as DestinationMap)[key] as BackupDestination,
                exist: sourceExist(key),
                id: key
            }
        });
    }

    function getSharesList(): ShareProps[] {
        const keys = state.currentConfiguration.shares
            ? Object.keys(state.currentConfiguration.shares)
            : [];
        // @ts-ignore
        keys.sort((a, b) => state.currentConfiguration.shares[a].name.localeCompare(state.currentConfiguration.shares[b].name));

        return keys.map(key => {
            return {
                share: (state.currentConfiguration.shares as ShareMap)[key] as BackupShare,
                exists: (!!state.originalConfiguration.shares) &&
                    !!((state.originalConfiguration.shares as ShareMap)[key]),
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
            restoreSource: newState.source,
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

        const newActivity = await fetchActivity();

        setState((oldState) => {
            return {
                ...oldState,
                ...newActivity,
                loading: false,
                restoreOverwrite: false,
                restoreTimestamp: undefined,
                restoreRoots: [],
                restoreDestination: undefined,
                navigateState: oldState.navigateState + 1,
                navigateDestination: "status"
            }
        });
    }

    React.useEffect(() => {
        if (state.navigatedState != state.navigateState) {
            setState((oldState) => ({
                ...oldState,
                navigatedState: oldState.navigateState
            }));
        }
        if (state.navigateDestination) {
            navigate(state.navigateDestination);
        }
    }, [state.navigateState]);

    React.useEffect(() => {
        const method = async () => {
            const newActivity = await fetchActivity();
            const newConfig = await fetchConfig();
            setState((oldState) => ({
                ...oldState,
                ...newConfig,
                ...newActivity,
                loading: false
            }));
        };

        method();

        const updateActivity = async () => {
            const newActivity = await fetchActivity();

            if (!lodashObject.isEqual(newActivity, lastActivityState)) {
                lastActivityState = newActivity;
                setState((oldState) => ({
                    ...oldState,
                    ...newActivity
                }));
            }
        }

        const timer = setInterval(updateActivity, 2000);
        return () => clearInterval(timer);
    }, []);

    async function unselectSource() {
        setState((oldState) => {
            return {
                ...oldState,
                loading: true,
                validatedPassphrase: false,
                restoreSource: "",
                selectedSource: undefined,
                passphrase: undefined
            }
        });
        await PostSelectSource(
            "-", undefined);
        location.reload();
    }

    function needActivation(shares?: string[]) {
        const keys = state.currentConfiguration.shares
            ? Object.keys(state.currentConfiguration.shares)
            : [];

        if (shares) {
            if (keys.length !== shares.length) {
                return true;
            }
            return !lodashObject.isEqual(keys.sort(), shares.sort());
        }

        return false;
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

    async function activateShares(passphrase: string) {
        setState((oldState) => {
            return {
                ...oldState,
                loading: true
            }
        })
        await PostActivateShares(passphrase);
        const newActivity = await fetchActivity();
        setState((oldState) => ({
            ...oldState,
            ...newActivity,
            loading: false,
            navigateState: oldState.navigateState + 1,
            navigateDestination: "status"
        }));
    }

    function updatedShares(valid: boolean, passphrase: string, shares: ShareProps[]) {
        let newVal: ShareMap = {};

        if (state.validatedPassphrase) {
            shares.forEach((item) => {
                newVal[item.id] = item.share;
            })

            setState({
                ...state,
                sharesValid: valid,
                passphrase: passphrase,
                currentConfiguration: {
                    ...state.currentConfiguration,
                    shares: newVal
                }
            })
        } else {
            setState({
                ...state,
                passphrase: passphrase
            })
        }
    }

    function calculateInitialDisplayState(ret: DisplayState) {
        if (Object.keys(state.originalConfiguration.destinations).length > 0) {
            ret.cancelAction = () => setState({
                ...state,
                originalConfiguration: {
                    ...state.originalConfiguration,
                    destinations: {}
                }
            });
            ret.cancelButton = "Back";
            ret.cancelEnabled = true;
            if (state.passphrase) {
                const passphrase = state.passphrase;
                if (state.rebuildAvailable) {
                    ret.acceptAction = () => {
                        const method = async function () {

                            if (await PostRemoteRestore(passphrase)) {
                                const newConfig = await fetchConfig();
                                setState((oldState) => ({
                                    ...oldState,
                                    ...newConfig,
                                    loading: false
                                }));
                            } else {
                                setState((oldState) => ({
                                    ...oldState,
                                    loading: false
                                }));
                            }
                        };

                        setState({
                            ...state,
                            loading: true
                        });

                        method();
                    };
                } else {
                    ret.acceptAction = async () => {
                        if (await PutEncryptionKey(passphrase)) {
                            const newConfig = await fetchConfig();
                            setState((oldState) => ({
                                ...oldState,
                                ...newConfig,
                                navigateState: oldState.navigateState + 1,
                                navigateDestination: "sets",
                                loading: false
                            }));
                        } else {
                            setState((oldState) => ({
                                ...oldState,
                                loading: false
                            }));
                        }
                    };
                }
            } else {
                ret.acceptAction = () => {
                };
            }
        } else {
            ret.acceptAction = () => applyConfig();
        }
        ret.acceptEnabled = state.initialValid;
        ret.statusTitle = "Initial Setup"
        ret.navigation.firstTime = true;
        ret.acceptButton = "Next";

        return ret;
    }

    const configNotChanged = lodashObject.isEqual(state.originalConfiguration, state.currentConfiguration);

    function restoreStatus(ret: DisplayState, status: string) {
        busyStatus(ret, status);

        ret.acceptAction = () => applyConfig();
        ret.acceptButton = "Cancel Restore";
        ret.acceptEnabled = true;
    }

    function calculateRestoreDisplayState(ret: DisplayState, validConfig: boolean) {
        if (state.validatedPassphrase) {
            if (!ret.cancelAction) {
                ret.cancelButton = "Back";
                ret.cancelEnabled = true;
                ret.cancelAction = () =>
                    setState({
                        ...state,
                        validatedPassphrase: false,
                        restoreSource: ""
                    });
            }

            if (!ret.acceptAction) {
                ret.acceptEnabled = validConfig && state.restoreRoots.length > 0;
                ret.acceptButton = "Start Restore";
                ret.acceptAction = () => startRestore();
            }
        } else {
            if (!ret.acceptAction) {
                const passphrase = state.passphrase ? state.passphrase : "";

                ret.acceptEnabled = validConfig && !!state.passphrase;
                ret.acceptButton = "Validate Passphrase";
                ret.acceptAction = async () => {

                    setState({
                        ...state,
                        loading: true
                    });

                    let response = await PostSelectSource(
                        state.restoreSource ? state.restoreSource : "-", passphrase);

                    var validDestination = true;
                    var redirect = "";
                    if (response) {
                        if (response === "destinations") {
                            validDestination = false;
                            redirect = response;
                        }
                    }

                    const newConfig = await fetchConfig(true);
                    const activity = await fetchActivity();

                    setState((oldState) => ({
                        ...oldState,
                        ...newConfig,
                        ...activity,
                        destinationsValid: validDestination,
                        validatedPassphrase: !!response,
                        navigateState: redirect ? oldState.navigateState + 1 : oldState.navigateState,
                        navigateDestination: redirect ? redirect : "",
                        loading: false
                    }));
                }
            }
        }
    }

    function wrongPageDisplayState(ret: DisplayState) {
        if (state.navigateState == state.navigatedState) {
            setState({
                ...state,
                navigateState: state.navigatedState + 1,
                navigateDestination: "status"
            });
        }
        ret.navigation.loading = true;
        return ret;
    }

    function calculateShareDisplayState(ret: DisplayState, validConfig: boolean) {
        const passphrase = state.passphrase ? state.passphrase : "";
        if (!state.validatedPassphrase) {
            ret.acceptEnabled = validConfig && !!state.passphrase;
            ret.acceptButton = "Validate Passphrase";

            ret.acceptAction = async () => {
                setState({
                    ...state,
                    loading: true
                });

                let response = await GetEncryptionKey(passphrase);

                if (response) {
                    setState((oldState) => ({
                        ...oldState,
                        validatedPassphrase: true,
                        loading: false
                    }));
                } else {
                    setState((oldState) => ({
                        ...oldState,
                        loading: false
                    }));
                }
            }
        } else if (!ret.acceptAction && needActivation(state.activatedShares)) {
            ret.acceptButton = "Activate Shares";
            ret.acceptEnabled = true;
            ret.acceptAction = () => activateShares(passphrase);
        }
    }

    function calculateDisplayState(): DisplayState {

        const ret = createDefaultDisplayState();

        if (!state.loading && state.activity) {
            const sourceActivity = state.activity.find(item => item.code == "SOURCE");
            if (state.selectedSource && sourceActivity) {
                if (state.selectedSource && !state.loading && (!sourceActivity || sourceActivity.valueString !== state.selectedSource)) {
                    updateSelectedSource(state);
                    return ret;
                }
            } else {
                if (!!state.selectedSource != !!sourceActivity) {
                    updateSelectedSource(state);
                    return ret;
                }
            }
        }

        if (state.initialLoad) {
            return ret;
        }

        if (state.unresponsive) {
            ret.statusTitle = "Unresponsive";
            return ret;
        }

        ret.navigation.unresponsive = false;
        ret.navigation.loading = state.loading;

        const completedSetup = Object.keys(state.originalConfiguration.destinations).length > 0 && state.hasKey;
        const validConfig = state.initialValid && state.destinationsValid && state.setsValid && state.sharesValid && state.sourcesValid;

        if (!completedSetup) {
            return calculateInitialDisplayState(ret);
        }

        let currentPage: string;
        if (location.href.endsWith("/")) {
            currentPage = "status";
        } else {
            currentPage = location.href.substring(location.href.lastIndexOf('/') + 1);
        }

        ret.navigation = {
            status: true,
            restore: configNotChanged && validConfig,
            sets: true,
            settings: true,
            sources: true,
            unresponsive: false,
            firstTime: false,
            destinations: true,
            share: true,
            loading: state.loading
        }

        if (!configNotChanged) {
            ret.cancelEnabled = true;
            ret.cancelButton = "Revert";
            ret.cancelAction = () => location.reload();

            ret.acceptEnabled = validConfig;
            ret.acceptButton = "Save";
            ret.acceptAction = () => applyConfig();
        }

        let backupInProgress = true;
        let backupCanStart = false;

        if (state.activity.some(item => item.code.startsWith("BACKUP_"))) {
            ret.statusTitle = "Backup In Progress";
        } else if (state.activity.some(item => item.code.startsWith("TRIMMING_"))) {
            ret.statusTitle = "Trimming Repository";
        } else if (state.activity.some(item => item.code.startsWith("VALIDATE_"))) {
            ret.statusTitle = "Validating Repository";
        } else {
            backupInProgress = false;

            if (state.activity.some(item => item.code.startsWith("UPLOAD_PENDING"))) {
                ret.statusTitle = "Initializing";
            } else if (state.activity.some(item => item.code.startsWith("ACTIVATING_SHARES_"))) {
                busyStatus(ret, "Activating Shares");
            } else if (state.activity.some(item => item.code.startsWith("DEACTIVATING_SHARES_"))) {
                busyStatus(ret, "Deactivating Shares");
            } else {
                if (state.selectedSource) {
                    ret.navigation.sets =
                        ret.navigation.sources =
                            ret.navigation.share =
                                ret.navigation.settings = false;
                    if (state.activity.some(item => item.code.startsWith("REPLAY_"))) {
                        busyStatus(ret, `Syncing Contents From ${state.selectedSource}`);
                    } else {
                        if (!ret.cancelAction) {
                            ret.cancelButton = "Exit";
                            ret.cancelAction = () => unselectSource();
                            ret.cancelEnabled = true;
                        }

                        if (state.activity.some(item => item.code.startsWith("RESTORE_"))) {
                            restoreStatus(ret, `Restore From ${state.selectedSource} In Progress`);
                        } else {
                            ret.statusTitle = `Browsing ${state.selectedSource} Contents`;
                        }
                    }
                } else {
                    if (state.activity.some(item => item.code.startsWith("RESTORE_"))) {
                        restoreStatus(ret, "Restore In Progress");
                    } else if (state.activity.some(item => item.code.startsWith("REPLAY_"))) {
                        busyStatus(ret, "Replaying From Backup");
                    } else if (state.activity.some(item => item.code.startsWith("OPTIMIZING_"))) {
                        busyStatus(ret, "Optimizing Log");
                    } else {
                        ret.statusTitle = "Currently Inactive";

                        backupCanStart = true;
                    }
                }
            }
        }

        switch (currentPage) {
            case "restore":
                if (!ret.navigation.restore)
                    return wrongPageDisplayState(ret);
                calculateRestoreDisplayState(ret, validConfig);
                break;
            case "sets":
                if (!ret.navigation.sets)
                    return wrongPageDisplayState(ret);
                break;
            case "destinations":
                if (!ret.navigation.destinations)
                    return wrongPageDisplayState(ret);
                break;
            case "settings":
                if (!ret.navigation.settings)
                    return wrongPageDisplayState(ret);
                break;
            case "share":
                if (!ret.navigation.share)
                    return wrongPageDisplayState(ret);
                calculateShareDisplayState(ret, validConfig);
                break;
            case "sources":
                if (!ret.navigation.sources)
                    return wrongPageDisplayState(ret);
                break;
        }

        if (!ret.acceptAction) {
            if (backupInProgress) {
                ret.acceptButton = "Pause Backup";
                ret.acceptEnabled = true;
                ret.acceptAction = () => changeBackup(false);
            } else {
                ret.acceptButton = "Start Backup";
                ret.acceptEnabled = validConfig && backupCanStart;
                ret.acceptAction = () => changeBackup(true);
            }
        }

        return ret;
    }

    const displayState = calculateDisplayState();

    let contents;

    if (displayState.navigation.unresponsive) {
        contents = <div/>
    } else if (displayState.navigation.firstTime) {
        contents = <InitialSetup
            originalConfig={state.originalConfiguration}
            currentConfig={state.currentConfiguration}
            configUpdated={(valid, newConfig, passphrase) => updateInitialConfig(valid, newConfig, passphrase)}
            rebuildAvailable={state.rebuildAvailable}/>
    } else if (state.backendState) {
        contents = <Routes>
            <Route path="/" element={<Status status={state.activity}/>}/>
            <Route path="status" element={<Status status={state.activity}/>}/>
            <Route path="settings"
                   element={<Settings config={state.currentConfiguration} onChange={updateConfig}/>}/>
            <Route path="restore" element={<Restore passphrase={state.passphrase}
                                                    validatedPassphrase={state.validatedPassphrase}
                                                    destination={state.restoreDestination}
                                                    defaultDestination={state.backendState.defaultRestoreFolder}
                                                    defaults={state.backendState}
                                                    roots={state.restoreRoots}
                                                    sources={state.originalConfiguration.additionalSources ? Object.keys(state.originalConfiguration.additionalSources) : []}
                                                    source={state.selectedSource ? state.selectedSource : state.restoreSource}
                                                    activeSource={!!state.selectedSource}
                                                    overwrite={state.restoreOverwrite}
                                                    timestamp={state.restoreTimestamp}
                                                    onSubmit={applyChanges}
                                                    onChange={updateRestore}/>}/>
            <Route path="destinations" element={<Destinations destinations={getDestinationList()}
                                                              dontDelete={[state.currentConfiguration.manifest.destination]}
                                                              configurationUpdated={updateDestinations}/>}/>
            <Route path="sources" element={<Sources sources={getSourcesList()}
                                                    configurationUpdated={updateSources}/>}/>
            <Route path="share" element={<Shares shares={getSharesList()}
                                                 passphrase={state.passphrase}
                                                 onSubmit={applyChanges}
                                                 activeShares={state.activatedShares}
                                                 validatedPassphrase={state.validatedPassphrase}
                                                 defaults={state.backendState}
                                                 configurationUpdated={updatedShares}/>}/>
            <Route path="sets" element={<Sets sets={state.currentConfiguration.sets}
                                              defaults={state.backendState}
                                              allowReset={configNotChanged && !!state.currentConfiguration.manifest.interactiveBackup}
                                              destinations={getDestinationList()}
                                              configurationUpdated={updateSets}/>
            }/>
        </Routes>;
    }

    function applyChanges() {
        if (displayState.acceptAction) {
            displayState.acceptAction();
        }
    }

    function applyCancel() {
        if (displayState.cancelAction) {
            displayState.cancelAction();
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
                        {displayState.statusTitle}
                    </Typography>
                    <Box sx={{flexGrow: 1, display: {xs: 'none', md: 'flex'}}}>
                    </Box>

                    {displayState.cancelAction && !displayState.navigation.loading &&
                        <Box sx={{flexGrow: 0, marginRight: "2em"}}>
                            <Button sx={{my: 2, color: 'white', display: 'block'}}
                                    id={"cancelButton"}
                                    disabled={!displayState.cancelEnabled}
                                    onClick={() => applyCancel()}>
                                {displayState.cancelButton}
                            </Button>
                        </Box>
                    }

                    {displayState.acceptAction && !displayState.navigation.loading &&
                        <Box sx={{flexGrow: 0}}>
                            <Button sx={{my: 2, color: 'white', display: 'block'}}
                                    variant="contained"
                                    color={displayState.acceptButton !== "Cancel Restore" ? "success" : "error"}
                                    disabled={!displayState.acceptEnabled}
                                    id={"acceptButton"}
                                    onClick={() => applyChanges()}>
                                {displayState.acceptButton}
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
                    <NavigationMenu
                        unresponsive={displayState.navigation.unresponsive}
                        destinations={displayState.navigation.destinations}
                        share={displayState.navigation.share}
                        sources={displayState.navigation.sources}
                        firstTime={displayState.navigation.firstTime}
                        restore={displayState.navigation.restore}
                        sets={displayState.navigation.sets}
                        settings={displayState.navigation.settings}
                        status={displayState.navigation.status}
                        loading={displayState.navigation.loading}
                    />

                }

                <Slide direction="right" in={state.open}>
                    <Typography
                        color="darkgray"
                        noWrap
                        fontSize={14}
                        style={{position: "absolute", bottom: "8px", right: "8px", width: "220px", textAlign: "right"}}
                    >
                        {state.backendState &&
                            <span>Version <span style={{fontWeight: "bold"}}>{state.backendState.version}</span></span>
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
                id={"loading"}
                open={displayState.navigation.loading || displayState.navigation.unresponsive}
            >
                <CircularProgress color="inherit" size={"10em"}/>
            </Backdrop>
        </Box>
    );
}