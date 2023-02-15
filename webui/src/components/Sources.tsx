import * as React from "react";
import {BackupDestination, BackupState} from '../api';
import Destination from './Destination';
import {EditableList} from './EditableList';
import {Alert, Stack, TextField} from "@mui/material";
import DividerWithText from "../3rdparty/react-js-cron-mui/components/DividerWithText";

interface SourceState {
    valid: boolean,
    destination: BackupDestination,
    exist: boolean,
    name?: string,
    id: string
}

export interface SourceProps {
    id: string,
    exist: boolean,
    destination: BackupDestination
}

export interface SourcesProps {
    sources: SourceProps[],
    backendState: BackupState,
    configurationUpdated: (valid: boolean, destinations: SourceProps[]) => void
}

function Source(props: {
    id: string, destination: BackupDestination,
    backendState: BackupState,
    destinationUpdated: (valid: boolean, val: BackupDestination) => void;
    exists: boolean,
    name: string, nameUpdated: (name: string) => void
}) {
    return <Destination id={props.id}
                        backendState={props.backendState}
                        destination={props.destination}
                        typeLabel={"Source Manifest Destination Type"}
                        manifestDestination={true}
                        sourceDestination={true}
                        destinationUpdated={props.destinationUpdated}
    >
        <DividerWithText>Name</DividerWithText>
        <div style={{marginLeft: "8px", marginRight: "0px", marginTop: "8px"}}>
            <TextField label="Source Name" variant="outlined"
                       required={true}
                       fullWidth={true}
                       value={props.name}
                       disabled={props.exists}
                       error={!props.name}
                       onChange={(e) => props.nameUpdated(e.target.value)}
            />
        </div>
    </Destination>
}

export default function Sources(props: SourcesProps) {
    const [state, setState] = React.useState(() => {
        return props.sources.map(dest => {
            return {
                valid: true,
                destination: dest.destination,
                exist: dest.exist,
                id: dest.id
            }
        })
    });

    function sendUpdate(newState: SourceState[]) {
        const ids: string[] = newState.map(t => t.name ? t.name.toLowerCase() : t.id.toLowerCase());
        // @ts-ignore
        const deduped = [...new Set(ids)];
        props.configurationUpdated(
            deduped.length == ids.length &&
            !newState.some(item => !item.valid || (!item.exist && !item.name)),
            newState.map(item => {
                return {
                    destination: item.destination,
                    exist: item.exist,
                    id: item.name ? item.name : item.id
                }
            })
        );
    }

    function destinationChanged(items: SourceState[]) {
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
        <Alert severity="warning">Making any changes to the location of existing sources could cause you to loose
            the information backed up in those locations!</Alert>

        {EditableList<SourceState>({
            deleteBelow: true,
            createNewItem: () => {
                return {
                    id: findNewId(),
                    valid: false,
                    exist: false,
                    destination: {
                        type: "FILE",
                        endpointUri: ''
                    }
                } as SourceState
            },
            allowDrop: (item) => true,
            onItemChanged: destinationChanged,
            items: state,
            createItem: (item, itemUpdated: (item: SourceState) => void) => {
                return <Source name={item.exist ? item.id : (item.name ? item.name : "")}
                               nameUpdated={(name) => itemUpdated({
                                   ...item,
                                   name: name
                               })}
                               backendState={props.backendState}
                               id={item.id}
                               destination={item.destination}
                               exists={item.exist}
                               destinationUpdated={(valid, destination) => {
                                   itemUpdated({
                                       valid: valid, destination: destination, id: item.id,
                                       exist: item.exist, name: item.name
                                   });
                               }}
                />
            }
        })}
    </Stack>
}