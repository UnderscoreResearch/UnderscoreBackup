import {
    BackupConfiguration,
    BackupDestination,
    BackupState,
    getConfiguration,
    getDefaultState,
    getEncryptionKey,
    getState,
    listActiveShares,
    needPrivateKeyPassword,
    postConfiguration,
    rebuildAvailable,
    submitPrivateKeyPassword
} from "../api";
import React, {Dispatch, SetStateAction, useEffect} from "react";

export interface DestinationProp {
    id: string,
    destination: BackupDestination
}

export interface ApplicationState {
    activatedShares?: string[],
    rebuildAvailable: boolean,
    hasKey: boolean,
    initialLoad: boolean,
    originalConfiguration: BackupConfiguration,
    currentConfiguration: BackupConfiguration,
    needAuthentication: boolean,
    password?: string,
    validatedPassword: boolean,
    setupComplete: boolean,
    shareEncryptionNeeded: boolean,
    destinationsValid: boolean,
    selectedSource?: string,
    selectedSourceName?: string,
    backendState: BackupState,
}

export interface ApplicationContext extends ApplicationState {
    isBusy: () => boolean
    setBusy: Dispatch<SetStateAction<boolean>>,
    busyOperation: (operation: () => Promise<void>) => Promise<void>,

    update: (password: string) => Promise<void>,
    applyChanges: (callback?: (oldState: ApplicationState, newState: ApplicationState) => Promise<void>) => Promise<boolean>,
    hasScheduledSets: () => boolean,
    interactiveEnabled: () => boolean,
    updateBackendState: (validatedPassword?: boolean) => Promise<void>,
    setState: Dispatch<SetStateAction<ApplicationState>>,
    state: ApplicationState
}

function defaultEmptyConfig(): BackupConfiguration {
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

const defaultConfig: ApplicationState = {
    rebuildAvailable: false,
    hasKey: false,
    needAuthentication: false,
    initialLoad: true,
    originalConfiguration: defaultEmptyConfig(),
    currentConfiguration: defaultEmptyConfig(),
    setupComplete: false,
    destinationsValid: true,
    validatedPassword: false,
    activatedShares: [],
    shareEncryptionNeeded: false,
    backendState: getDefaultState()
}

function generateApplicationContext(): ApplicationContext {
    const [state, setState] = React.useState(defaultConfig);
    const [busy, setBusy] = React.useState(false);

    const [operationBusy, setOperationBusy] = React.useState(false);


    async function fetchConfig(password?: string): Promise<ApplicationState> {
        let newState = {} as ApplicationState;
        newState.initialLoad = false;
        newState.backendState = await getState();
        if (await needPrivateKeyPassword(true)) {
            if (password) {
                await submitPrivateKeyPassword(password);
                newState.backendState = await getState();
            }
            if (await needPrivateKeyPassword(true)) {
                newState.needAuthentication = true;
                return newState;
            }
        }

        newState.needAuthentication = false;
        const newConfig = await getConfiguration();
        if (newConfig !== undefined) {
            let newRebuildAvailable = false;
            if (Object.keys(newConfig.destinations).length > 0) {
                const hasKey = await getEncryptionKey(password);
                if (hasKey && password) {
                    newState.validatedPassword = true;
                }
                if (!hasKey && !password && newConfig.manifest.destination) {
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
                newState.currentConfiguration.missingRetention = newState.backendState.defaultSet.retention;
            }

            if (newState.selectedSource || (newState.hasKey &&
                newState.originalConfiguration &&
                newState.originalConfiguration.sets &&
                newState.originalConfiguration.sets.length > 0 &&
                newState.originalConfiguration.manifest &&
                newState.originalConfiguration.manifest.destination &&
                !newState.originalConfiguration.manifest.initialSetup)) {
                newState.setupComplete = true;
            }
        }
        return newState;
    }

    async function updateBackendState(validatedPassword?: boolean) {
        setBusy(true);
        try {
            const newBackendState = await getState();
            setState((oldState) => ({
                ...oldState,
                backendState: newBackendState,
                validatedPassword: validatedPassword !== undefined ? validatedPassword : oldState.validatedPassword
            }));
        } finally {
            setBusy(false);
        }
    }

    async function update(password?: string) {
        const newState = await fetchConfig(password)
        setState((oldState) => ({...oldState, ...newState}));
    }

    function applyChanges(callback?: (oldState: ApplicationState, newState: ApplicationState) => Promise<void>): Promise<boolean> {

        setBusy(true);
        return new Promise<boolean>((resolve) => {
            setState((oldState) => {
                function complete(newState: ApplicationState) {
                    setState({
                        ...newState,
                        validatedPassword: oldState.validatedPassword,
                    });
                    setBusy(false);
                    resolve(true);
                }

                postConfiguration(oldState.currentConfiguration).then((result) => {
                    if (result) {
                        fetchConfig().then(newState => {
                            if (callback) {
                                callback(oldState, newState).then(() => complete(newState));
                            } else
                                complete(newState);
                        });
                    } else {
                        setBusy(false);
                        resolve(false);
                    }
                });

                return {...oldState}
            });
        });
    }

    useEffect(() => {
        fetchConfig()
            .then(newState => {
                setState({...newState})
                setBusy(false);
            });
    }, []);

    async function busyOperation(operation: () => Promise<void>) {
        setOperationBusy(true);
        try {
            await operation();
        } finally {
            setOperationBusy(false);
        }
    }

    function hasScheduledSets() {
        return !!(state.originalConfiguration && state.originalConfiguration.sets
            && state.originalConfiguration.sets.find((item) => item.schedule));
    }

    function interactiveEnabled() {
        return !!(state.originalConfiguration && state.originalConfiguration.manifest
            && state.originalConfiguration.manifest.interactiveBackup);
    }

    return {
        ...state,
        isBusy: () => busy || operationBusy,
        setBusy: setBusy,
        busyOperation,
        update: update,
        updateBackendState: updateBackendState,
        setState: setState,
        applyChanges: applyChanges,
        hasScheduledSets: hasScheduledSets,
        interactiveEnabled: interactiveEnabled,
        state: state
    }
}

const applicationContext = React.createContext(defaultConfig as ApplicationContext);
export const useApplication = () => React.useContext(applicationContext);

export function ApplicationContextProvider(props: { children: any }) {
    const context = generateApplicationContext();
    return <applicationContext.Provider value={context}>{props.children}</applicationContext.Provider>;
}

export function destinationList(appContext: ApplicationContext): DestinationProp[] {
    const keys = Object.keys(appContext.currentConfiguration.destinations);
    keys.sort();

    return keys.map(key => {
        return {
            destination: appContext.currentConfiguration.destinations[key] as BackupDestination,
            id: key
        }
    });
}
