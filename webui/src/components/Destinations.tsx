import * as React from "react";
import {BackupDestination} from '../api';
import Destination from './Destination';
import {EditableList} from './EditableList';
import {Alert, Stack} from "@mui/material";

interface DestinationState {
    valid: boolean,
    destination: BackupDestination,
    id: string
}

export interface DestinationProp {
    id: string,
    destination: BackupDestination
}

export interface DestinationsProps {
    destinations: DestinationProp[],
    dontDelete: string[]
    configurationUpdated: (valid : boolean, destinations: DestinationProp[]) => void
}

export default function Destinations(props : DestinationsProps) {
    const [state, setState] = React.useState(props.destinations.map(dest => {
        return {
            valid: true,
            destination: dest.destination,
            id: dest.id
        } as DestinationState
    }));

    function sendUpdate(newState: DestinationState[]) {
        props.configurationUpdated(
            !newState.some(item => !item.valid),
            newState.map(item => {
                return {
                    destination: item.destination,
                    id: item.id
                }
            })
        );
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
            allowDrop: (item) => !props.dontDelete.includes(item.id),
            onItemChanged: destinationChanged,
            items: state,
            createItem: (item, itemUpdated: (item: DestinationState) => void) => {
                return <Destination destination={item.destination}
                                    manifestDestination={props.dontDelete.includes(item.id)}
                                    destinationUpdated={(valid, destination) => {
                                        itemUpdated({valid: valid, destination: destination, id: item.id});
                                    }
                                    }/>
            }
        })}
    </Stack>
}