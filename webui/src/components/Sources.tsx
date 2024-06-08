import * as React from "react";
import {BackupDestination, BackupState, DestinationMap} from '../api';
import Destination from './Destination';
import {EditableList} from './EditableList';
import {Stack, TextField} from "@mui/material";
import DividerWithText from "../3rdparty/react-js-cron-mui/components/DividerWithText";
import {ApplicationContext, useApplication} from "../utils/ApplicationContext";

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
    sourcesUpdated: (valid: boolean) => void
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
        <div style={{marginLeft: "0px", marginRight: "0px", marginTop: "8px"}}>
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

function sourceExist(appContext: ApplicationContext, key: string): boolean {
    if (appContext.originalConfiguration && appContext.originalConfiguration.additionalSources) {
        return !!appContext.originalConfiguration.additionalSources[key]
    }
    return false;
}

function getSourcesList(appContext: ApplicationContext): SourceProps[] {
    const keys = appContext.currentConfiguration.additionalSources
        ? Object.keys(appContext.currentConfiguration.additionalSources)
        : [];
    keys.sort();

    return keys.map(key => {
        return {
            destination: (appContext.currentConfiguration.additionalSources as DestinationMap)[key] as BackupDestination,
            exist: sourceExist(appContext, key),
            id: key
        }
    });
}

export default function Sources(props: SourcesProps) {
    const appContext = useApplication();
    const [state, setState] = React.useState(() => {
        return getSourcesList(appContext).map(dest => {
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
        const deDuped = [...new Set(ids)];
        props.sourcesUpdated(
            deDuped.length == ids.length &&
            !newState.some(item => !item.valid || (!item.exist && !item.name)),
        );

        let newVal: DestinationMap = {};
        newState.forEach((item) => {
            newVal[item.id] = item.destination;
        })

        appContext.setState((oldState) => ({
            ...oldState,
            currentConfiguration: {
                ...oldState.currentConfiguration,
                additionalSources: newVal
            }
        }))
    }

    function destinationChanged(items: SourceState[]) {
        setState(items);
        sendUpdate(items);
    }

    function findNewId() {
        let i = 1;
        while (state.some(item => item.id === "d" + i)) {
            i++;
        }
        return "d" + i;
    }

    return <Stack spacing={2} style={{width: "100%"}}>

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
            allowDrop: () => true,
            onItemChanged: destinationChanged,
            items: state,
            createItem: (item, itemUpdated: (item: SourceState) => void) => {
                return <Source name={item.exist ? item.id : (item.name ? item.name : "")}
                               nameUpdated={(name) => itemUpdated({
                                   ...item,
                                   name: name
                               })}
                               backendState={appContext.backendState}
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