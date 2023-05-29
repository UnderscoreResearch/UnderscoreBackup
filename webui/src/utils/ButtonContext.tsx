import React, {Dispatch, SetStateAction} from "react";

export interface IndividualButtonProps {
    action?: () => void,
    title: string,
    disabled?: boolean,
    color?: 'inherit' | 'primary' | 'secondary' | 'success' | 'error' | 'info' | 'warning'
}

export interface ButtonState {
    accept: () => void,
    cancel: () => void
}

export interface ButtonContext extends ButtonState {
    setState: Dispatch<SetStateAction<ButtonState>>,
}

const buttonContext = React.createContext({} as ButtonContext);

function generateButtonContext(): ButtonContext {
    const [state, setState] = React.useState({} as ButtonState);

    return {
        ...state,
        setState
    }
}

export function useButton() {
    return React.useContext(buttonContext);
}

export function ButtonContextProvider(props: { children: React.ReactNode }) {
    return <buttonContext.Provider value={generateButtonContext()}>
        {props.children}
    </buttonContext.Provider>
}
