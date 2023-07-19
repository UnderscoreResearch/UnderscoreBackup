import * as React from "react";
import {Fragment, ReactNode} from "react";
import {BackupDestination, DestinationMap} from '../api';
import Destination from './Destination';
import {EditableList} from './EditableList';
import {Alert, Checkbox, FormControlLabel, Stack} from "@mui/material";
import {ApplicationContext, destinationList, useApplication} from "../utils/ApplicationContext";
import DividerWithText from "../3rdparty/react-js-cron-mui/components/DividerWithText";

interface DestinationState {
    valid: boolean,
    destination: BackupDestination,
    id: string
}

export interface DestinationsProps {
    destinationsUpdated: (destinationsValid: boolean) => void
}

function getUsedDestinations(appContext: ApplicationContext) {
    const allDestinations = [appContext.currentConfiguration.manifest.destination];
    appContext.currentConfiguration.sets.forEach(set =>
        set.destinations.forEach(destination => allDestinations.push(destination)))
    // @ts-ignore
    return [...new Set(allDestinations)];
}

function isManifestDestination(appContext: ApplicationContext, item: DestinationState) {
    return appContext.currentConfiguration.manifest.destination === item.id ||
        (appContext.currentConfiguration.manifest.additionalDestinations && appContext.currentConfiguration.manifest.additionalDestinations.indexOf(item.id) >= 0);
}

export default function Destinations(props: DestinationsProps) {
    const appContext = useApplication();
    const [state, setState] = React.useState(() => {
        const destinations = destinationList(appContext);

        let destinationId: string | null = window.sessionStorage.getItem("destinationId");
        if (destinationId) {
            const pendingDestination = JSON.parse(window.sessionStorage.getItem("destination") as string);
            for (let i = 0; i < destinations.length; i++) {
                if (destinations[i].id === destinationId) {
                    destinations[i].destination = pendingDestination;
                    destinationId = null;
                    break;
                }
            }
            if (destinationId) {
                destinations.push({destination: pendingDestination, id: destinationId});
            }
        }

        return destinations.map(dest => {
            return {
                valid: true,
                destination: dest.destination,
                id: dest.id
            } as DestinationState
        })
    });
    const dontDelete = getUsedDestinations(appContext);

    function sendUpdate(newState: DestinationState[]) {
        let newVal: DestinationMap = {};
        newState.forEach((item) => {
            newVal[item.id] = item.destination;
        })

        appContext.setState((oldState) => ({
            ...oldState,
            currentConfiguration: {
                ...oldState.currentConfiguration,
                destinations: newVal
            }
        }));

        props.destinationsUpdated(!newState.some(item => !item.valid))
    }

    function destinationChanged(items: DestinationState[]) {
        setState(items);
        sendUpdate(items);
    }

    function findNewId() {
        var i = 1;
        while (state.some(item => item.id === "d" + i)) {
            i++;
        }
        return "d" + i;
    }

    function toggleManifestDestination(destinationId: string) {
        const currentManifest = {
            ...appContext.currentConfiguration.manifest
        };
        if (currentManifest.destination === destinationId) {
            if (currentManifest.additionalDestinations && currentManifest.additionalDestinations.length > 0) {
                currentManifest.destination = currentManifest.additionalDestinations[0];
                currentManifest.additionalDestinations.splice(0, 1);
            }
        } else if (!currentManifest.additionalDestinations) {
            currentManifest.additionalDestinations = [destinationId];
        } else {
            const ind = currentManifest.additionalDestinations.indexOf(destinationId);
            if (ind >= 0) {
                currentManifest.additionalDestinations.splice(ind, 1);
            } else {
                currentManifest.additionalDestinations.push(destinationId);
            }
        }
        appContext.setState((oldState) => ({
            ...oldState,
            currentConfiguration: {
                ...oldState.currentConfiguration,
                manifest: currentManifest
            }
        }));
    }

    return <Stack spacing={2} style={{width: "100%"}}>
        <Alert severity="warning">Making any changes to the location of existing destinations could cause you to loose
            the information backed up in those locations!</Alert>

        {EditableList<DestinationState>({
            deleteBelow: true,
            createNewItem: () => {
                return {
                    id: findNewId(),
                    valid: false,
                    destination: {
                        type: "FILE",
                        endpointUri: ''
                    }
                } as DestinationState
            },
            allowDrop: (item) => !dontDelete.includes(item.id),
            onItemChanged: destinationChanged,
            items: state,
            createItem: (item, itemUpdated: (item: DestinationState) => void) => {
                let postElement: ReactNode | undefined;
                switch (item.destination.type) {
                    default:
                        if (((appContext.currentConfiguration.manifest.additionalDestinations &&
                                    appContext.currentConfiguration.manifest.additionalDestinations.length > 0) ||
                                appContext.currentConfiguration.manifest.destination !== item.id) &&
                            (item.destination.errorCorrection === undefined || item.destination.errorCorrection === "NONE")) {
                            postElement = <Fragment>
                                <DividerWithText>Store manifest</DividerWithText>
                                <FormControlLabel control={<Checkbox
                                    checked={isManifestDestination(appContext, item)}
                                    onChange={(e) => toggleManifestDestination(item.id)}
                                />} label="Store metadata to allow adoption from this destination"/>
                            </Fragment>
                        } else {
                            postElement = <Fragment>
                                <DividerWithText>Store manifest</DividerWithText>
                                <FormControlLabel control={<Checkbox
                                    checked={isManifestDestination(appContext, item)}
                                    onChange={(e) => {
                                    }}
                                    disabled={true}
                                />} label="Store metadata to allow adoption from this destination"/>
                            </Fragment>
                        }
                }

                return <Destination id={item.id}
                                    backendState={appContext.backendState}
                                    destination={item.destination}
                                    manifestDestination={isManifestDestination(appContext, item)}
                                    destinationUpdated={(valid, destination) => {
                                        itemUpdated({valid: valid, destination: destination, id: item.id});
                                    }
                                    }
                                    postElement={postElement}
                />
            }
        })}
    </Stack>
}