import * as React from "react";
import {BackupSet} from "../api";
import {EditableList} from "./EditableList";
import SetConfig from "./SetConfig";
import {destinationList, useApplication} from "../utils/ApplicationContext";

export interface SetsProps {
    allowReset: boolean,
    setsUpdated: (valid: boolean) => void
}

interface SetState {
    valid: boolean,
    set: BackupSet
}

export default function Sets(props: SetsProps) {
    const appContext = useApplication();
    const [state, setState] = React.useState(appContext.currentConfiguration.sets.map(dest => {
        return {
            valid: true,
            set: dest,
        } as SetState
    }));

    const destList = destinationList(appContext);

    function findNewId() {
        let i = 1;
        while (state.some(item => item.set.id === "s" + i)) {
            i++;
        }
        return "s" + i;
    }

    function createEmptySet(): SetState {
        const useState = appContext.currentConfiguration.sets.length > 0 ? state[0].set : appContext.backendState.defaultSet;
        return {
            set: {
                id: findNewId(),
                destinations: appContext.backendState.defaultSet.destinations,
                roots: [],
                exclusions: useState.exclusions,
                retention: useState.retention,
                schedule: useState.schedule
            },
            valid: false
        }
    }

    function sendUpdate(newState: SetState[]) {
        const ids: string[] = newState.map(t => t.set.id.toLowerCase());
        // @ts-ignore
        const deDuped = [...new Set(ids)];

        appContext.setState((oldState) => ({
            ...oldState,
            currentConfiguration: {
                ...oldState.currentConfiguration,
                sets: newState.map(item => item.set)
            }
        }));

        props.setsUpdated(deDuped.length == newState.length && !newState.some(item => !item.valid));
    }

    function setsChanged(items: SetState[]) {
        setState(items);
        sendUpdate(items);
    }

    return EditableList<SetState>({
        deleteBelow: true,
        createNewItem: createEmptySet,
        allowDrop: () => state.length > 1,
        onItemChanged: setsChanged,
        allowReorder: true,
        items: state,
        createItem: (item, itemUpdated: (item: SetState) => void) => {
            return <SetConfig set={item.set}
                              destinations={destList}
                              allowReset={props.allowReset}
                              setUpdated={(valid, set) => {
                                  itemUpdated({valid: valid, set: set});
                              }}/>
        }
    });
}