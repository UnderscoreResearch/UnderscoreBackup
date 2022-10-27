import * as React from "react";
import {BackupDefaults, BackupSet} from "../api";
import {EditableList} from "./EditableList";
import SetConfig from "./SetConfig";
import {DestinationProp} from "./Destinations";

export interface SetsProps {
    sets: BackupSet[],
    allowReset: boolean,
    defaults: BackupDefaults,
    destinations: DestinationProp[],
    configurationUpdated: (valid: boolean, destinations: BackupSet[]) => void
}

interface SetState {
    valid: boolean,
    set: BackupSet
}

export default function Sets(props: SetsProps) {
    const [state, setState] = React.useState(props.sets.map(dest => {
        return {
            valid: true,
            set: dest,
        } as SetState
    }));

    function findNewId() {
        var i = 1;
        while (state.some(item => item.set.id === "s" + i)) {
            i++;
        }
        return "s" + i;
    }

    function createEmptySet(): SetState {
        const useState = props.sets.length > 0 ? state[0].set : props.defaults.set;
        return {
            set: {
                id: findNewId(),
                destinations: props.defaults.set.destinations,
                roots: [],
                exclusions: useState.exclusions,
                retention: useState.retention,
                schedule: useState.schedule
            },
            valid: false
        }
    }

    function sendUpdate(newState: SetState[]) {
        const ids: string[] = newState.map(t => t.set.id);
        // @ts-ignore
        const deduped = [...new Set(ids)];

        props.configurationUpdated(
            deduped.length == newState.length && !newState.some(item => !item.valid),
            newState.map(item => item.set)
        );
    }

    function setsChanged(items: SetState[]) {
        setState(items);
        sendUpdate(items);
    }

    return EditableList<SetState>({
        deleteBelow: true,
        createNewItem: createEmptySet,
        allowDrop: (item) => state.length > 1,
        onItemChanged: setsChanged,
        allowReorder: true,
        items: state,
        createItem: (item, itemUpdated: (item: SetState) => void) => {
            return <SetConfig set={item.set}
                              destinations={props.destinations}
                              allowReset={props.allowReset}
                              defaults={props.defaults}
                              setUpdated={(valid, set) => {
                                  itemUpdated({valid: valid, set: set});
                              }}/>
        }
    });
}