import {MainAppSkeleton} from "./components/MainAppSkeleton";
import React, {useEffect} from 'react';
import {Step, StepButton, Stepper} from "@mui/material";
import {ServicePage} from "./components/initial/ServicePage";
import {RemoveCircleOutline} from "@mui/icons-material";
import {useApplication} from "./utils/ApplicationContext";
import {Route, Routes, useLocation, useNavigate} from "react-router-dom";
import {SourceResponse} from "./api/service";
import {BackupConfiguration} from "./api";
import AuthorizeAccept from "./components/AuthorizeAccept";
import ContentPage from "./components/initial/ContentPage";
import SourcePage from "./components/initial/SourcePage";
import DestinationPage from "./components/initial/DestinationPage";
import SecurityPage from "./components/initial/SecurityPage";

interface InitialSetupState {
    maximumStep: number,
    selectedSource?: SourceResponse,
    secretRegion?: string
}

const pages: string[] = [
    "connect",
    "source",
    "destination",
    "security",
    "contents"
];

function pageTitle(page: string, rebuilding: boolean): string {
    switch (page) {
        default:
            return "Connect to account";
        case "source":
            return "Select source";
        case "destination":
            return "Destination";
        case "security":
            return "Security";
        case "contents":
            if (rebuilding)
                return "Rebuild";
            return "Contents & schedule";
    }
}

function pageIndex(page: string) {
    return pages.findIndex((p) => p === page);
}

function getSecretRegionFromDestination(configuration: BackupConfiguration) {
    if (configuration &&
        configuration.destinations &&
        configuration.destinations[configuration.manifest.destination] &&
        configuration.destinations[configuration.manifest.destination].type === "UB") {
        return configuration.destinations[configuration.manifest.destination].endpointUri;
    }
    return "us-west";
}

export default function InitialSetup() {
    const nav = useNavigate();
    const appContext = useApplication();
    const location = useLocation();
    const [state, setState] = React.useState<InitialSetupState>({
        maximumStep: 0,
        selectedSource: undefined,
        secretRegion: getSecretRegionFromDestination(appContext.currentConfiguration)
    });

    let currentStep = pageIndex(location.pathname.substring(1));
    if (currentStep < 0)
        currentStep = 0;

    const rebuildActive = appContext.hasKey &&
        appContext.backendState.validDestinations &&
        !appContext.backendState.repositoryReady;

    const rebuilding = rebuildActive || appContext.rebuildAvailable || !!state.selectedSource;

    function disabledStep(page: string, icon: boolean = false) {
        const ind = pageIndex(page);
        if (ind > state.maximumStep) {
            return true;
        }
        if (page === "source") {
            if (!appContext.backendState.serviceConnected)
                return true;
        }
        if (page === "destination") {
            if (state.selectedSource) {
                return true;
            }
        }
        if (page === "security") {
            if (!appContext.currentConfiguration && !appContext.rebuildAvailable && !state.selectedSource) {
                return true;
            }
        }
        if (page !== "contents" && !icon) {
            if (rebuildActive) {
                return true;
            }
        }
        return false;
    }

    function stepIcon(page: string) {
        const ind = pageIndex(page);
        if (ind > state.maximumStep) {
            return undefined;
        }
        return disabledStep(page, true) ? <RemoveCircleOutline/> : undefined;
    }

    function changePage(page: string) {
        const ind = pageIndex(page);
        setState(oldState => ({
            ...oldState,
            maximumStep: Math.max(oldState.maximumStep, ind)
        }));
        nav("/" + page);
    }

    function clickedPage(page: string) {
        const ind = pageIndex(page);
        if (!disabledStep(pages[ind]) && ind <= state.maximumStep) {
            return () => changePage(page);
        }
    }

    const navigation = location.pathname === "/authorizeaccept" ?
        <></>
        :
        <div style={{margin: "1em"}}>
            <Stepper activeStep={currentStep} orientation="vertical">
                {
                    pages.map((page) =>
                        <Step key={page} disabled={disabledStep(page)}>
                            <StepButton icon={stepIcon(page)} onClick={clickedPage(page)}>
                                {pageTitle(page, rebuilding)}
                            </StepButton>
                        </Step>
                    )
                }
            </Stepper>
        </div>;

    useEffect(() => {
        if (currentStep > pageIndex("source")) {
            if (appContext.backendState.serviceConnected && !appContext.backendState.serviceSourceId) {
                changePage("source");
            }
        }
    }, [appContext.backendState]);

    async function authenticatedService(): Promise<void> {
        await appContext.updateBackendState();
        changePage("source");
    }

    async function disconnectService(): Promise<void> {
        await appContext.updateBackendState();
        setState((oldState) => ({
            ...oldState,
            maximumStep: pageIndex("connect")
        }));
        changePage("connect");
    }

    function updateSource(source: SourceResponse | undefined, page: string) {
        setState((oldState) => ({
            ...oldState,
            selectedSource: source,
            maximumStep: pageIndex("page")
        }));
        changePage(page);
    }

    useEffect(() => {
        const newRegion = getSecretRegionFromDestination(appContext.currentConfiguration);
        if (newRegion !== state.secretRegion) {
            setState((oldState) => ({
                ...oldState,
                secretRegion: newRegion
            }));
        }
    }, [appContext.currentConfiguration?.destinations])

    useEffect(() => {
        if (location.pathname !== "/authorizeaccept") {
            if (rebuildActive) {
                const contentsIndex = pageIndex("contents");
                if (state.maximumStep < contentsIndex) {
                    changePage("contents");
                }
            } else {
                if (location.pathname === "/") {
                    if (appContext.backendState.serviceConnected || appContext.destinationsValid) {
                        currentStep = pageIndex("contents");
                    }
                }
                if (currentStep > state.maximumStep) {
                    let newMax: number;
                    if (appContext.hasKey && appContext.destinationsValid) {
                        newMax = pageIndex("contents");
                    } else if (appContext.backendState.serviceConnected) {
                        newMax = pageIndex("source");
                    } else if (appContext.destinationsValid) {
                        newMax = pageIndex("security");
                    } else {
                        newMax = pageIndex("destination");
                    }
                    if (newMax > state.maximumStep) {
                        changePage(pages[Math.min(currentStep, newMax)]);
                        setState((oldState) => ({
                            ...oldState,
                            maximumStep: newMax
                        }));
                    }
                }
            }
        }
    }, [location.pathname, appContext.hasKey, appContext.backendState, currentStep])

    return <MainAppSkeleton title={"Initial setup"} processing={false} navigation={navigation} disallowClose={true}>
        <Routes>
            <Route path="authorizeaccept" element={<AuthorizeAccept updatedToken={authenticatedService}/>}/>
            <Route path="*" element={<ServicePage onPageChange={changePage} onDisconnect={disconnectService}/>}/>
            <Route path="source" element={<SourcePage onSourceChange={updateSource}/>}/>
            <Route path="destination" element={<DestinationPage onPageChange={changePage}/>}/>
            <Route path="security" element={<SecurityPage onPageChange={changePage} secretRegion={state.secretRegion}
                                                          selectedSource={state.selectedSource}/>}/>
            <Route path="contents" element={<ContentPage/>}/>
        </Routes>
    </MainAppSkeleton>
}