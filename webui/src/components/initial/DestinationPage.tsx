import {Stack} from "@mui/material";
import Paper from "@mui/material/Paper";
import Typography from "@mui/material/Typography";
import Destination from "../Destination";
import * as React from "react";
import {useEffect} from "react";
import {useApplication} from "../../utils/ApplicationContext";
import {BackupDestination, BackupManifest, BackupState} from "../../api";
import {NextButton} from "./NextButton";

export interface DestinationPageProps {
    onPageChange: (page: string) => void
}

interface DestinationPageState {
    valid: boolean,
    awaitingValidation: boolean
}

export default function DestinationPage(props: DestinationPageProps) {
    const appContext = useApplication();

    const currentDestination = appContext.currentConfiguration &&
    appContext.currentConfiguration.manifest &&
    appContext.currentConfiguration.manifest.destination &&
    appContext.currentConfiguration.destinations
        ? appContext.currentConfiguration.destinations[appContext.currentConfiguration.manifest.destination]
        : {type: "UB", endpointUri: ""};

    const [state, setState] = React.useState({
        valid: !!currentDestination.endpointUri,
        awaitingValidation: false
    } as DestinationPageState);

    function configurationUpdate(valid: boolean, val: BackupDestination) {
        if (valid) {
            appContext.setState((oldState) => {
                const newState = {...oldState};

                let manifest: BackupManifest = {
                    destination: "d0",
                    optimizeSchedule: "0 0 1 * *",
                    authenticationRequired: appContext.backendState.administrator,
                    scheduleRandomize: {
                        duration: 1,
                        unit: "HOURS"
                    }
                };
                if (!newState.currentConfiguration) {
                    newState.currentConfiguration = {
                        sets: [],
                        destinations: {},
                        manifest: manifest
                    };
                } else {
                    if (!newState.currentConfiguration.manifest) {
                        newState.currentConfiguration.manifest = manifest;
                    } else if (newState.currentConfiguration.manifest) {
                        if (!manifest.destination) {
                            manifest.destination = "d0";
                        }
                    }
                }

                if (!newState.currentConfiguration.destinations) {
                    newState.currentConfiguration.destinations = {};
                }
                newState.currentConfiguration.manifest.initialSetup = true;
                newState.currentConfiguration.destinations[newState.currentConfiguration.manifest.destination] = val;
                return newState;
            });
        }
        setState((oldState) => ({...oldState, valid: valid}));
    }

    async function applyChanges() {
        if (state.valid) {
            setState({
                ...state,
                awaitingValidation: true
            })
            const originalSets = appContext.currentConfiguration.sets;
            if (originalSets && originalSets.length > 0) {
                appContext.setState((oldState) => {
                    const newState = {...oldState};
                    newState.currentConfiguration.sets = [];
                    return newState;
                });
            }

            await appContext.applyChanges();

            if (originalSets && originalSets.length > 0) {
                appContext.setState((oldState) => {
                    const newState = {...oldState};
                    newState.currentConfiguration.sets = originalSets;
                    return newState;
                });
            }
        }
    }

    useEffect(() => {
        if (state.awaitingValidation && appContext.destinationsValid && appContext.originalConfiguration.destinations[appContext.originalConfiguration.manifest.destination]) {
            props.onPageChange("security");
            setState({
                ...state,
                awaitingValidation: false
            })
        }
    }, [state.awaitingValidation, appContext.destinationsValid, appContext.originalConfiguration]);

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
                         backendState={appContext.backendState as BackupState}
                         destination={currentDestination}/>
            <NextButton disabled={!state.valid} onClick={applyChanges}/>
        </Paper>
    </Stack>
}