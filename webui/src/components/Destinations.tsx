import * as React from "react";
import {BackupDestination, DestinationMap} from '../api';
import Destination from './Destination';
import {EditableList} from './EditableList';
import {Alert, Stack} from "@mui/material";
import {ApplicationContext, destinationList, useApplication} from "../utils/ApplicationContext";

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
                return <Destination id={item.id}
                                    backendState={appContext.backendState}
                                    destination={item.destination}
                                    manifestDestination={appContext.currentConfiguration.manifest.destination === item.id}
                                    destinationUpdated={(valid, destination) => {
                                        itemUpdated({valid: valid, destination: destination, id: item.id});
                                    }
                                    }/>
            }
        })}
    </Stack>
}