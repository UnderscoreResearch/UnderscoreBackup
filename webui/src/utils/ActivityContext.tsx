import React, {useEffect} from "react";
import {getActivity, listActiveShares, StatusLine} from "../api";
import {useApplication} from "./ApplicationContext";

interface ActivityState {
    loading: boolean,
    unresponsive: boolean,
    activity: StatusLine[],
    activatedShares?: string[]
}

export interface ActivityContext extends ActivityState {
    update: () => Promise<void>
}

const activityContext = React.createContext({} as ActivityContext);

const initialState: ActivityState = {
    loading: true,
    unresponsive: false,
    activity: []
};

let lastState: ActivityState = initialState;

function generateActivityContext(): ActivityContext {
    const appContext = useApplication();
    const [state, setState] = React.useState(initialState);

    async function update() {
        const activity = await getActivity(false);
        if (activity === null) { // Auth not ready
            if (lastState && lastState.unresponsive) {
                appContext.update(appContext.password ?? "");
            }
            return;
        }
        let newState: ActivityState
        if (activity === undefined) {
            newState = {
                ...state,
                unresponsive: true,
            };
        } else {
            newState = {
                loading: false,
                unresponsive: false,
                activity: activity
            }
        }
        if (location.href.endsWith("/share")) {
            const shares = await listActiveShares();
            if (shares && shares.activeShares) {
                newState.activatedShares = shares.activeShares;
            }
        }
        if (JSON.stringify(newState) !== JSON.stringify(lastState)) {
            if (lastState && lastState.unresponsive && !newState.unresponsive) {
                appContext.update(appContext.password ?? "");
            }
            lastState = newState;
            setState(newState);
        }
    }

    return {
        ...state,
        update
    }
}

export function useActivity() {
    return React.useContext(activityContext);
}

export function ActivityContextProvider(props: { children: React.ReactNode }) {
    const value = generateActivityContext();
    useEffect(() => {
        setInterval(value.update, 2000);
    }, [])

    return <activityContext.Provider value={value}>
        {props.children}
    </activityContext.Provider>
}
