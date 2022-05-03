import * as React from "react";
import {Checkbox, MenuItem, Select, TextField} from "@mui/material";

export interface SpeedLimitProps {
    speed?: number,
    onChange: (speed?: number) => void
    title: title
}

interface SpeedLimitState {
    enabled: boolean,
    speed: number,
    unit: string
};

function decodeSpeed(speed?: number): SpeedLimitState {
    if (!speed) {
        return {
            speed: 10,
            enabled: false,
            unit: "MB"
        }
    }
    if (speed % (1024 * 1024) == 0) {
        return {
            speed: speed / 1024 / 1024,
            enabled: true,
            unit: "MB"
        }
    }
    if (speed % 1024 == 0) {
        return {
            speed: speed / 1024,
            enabled: true,
            unit: "KB"
        }
    }
    return {
        speed: speed,
        enabled: true,
        unit: "B"
    }
}

export default function SpeedLimit(props: SpeedLimitProps) {
    const [state, setState] = React.useState(decodeSpeed(props.speed));

    function updateState(newState: SpeedLimitState) {
        if (newState.enabled) {
            switch (newState.unit) {
                case "MB":
                    props.onChange(newState.speed * 1024 * 1024);
                    break;
                case "KB":
                    props.onChange(newState.speed * 1024);
                    break;
                case "B":
                    props.onChange(newState.speed);
                    break;
            }
        } else {
            props.onChange(undefined);
        }
        setState(newState);
    }

    return <div style={{display: "flex", alignItems: "center", marginRight: "8px"}}>
        <Checkbox checked={state.enabled} onChange={(e) => {
            updateState({
                ...state,
                enabled: e.target.checked
            })
        }
        }/>
        <TextField variant="outlined"
                   disabled={!state.enabled}
                   defaultValue={state.speed}
                   inputProps={{min: 1, style: {textAlign: "right"}}}
                   type={"number"}
                   label={props.title}
                   onBlur={(e) => updateState({
                       ...state,
                       speed: e.target.value as number,
                   })}/>
        <Select disabled={!state.enabled} style={{minWidth: "10em", margin: "4px"}} value={state.unit}
                onChange={(e) => updateState({
                    ...state,
                    unit: e.target.value
                })}>
            <MenuItem value={"B"}>B/s</MenuItem>
            <MenuItem value={"KB"}>KB/s</MenuItem>
            <MenuItem value={"MB"}>MB/s</MenuItem>
        </Select>
    </div>;
}