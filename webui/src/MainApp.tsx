import {ApplicationContext, useApplication} from "./utils/ApplicationContext";
import {useActivity} from "./utils/ActivityContext";
import {MainAppSkeleton} from "./components/MainAppSkeleton";
import {Route, Routes, useLocation, useNavigate} from "react-router-dom";
import React, {useEffect} from "react";
import NavigationMenu from "./components/NavigationMenu";
import {calculateDisplayState} from "./utils/DisplayState";
import {activateShares, BackupSetRoot, initiateRestore, restartSets, selectSource} from "./api";
import {IndividualButtonProps, useButton} from "./utils/ButtonContext";
import {deepEqual} from "fast-equals";
import Restore from "./components/Restore";
import Status from "./components/Status";
import AuthorizeAccept from "./components/AuthorizeAccept";
import Destinations from "./components/Destinations";
import Sets from "./components/Sets";
import Shares from "./components/Shares";
import Sources from "./components/Sources";
import Settings from "./components/Settings";
import {DisplayMessage} from "./App";

interface MainAppState {
    destinationValid: boolean,
    setsValid: boolean,
    sourcesValid: boolean,
    sharesValid: boolean,
    password: string,
    desiredPage?: string,
    restoreRoots: BackupSetRoot[],
    restoreOverwrite: boolean,
    restoreDestination?: string,
    restoreTimestamp?: Date,
    restoreSource?: string,
    restoreIncludeDeleted?: boolean,
    restoreSkipPermissions?: boolean
}

export interface RestorePropsChange {
    password: string,
    timestamp?: Date,
    roots: BackupSetRoot[],
    destination?: string,
    overwrite: boolean,
    source?: string,
    includeDeleted?: boolean,
    skipPermissions?: boolean
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

function needActivation(appContext: ApplicationContext) {
    const keys = appContext.currentConfiguration.shares
        ? Object.keys(appContext.currentConfiguration.shares)
        : [];

    if (appContext.activatedShares) {
        if (appContext.activatedShares.length !== appContext.activatedShares.length) {
            return true;
        }
        return !deepEqual(keys.sort(), appContext.activatedShares.sort());
    }

    return false;
}

let acceptButton: IndividualButtonProps | undefined;
let cancelButton: IndividualButtonProps | undefined;

function invokeAccept() {
    if (acceptButton && acceptButton.action && !acceptButton.disabled) {
        acceptButton.action();
    }
}

function invokeCancel() {
    if (cancelButton && cancelButton.action && !cancelButton.disabled) {
        cancelButton.action();
    }
}

function getDefaultState() {
    return {
        destinationValid: true,
        setsValid: true,
        sharesValid: true,
        sourcesValid: true,
        password: "",
        restoreRoots: [],
        restoreOverwrite: false,
        restoreIncludeDeleted: false
    };
}

export default function MainApp() {
    const appContext = useApplication();
    const buttonContext = useButton();
    const reactLocation = useLocation();
    const nav = useNavigate();
    const activityContext = useActivity();
    const [state, setState] = React.useState<MainAppState>(getDefaultState());

    async function updateBackendState(): Promise<void> {
        await appContext.updateBackendState();
    }

    const validConfig = state.destinationValid && state.setsValid && state.sourcesValid && state.sharesValid;

    const displayState = calculateDisplayState(appContext, activityContext, validConfig, reactLocation);
    const navigation = <NavigationMenu  {...displayState.navigation}/>

    async function setInteractiveBackup(start: boolean) {
        appContext.setState((oldState) => ({
            ...oldState,
            currentConfiguration: {
                ...oldState.currentConfiguration,
                manifest: {
                    ...oldState.currentConfiguration.manifest,
                    interactiveBackup: start
                }
            }
        }));
        await appContext.applyChanges();
    }

    function changeBackup(start: boolean) {
        if (start && (appContext.interactiveEnabled() || !appContext.hasScheduledSets())) {
            appContext.busyOperation(async () => {
                if (!appContext.interactiveEnabled()) {
                    await setInteractiveBackup(start);
                }
                await restartSets();
                await appContext.updateBackendState();
                await activityContext.update();
            });
        } else {
            appContext.busyOperation(async () => {
                await setInteractiveBackup(start);
                await appContext.updateBackendState();
                await activityContext.update();
            });
        }
    }

    async function startRestoreBusy() {
        DisplayMessage("Restore started", "info");
        await initiateRestore({
            password: state.password ? state.password : "",
            destination: state.restoreDestination,
            files: state.restoreRoots,
            overwrite: state.restoreOverwrite,
            timestamp: state.restoreTimestamp ? state.restoreTimestamp.getTime() : undefined,
            includeDeleted: state.restoreIncludeDeleted ? state.restoreIncludeDeleted : false,
            skipPermissions: state.restoreSkipPermissions ? state.restoreSkipPermissions : false
        });
        await activityContext.update();
    }

    async function selectRestoreSourceBusy() {
        let response = await selectSource(
            state.restoreSource ? state.restoreSource : "-", state.password);

        let desiredPage: string = "";
        let validDestination = true;
        if (response) {
            if (response === "destinations") {
                desiredPage = "destinations";
                validDestination = false;
            }
        }

        if (response !== undefined) {
            await appContext.update(state.password);
            await activityContext.update();

            if (!validDestination || desiredPage) {
                setState((oldState) => ({
                    ...oldState,
                    destinationValid: validDestination,
                    desiredPage: desiredPage
                }));
            }
        }
    }

    async function exitSourceBusy() {
        await selectSource("-", undefined);
        await appContext.update("");
        appContext.setState((oldState) => ({
            ...oldState,
            validatedPassword: false
        }));
        setState(getDefaultState());
        await activityContext.update();
    }

    async function activateSharesBusy() {
        if (await activateShares(state.password)) {
            await appContext.updateBackendState();
        }
        await activityContext.update();
    }

    function updateSharedButtonState() {
        let acceptButton: IndividualButtonProps | undefined = undefined;
        let cancelButton: IndividualButtonProps | undefined = undefined;

        if (displayState.configChanged) {
            acceptButton = {
                title: "Save",
                disabled: !validConfig,
                action: () => appContext.applyChanges(async (oldState, newState) => {
                    if (newState.selectedSource &&
                        state.password &&
                        oldState.backendState && !oldState.backendState.validDestinations &&
                        oldState.validatedPassword &&
                        newState.backendState && newState.backendState.validDestinations) {
                        await selectSource(newState.selectedSource, state.password);
                    }
                })
            };
            cancelButton = {
                title: "Revert",
                disabled: false,
                action: () => location.reload()
            }
        } else if (displayState.restoreInProgress) {
            acceptButton = {
                title: "Cancel Restore",
                disabled: false,
                color: "error",
                action: () => appContext.applyChanges()
            }
        }

        switch (displayState.navigation.currentPage) {
            case "restore":
                if (!appContext.validatedPassword) {
                    acceptButton = {
                        title: "Validate Password",
                        disabled: state.password.length === 0,
                        action: () => appContext.busyOperation(selectRestoreSourceBusy)
                    }
                } else {
                    if (!appContext.selectedSource) {
                        cancelButton = {
                            title: "Back",
                            disabled: false,
                            action: () => {
                                appContext.setState((oldState) => ({
                                    ...oldState,
                                    validatedPassword: false
                                }));
                            }
                        }
                    }

                    acceptButton = {
                        title: "Start Restore",
                        disabled: !validConfig || !state.restoreRoots || state.restoreRoots.length === 0,
                        action: () => appContext.busyOperation(startRestoreBusy)
                    };
                }
                break;
            case "share":
                if (!appContext.validatedPassword) {
                    acceptButton = {
                        title: "Validate Password",
                        disabled: !validConfig || state.password.length === 0,
                        action: () => appContext.busyOperation(
                            async () => {
                                await appContext.update(state.password)
                            })
                    }
                } else if (!acceptButton && (needActivation(appContext) || appContext.shareEncryptionNeeded)) {
                    acceptButton = {
                        title: "Activate Shares",
                        disabled: !validConfig,
                        action: () => appContext.busyOperation(activateSharesBusy)
                    }
                }
                break;
        }

        if (!cancelButton && appContext.selectedSource) {
            cancelButton = {
                title: "Exit",
                disabled: false,
                action: () => appContext.busyOperation(exitSourceBusy)
            }
        } else if (!acceptButton && validConfig) {
            const disabled = !validConfig || !displayState.backupCanStart;

            if (displayState.backupInProgress) {
                acceptButton = {
                    title: "Stop Backup",
                    color: "error",
                    disabled: false,
                    action: () => changeBackup(false)
                };

                if (appContext.interactiveEnabled() && displayState.backupCanStart) {
                    cancelButton = {
                        title: "Backup Now",
                        color: "primary",
                        disabled: disabled,
                        action: () => changeBackup(true)
                    };
                }
            } else {
                if (appContext.interactiveEnabled()) {
                    cancelButton = {
                        title: "Backup Now",
                        color: "primary",
                        disabled: disabled,
                        action: () => changeBackup(true)
                    };

                    if (appContext.hasScheduledSets()) {
                        acceptButton = {
                            title: "Pause Schedule",
                            color: "error",
                            disabled: false,
                            action: () => changeBackup(false)
                        }
                    }
                } else {
                    acceptButton = {
                        title: appContext.hasScheduledSets() ? "Continue Backup" : "Backup Now",
                        disabled: disabled,
                        action: () => changeBackup(true)
                    }
                }
            }
        }

        if (appContext.isBusy()) {
            if (acceptButton)
                acceptButton = {
                    ...acceptButton,
                    disabled: true
                }

            if (cancelButton)
                cancelButton = {
                    ...cancelButton,
                    disabled: true
                }
        }

        return [acceptButton, cancelButton];
    }

    useEffect(() => {
        if (!buttonContext.accept) {
            buttonContext.setState({
                accept: invokeAccept,
                cancel: invokeCancel
            });
        }
    }, []);

    useEffect(() => {
        if (state.desiredPage) {
            nav("/" + state.desiredPage);
            setState({
                ...state,
                desiredPage: ""
            });
        }
    }, [state.desiredPage]);

    useEffect(() => {
        if (!state.password && appContext.validatedPassword) {
            appContext.setState((oldState) => ({
                ...oldState,
                validatedPassword: false
            }));
        }
    }, [state.password, appContext.validatedPassword]);

    useEffect(() => {
        if (appContext.selectedSource && !displayState.rebuildInProgress && displayState.navigation.restore) {
            nav("/restore");
        }
    }, [appContext.selectedSource, displayState.rebuildInProgress]);

    if (displayState.invalidPage) {
        if (displayState.invalidPage && !state.desiredPage) {
            setState((oldState) => ({
                ...oldState,
                desiredPage: "status"
            }));
        }

        return <MainAppSkeleton title={displayState.statusTitle} processing={displayState.processing}
                                displayState={displayState}
                                navigation={navigation} disallowClose={false}
                                acceptButton={acceptButton} cancelButton={cancelButton}>
        </MainAppSkeleton>
    }

    // Hack, but can't figure out how do refer to it from above without it.
    [acceptButton, cancelButton] = updateSharedButtonState();

    return <MainAppSkeleton title={displayState.statusTitle} processing={displayState.processing}
                            navigation={navigation} disallowClose={false}
                            displayState={displayState}
                            acceptButton={acceptButton} cancelButton={cancelButton}>
        <Routes>
            <Route path="*" element={<Status status={activityContext.activity}/>}/>
            <Route path="authorizeaccept" element={<AuthorizeAccept updatedToken={updateBackendState}/>}/>
            <Route path="settings" element={<Settings/>}/>
            <Route path="destinations" element={<Destinations destinationsUpdated={(valid) => setState((oldState) => ({
                ...oldState,
                destinationValid: valid
            }))}/>}/>
            <Route path="sets" element={<Sets
                allowReset={(displayState.backupCanStart || displayState.backupInProgress) && !!appContext.currentConfiguration.manifest.interactiveBackup}
                setsUpdated={(valid) => setState((oldState) => ({
                    ...oldState,
                    setsValid: valid
                }))}/>}/>
            <Route path="sources" element={<Sources
                sourcesUpdated={(valid) => setState((oldState) => ({...oldState, sourcesValid: valid}))}/>}/>
            <Route path="share" element={<Shares password={state.password}
                                                 sharesUpdated={(valid, password) => setState((oldState) => ({
                                                     ...oldState,
                                                     password: password,
                                                     sharesValid: valid
                                                 }))}/>}/>
            <Route path="restore" element={
                <Restore password={state.password}
                         destination={state.restoreDestination}
                         roots={state.restoreRoots}
                         overwrite={state.restoreOverwrite}
                         includeDeleted={state.restoreIncludeDeleted}
                         restoreUpdated={(newState: RestorePropsChange) =>
                             setState((oldState) => ({
                                 ...oldState,
                                 password: newState.password,
                                 restoreDestination: newState.destination,
                                 restoreRoots: newState.roots,
                                 restoreSource: newState.source,
                                 restoreOverwrite: newState.overwrite,
                                 restoreTimestamp: newState.timestamp,
                                 restoreIncludeDeleted: newState.includeDeleted,
                                 restoreSkipPermissions: newState.skipPermissions
                             }))}
                         timestamp={state.restoreTimestamp}/>}/>
        </Routes>
    </MainAppSkeleton>
}