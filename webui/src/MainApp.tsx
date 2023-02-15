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
import {marked} from 'marked';

import {
    activateShares,
    BackupConfiguration,
    BackupDestination,
    BackupSet,
    BackupSetRoot,
    BackupShare,
    BackupState,
    createEncryptionKey,
    DestinationMap,
    getActivity,
    getConfiguration,
    getEncryptionKey,
    getState,
    initiateRestore,
    listActiveShares,
    postConfiguration,
    rebuildAvailable,
    restartSets,
    selectSource,
    ShareMap,
    startRemoteRestore,
    StatusLine
} from './api';
import NavigationMenu, {NavigationProps} from './components/NavigationMenu';
import Settings from './components/Settings';
import {Route, Routes, useNavigate,} from "react-router-dom";
import Status from "./components/Status";
import Destinations, {DestinationProp} from "./components/Destinations";
import Sets from "./components/Sets";
import {
    Backdrop,
    Button,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle, LinearProgress
} from "@mui/material";
import InitialSetup, {InitialPage} from "./components/InitialSetup";
import Restore, {RestorePropsChange} from "./components/Restore";
import {deepEqual} from 'fast-equals';
import Sources, {SourceProps} from "./components/Sources";
import Shares, {ShareProps} from "./components/Shares";
import AuthorizeAccept from "./components/AuthorizeAccept";
import {invertedLogo, logo} from "./images";
import {createSecret, SourceResponse, updateSource} from "./api/service";
import base64url from "base64url";

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
    initialPage: InitialPage,
    initialSource?: SourceResponse,
    originalConfiguration: BackupConfiguration,
    currentConfiguration: BackupConfiguration,
    password?: string,
    validatedPassword: boolean,
    initialValid: boolean,
    activatedShares?: string[],
    shareEncryptionNeeded: boolean,
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
    selectedSourceName?: string,
    secretRegion?: string,
    backendState?: BackupState,
    activity: StatusLine[],
    navigateState: number,
    navigatedState: number,
    navigateDestination: string,
    showNewVersion: boolean,
    slideAnimation: boolean
}

interface ActivityState {
    unresponsive?: boolean,
    activity?: StatusLine[],
    activatedShares?: string[]
}

var lastActivityState: ActivityState = {};
var activityUpdated: ((newValue: ActivityState) => void) | undefined;

async function fetchActivityInternal(): Promise<ActivityState> {
    const ret: ActivityState = {};
    const activity = await getActivity(false);
    if (activity === undefined) {
        ret.unresponsive = true;
    } else {
        ret.activity = activity;
        ret.unresponsive = false;
    }
    if (location.href.endsWith("/share")) {
        const shares = await listActiveShares();
        if (shares && shares.activeShares) {
            ret.activatedShares = shares.activeShares;
        }
    }
    return ret;
}

async function fetchActivity(): Promise<ActivityState> {
    const ret = await fetchActivityInternal();
    lastActivityState = ret;
    return lastActivityState;
}

async function updateActivity() {
    const newActivity = await fetchActivityInternal();

    if (!deepEqual(newActivity, lastActivityState)) {
        lastActivityState = newActivity;
        if (activityUpdated)
            activityUpdated(lastActivityState);
    }
}

function defaultState(): MainAppState {
    const roots: BackupSetRoot[] = [];

    function defaultConfig(): BackupConfiguration {
        return {
            destinations: {},
            sets: [],
            manifest: {
                destination: '',
                scheduleRandomize: {
                    duration: 1,
                    unit: "HOURS"
                }
            }
        }
    }

    const activity: StatusLine[] = lastActivityState.activity ? lastActivityState.activity : [];

    return {
        open: window.localStorage.getItem("open") !== "false",
        rebuildAvailable: false,
        hasKey: false,
        loading: true,
        unresponsive: lastActivityState.unresponsive ? lastActivityState.unresponsive : false,
        initialLoad: true,
        initialPage: "service",
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
        secretRegion: undefined,
        password: undefined,
        validatedPassword: false,
        activity: activity,
        navigateState: 1,
        navigatedState: 1,
        navigateDestination: "",
        activatedShares: lastActivityState.activatedShares,
        shareEncryptionNeeded: false,
        showNewVersion: false,
        slideAnimation: false
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

export default function MainApp() {
    const [state, setState] = React.useState(() => defaultState());

    const navigate = useNavigate();

    const toggleDrawer = () => {
        window.localStorage.setItem("open", (!state.open).toString());
        setState({
            ...state,
            open: !state.open,
            slideAnimation: true
        })

        setTimeout(() => {
            setState((oldState) => ({
                ...oldState,
                slideAnimation: false
            }));
        }, 300);
    };

    async function updateSelectedSource(state: MainAppState) {
        setState((oldState) => ({
            ...oldState,
            loading: true
        }));
        const backendState = await getState();
        if (backendState) {
            setState((oldState) => {
                return {
                    ...oldState,
                    backendState: backendState,
                    selectedSource: backendState.source,
                    selectedSourceName: backendState.sourceName,
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

    async function fetchConfig(knownConfig?: BackupConfiguration, ignoreState?: boolean): Promise<MainAppState> {
        var newState = {} as MainAppState;
        newState.initialLoad = false;
        newState.backendState = await getState();
        const newConfig = knownConfig ? JSON.parse(JSON.stringify(knownConfig)) : await getConfiguration();
        try {
            if (newConfig !== undefined) {
                var newRebuildAvailable = false;
                if (Object.keys(newConfig.destinations).length > 0) {
                    const hasKey = await getEncryptionKey(ignoreState ? undefined : state.password);
                    if (hasKey && !ignoreState && state.password) {
                        newState.validatedPassword = true;
                    }
                    if (!hasKey && newConfig.manifest.destination) {
                        newRebuildAvailable = await rebuildAvailable(newConfig.manifest.destination);
                    } else {
                        newState.hasKey = true;
                        const activeShares = await listActiveShares();
                        if (activeShares) {
                            newState.activatedShares = activeShares.activeShares;
                            newState.shareEncryptionNeeded = activeShares.shareEncryptionNeeded;
                        }
                    }
                }
                newState.originalConfiguration = newConfig;
                newState.currentConfiguration = JSON.parse(JSON.stringify(newConfig));
                newState.rebuildAvailable = newRebuildAvailable;
                newState.initialValid = true;
                if (newState.backendState) {
                    newState.selectedSource = newState.backendState.source;
                    newState.selectedSourceName = newState.backendState.sourceName;
                    newState.destinationsValid = newState.backendState.validDestinations;
                } else {
                    newState.selectedSource = undefined;
                    newState.selectedSourceName = undefined;
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

    async function applyConfig(newState?: MainAppState) {
        var currentState = newState !== undefined ? newState : state;
        setState((oldState) => {
            return {
                ...oldState,
                loading: true
            }
        });
        if (await postConfiguration(currentState.currentConfiguration)) {
            const newConfig = await fetchConfig(currentState.currentConfiguration);

            if (newConfig.selectedSource &&
                currentState.validatedPassword &&
                currentState.password &&
                currentState.backendState &&
                !currentState.backendState.validDestinations &&
                newConfig.backendState &&
                newConfig.backendState.validDestinations) {
                await selectSource(newConfig.selectedSource, currentState.password);
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

    function updateInitialConfig(valid: boolean, newConfig: BackupConfiguration, password?: string, secretRegion?: string) {
        setState((oldState) => ({
            ...oldState,
            initialValid: valid,
            currentConfiguration: newConfig,
            password: password,
            secretRegion: secretRegion
        }));
    }

    function updateSets(valid: boolean, sets: BackupSet[]) {
        setState((oldState) => ({
            ...oldState,
            currentConfiguration: {
                ...(oldState.currentConfiguration),
                sets: sets
            },
            setsValid: valid
        }));
    }

    function updateDestinations(valid: boolean, destinations: DestinationProp[]) {
        let newVal: DestinationMap = {};
        destinations.forEach((item) => {
            newVal[item.id] = item.destination;
        })

        setState((oldState) => ({
            ...oldState,
            currentConfiguration: {
                ...oldState.currentConfiguration,
                destinations: newVal
            },
            destinationsValid: valid
        }));
    }

    function updateSources(valid: boolean, sources: SourceProps[]) {
        let newVal: DestinationMap = {};
        sources.forEach((item) => {
            newVal[item.id] = item.destination;
        })

        setState((oldState) => ({
            ...oldState,
            currentConfiguration: {
                ...oldState.currentConfiguration,
                additionalSources: newVal
            },
            sourcesValid: valid
        }));
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
        setState((oldState) => ({
            ...oldState,
            currentConfiguration: newConfig
        }));
    }

    function updateRestore(newState: RestorePropsChange): void {
        setState((oldState) => ({
            ...oldState,
            password: newState.password,
            restoreDestination: newState.destination,
            restoreRoots: newState.roots,
            restoreSource: newState.source,
            restoreTimestamp: newState.timestamp,
            restoreIncludeDeleted: newState.includeDeleted,
            restoreOverwrite: newState.overwrite
        }));
    }

    async function startRestore() {
        setState((oldState) => ({
            ...oldState,
            loading: true
        }));
        await initiateRestore({
            password: state.password ? state.password : "",
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

        activityUpdated = (newActivity: ActivityState) => {
            setState((oldState) => ({
                ...oldState,
                ...newActivity
            }));
        }

        const timer = setInterval(updateActivity, 2000);
        return () => {
            activityUpdated = undefined;
            clearInterval(timer);
        }
    }, []);

    async function unselectSource() {
        setState((oldState) => {
            return {
                ...oldState,
                loading: true,
                validatedPassword: false,
                restoreSource: "",
                selectedSource: undefined,
                selectedSourceName: undefined,
                password: undefined
            }
        });
        await selectSource(
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
            return !deepEqual(keys.sort(), shares.sort());
        }

        return false;
    }

    async function changeBackup(start: boolean) {
        if (start && state.currentConfiguration.manifest.interactiveBackup) {
            setState((oldState) => ({
                ...oldState,
                loading: true
            }));
            try {
                await restartSets();
            } finally {
                setState((oldState) => ({
                    ...oldState,
                    loading: false
                }));
            }
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

    async function activateSharesClicked(password: string) {
        setState((oldState) => {
            return {
                ...oldState,
                loading: true
            }
        })
        try {
            await activateShares(password);
            const newActivity = await fetchActivity();
            setState((oldState) => ({
                ...oldState,
                ...newActivity,
                loading: false,
                navigateState: oldState.navigateState + 1,
                navigateDestination: "status"
            }));
        } finally {
            setState((oldState) => ({
                ...oldState,
                loading: false
            }));
        }
    }

    function updatedShares(valid: boolean, password: string, shares: ShareProps[]) {
        let newVal: ShareMap = {};

        if (state.validatedPassword) {
            shares.forEach((item) => {
                newVal[item.id] = item.share;
            })

            setState((oldState) => ({
                ...oldState,
                sharesValid: valid,
                password: password,
                currentConfiguration: {
                    ...oldState.currentConfiguration,
                    shares: newVal
                }
            }))
        } else {
            setState((oldState) => ({
                ...oldState,
                password: password
            }))
        }
    }

    function calculateInitialDisplayState(ret: DisplayState, currentPage: string) {
        ret.statusTitle = "Initial Setup"
        ret.navigation.firstTime = true;
        ret.navigation.loading = currentPage.startsWith("authorizeaccept") || state.loading
        ret.acceptButton = "Next";

        if (state.initialPage !== "service") {
            let previousPage: InitialPage;
            switch (state.initialPage) {
                case "source":
                    previousPage = "service";
                    break;
                case "destination":
                    if (state.backendState?.serviceConnected)
                        previousPage = "source";
                    else
                        previousPage = "service";
                    break;
                case "key":
                    if (state.initialSource)
                        previousPage = "source";
                    else
                        previousPage = "destination";
                    break;
            }
            ret.cancelAction = () => {
                setState((oldState) => ({
                    ...oldState,
                    initialPage: previousPage
                }));
            }
            ret.cancelEnabled = true;
            ret.cancelButton = "Back";
        }
        switch (state.initialPage) {
            case "source":
                if (state.backendState?.serviceSourceId) {
                    ret.acceptEnabled = true;
                    ret.acceptAction = () => setState((oldState) => ({
                        ...oldState,
                        initialPage: oldState.initialSource ? "key" : "destination"
                    }));
                } else {
                    ret.acceptEnabled = false;
                }
                break;
            case "destination":
                ret.acceptAction = () => {
                    applyConfig().then(() =>
                        setState((oldState) => ({
                            ...oldState,
                            initialPage: "key"
                        })));
                }
                ret.acceptEnabled = state.initialValid;
                break;
            case "key":
                ret.acceptEnabled = state.initialValid && !!state.password;
                if (state.password) {
                    const password = state.password;
                    if (state.initialSource) {
                        const source = state.initialSource;
                        ret.acceptAction = async () => {
                            setState((oldState) => ({
                                ...oldState,
                                loading: true
                            }));

                            if (await updateSource(source.name, source.sourceId, state.password)) {
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
                    } else if (state.rebuildAvailable) {
                        ret.acceptAction = async () => {
                            setState((oldState) => ({
                                ...oldState,
                                loading: true
                            }));

                            if (await startRemoteRestore(password)) {
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
                    } else {
                        ret.acceptAction = async () => {
                            setState((oldState) => ({
                                ...state,
                                loading: true
                            }));
                            if (await createEncryptionKey(password)) {
                                if (state.secretRegion) {
                                    await createSecret(state.password as string, state.secretRegion,
                                        base64url.decode(window.localStorage.getItem("email") as string))
                                }

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
                    }
                }
        }

        return ret;
    }

    const configNotChanged = deepEqual(state.originalConfiguration, state.currentConfiguration);

    function restoreStatus(ret: DisplayState, status: string) {
        busyStatus(ret, status);

        ret.acceptAction = () => applyConfig();
        ret.acceptButton = "Cancel Restore";
        ret.acceptEnabled = true;
    }

    function calculateRestoreDisplayState(ret: DisplayState, validConfig: boolean) {
        if (state.validatedPassword) {
            if (!ret.cancelAction) {
                ret.cancelButton = "Back";
                ret.cancelEnabled = true;
                ret.cancelAction = () =>
                    setState((oldstate) => ({
                        ...oldstate,
                        validatedPassword: false,
                        restoreSource: ""
                    }));
            }

            if (!ret.acceptAction) {
                ret.acceptEnabled = validConfig && state.restoreRoots.length > 0;
                ret.acceptButton = "Start Restore";
                ret.acceptAction = () => startRestore();
            }
        } else {
            if (!ret.acceptAction) {
                const password = state.password ? state.password : "";

                ret.acceptEnabled = validConfig && !!state.password;
                ret.acceptButton = "Validate Password";
                ret.acceptAction = async () => {

                    setState((oldState) => ({
                        ...oldState,
                        loading: true
                    }));

                    let response = await selectSource(
                        state.restoreSource ? state.restoreSource : "-", password);

                    var validDestination = true;
                    var redirect = "";
                    if (response) {
                        if (response === "destinations") {
                            validDestination = false;
                            redirect = response;
                        }
                    }

                    const newConfig = await fetchConfig(undefined, true);
                    const activity = await fetchActivity();

                    setState((oldState) => ({
                        ...oldState,
                        ...newConfig,
                        ...activity,
                        destinationsValid: validDestination,
                        validatedPassword: !!response,
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
            setState((oldState) => ({
                ...oldState,
                navigateState: oldState.navigatedState + 1,
                navigateDestination: "status"
            }));
        }
        ret.navigation.loading = true;
        return ret;
    }

    async function updateToken(): Promise<void> {
        const backendState = await getState();
        setState((oldState) => ({
            ...oldState,
            backendState: backendState
        }));
    }

    function calculateShareDisplayState(ret: DisplayState, validConfig: boolean) {
        const password = state.password ? state.password : "";
        if (!state.validatedPassword) {
            ret.acceptEnabled = validConfig && !!state.password;
            ret.acceptButton = "Validate Password";

            ret.acceptAction = async () => {
                setState((oldState) => ({
                    ...oldState,
                    loading: true
                }));

                let response = await getEncryptionKey(password);

                if (response) {
                    setState((oldState) => ({
                        ...oldState,
                        validatedPassword: true,
                        loading: false
                    }));
                } else {
                    setState((oldState) => ({
                        ...oldState,
                        loading: false
                    }));
                }
            }
        } else if (!ret.acceptAction && (needActivation(state.activatedShares) || state.shareEncryptionNeeded)) {
            ret.acceptButton = "Activate Shares";
            ret.acceptEnabled = true;
            ret.acceptAction = () => activateSharesClicked(password);
        }
    }

    function calculateDisplayState(): DisplayState {

        const ret = createDefaultDisplayState();

        if (!state.loading && state.activity) {
            const sourceActivity = state.activity.find(item => item.code == "SOURCE");
            if (state.selectedSource && sourceActivity) {
                if (state.selectedSource && !state.loading && (!sourceActivity || sourceActivity.valueString !== state.selectedSourceName)) {
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

        let currentPage: string;
        if (location.href.endsWith("/")) {
            currentPage = "status";
        } else {
            currentPage = location.href.substring(location.href.lastIndexOf('/') + 1);
        }

        if (!completedSetup) {
            return calculateInitialDisplayState(ret, currentPage);
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
            loading: state.loading || currentPage.startsWith("authorizeaccept")
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
                        busyStatus(ret, `Syncing Contents From ${state.selectedSourceName}`);
                    } else {
                        if (!ret.cancelAction) {
                            ret.cancelButton = "Exit";
                            ret.cancelAction = () => unselectSource();
                            ret.cancelEnabled = true;
                        }

                        if (state.activity.some(item => item.code.startsWith("RESTORE_"))) {
                            restoreStatus(ret, `Restore From ${state.selectedSourceName} In Progress`);
                        } else {
                            ret.statusTitle = `Browsing ${state.selectedSourceName} Contents`;
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

    function initialPageChanged(page: InitialPage) {
        setState((oldState) => ({
            ...oldState,
            initialPage: page
        }));
    }

    function getUsedDestinations() {
        const allDestinations = [state.currentConfiguration.manifest.destination];
        state.currentConfiguration.sets.forEach(set =>
            set.destinations.forEach(destination => allDestinations.push(destination)))
        // @ts-ignore
        return [...new Set(allDestinations)];
    }

    if (displayState.navigation.unresponsive) {
        contents = <div/>
    } else if (displayState.navigation.firstTime) {
        if (state.backendState) {
            contents = <Routes>
                <Route path="authorizeaccept" element={<AuthorizeAccept updatedToken={updateToken}/>}/>
                <Route path="*" element={
                    <InitialSetup
                        page={state.initialPage}
                        originalConfig={state.originalConfiguration}
                        backendState={state.backendState}
                        updatedToken={updateToken}
                        initialSource={(source) => setState((oldState) => ({
                            ...oldState,
                            initialSource: source,
                            initialPage: source ? "destination" : "key"
                        }))}
                        onPageChange={(page) => initialPageChanged(page)}
                        currentConfig={state.currentConfiguration}
                        configUpdated={(valid, newConfig, password, secretRegion) => updateInitialConfig(valid, newConfig, password, secretRegion)}
                        rebuildAvailable={state.rebuildAvailable}/>
                }/>
            </Routes>
        } else {
            return <></>
        }
    } else if (state.backendState) {
        contents = <Routes>
            <Route path="/" element={<Status status={state.activity}/>}/>
            <Route path="status" element={<Status status={state.activity}/>}/>
            <Route path="authorizeaccept" element={<AuthorizeAccept updatedToken={updateToken}/>}/>
            <Route path="settings"
                   element={<Settings config={state.currentConfiguration} backendState={state.backendState}
                                      updatedToken={updateToken}
                                      onChange={updateConfig}/>}/>
            <Route path="restore" element={<Restore password={state.password}
                                                    validatedPassword={state.validatedPassword}
                                                    destination={state.restoreDestination}
                                                    defaultDestination={state.backendState.defaultRestoreFolder}
                                                    backendState={state.backendState}
                                                    roots={state.restoreRoots}
                                                    sources={state.originalConfiguration.additionalSources ? Object.keys(state.originalConfiguration.additionalSources) : []}
                                                    source={state.selectedSource ? state.selectedSource : state.restoreSource}
                                                    activeSource={!!state.selectedSource}
                                                    overwrite={state.restoreOverwrite}
                                                    timestamp={state.restoreTimestamp}
                                                    onSubmit={applyChanges}
                                                    onChange={updateRestore}/>}/>
            <Route path="destinations" element={<Destinations destinations={getDestinationList()}
                                                              backendState={state.backendState}
                                                              dontDelete={getUsedDestinations()}
                                                              configurationUpdated={updateDestinations}/>}/>
            <Route path="sources" element={<Sources sources={getSourcesList()}
                                                    backendState={state.backendState}
                                                    configurationUpdated={updateSources}/>}/>
            <Route path="share" element={<Shares shares={getSharesList()}
                                                 password={state.password}
                                                 onSubmit={applyChanges}
                                                 activeShares={state.activatedShares}
                                                 validatedPassword={state.validatedPassword}
                                                 backendState={state.backendState}
                                                 configurationUpdated={updatedShares}/>}/>
            <Route path="sets" element={<Sets sets={state.currentConfiguration.sets}
                                              backendState={state.backendState}
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
                { displayState.statusTitle !== "Currently Inactive" && !displayState.navigation.firstTime && !state.loading &&
                    <LinearProgress style={{position: "fixed", width: "100%", top: "0"}}/>
                }
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
                    <Box style={{...(state.open && {display: 'none'})}} padding={0} margin={0} marginRight={1}
                         component={"img"} src={invertedLogo} maxHeight={40}/>
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
                    <Box component={"img"} src={logo} maxHeight={40}/>
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

                <Slide direction="right" in={state.open && !state.slideAnimation}>
                    <Typography
                        color="darkgray"
                        noWrap
                        fontSize={14}
                        style={{
                            position: "absolute",
                            bottom: "8px",
                            right: "8px",
                            width: "220px",
                            textAlign: "right"
                        }}
                    >
                        {state.backendState && state.backendState.newVersion &&
                            <>
                                <b style={{color: "black"}}>New version available now!</b>
                                <br/>
                                <br/>
                                <Button variant={"contained"}
                                        onClick={() => setState({...state, showNewVersion: true})}>
                                    Get version {state.backendState.newVersion.version}
                                </Button>
                                <br/>
                                <br/>
                            </>
                        }
                        {state.backendState &&
                            <span>Version <span
                                style={{fontWeight: "bold"}}>{state.backendState.version}</span></span>
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
            {state.backendState && state.backendState.newVersion &&
                <Dialog open={state.showNewVersion} onClose={() => setState({...state, showNewVersion: false})}>
                    <DialogTitle>Version {state.backendState?.newVersion?.version}: {state.backendState?.newVersion?.name}</DialogTitle>
                    <DialogContent dividers>
                        <DialogContentText>
                            Released {new Date(state.backendState?.newVersion?.releaseDate * 1000 as number).toLocaleString()}

                            <div dangerouslySetInnerHTML={{
                                __html:
                                    marked(state.backendState?.newVersion?.body as string)
                            }}/>
                        </DialogContentText>
                    </DialogContent>
                    <DialogActions>
                        <Button autoFocus={true} onClick={() => {
                            window.open(state.backendState?.newVersion?.changeLog, '_blank')
                        }}>Changelog</Button>
                        <div style={{flex: '1 0 0'}}/>
                        <Button onClick={() => setState({...state, showNewVersion: false})}>Cancel</Button>
                        <Button variant={"contained"} autoFocus={true} onClick={() => {
                            window.open(state.backendState?.newVersion?.download?.url, '_blank')
                            setState({...state, showNewVersion: false});
                        }}>Download
                            ({Math.round((state.backendState?.newVersion?.download?.size as number) / 1024 / 1024)}mb)</Button>
                    </DialogActions>
                </Dialog>
            }
        </Box>
    );
}
