import * as React from "react";
import Typography from "@mui/material/Typography";
import {MenuItem, Select, TextField} from "@mui/material";
import {BackupTimespan} from "../api";

export interface DurationProps {
    timespan?: BackupTimespan,
    onChange: (timespan?: BackupTimespan) => void,
    requireTime?: boolean,
    title: string,
    disabled?: boolean
}

interface DurationState {
    duration: number,
    unit: string,
    type: string
}

export function calculateUnit(props: DurationProps) {
    if (props.timespan) {
        if (props.timespan.unit) {
            switch (props.timespan.unit) {
                case "IMMEDIATE":
                case "FOREVER":
                    return "MONTHS";
                default:
                    return props.timespan.unit;
            }
        }
    }
    return "MONTHS";
}

export function calculateType(props: DurationProps) {
    if (props.requireTime) {
        return "AFTER";
    }
    if (props.timespan && props.timespan.unit) {
        switch (props.timespan.unit) {
            case "IMMEDIATE":
            case "FOREVER":
                return props.timespan.unit;
            default:
                return "AFTER";
        }
    }
    return "IMMEDIATE";
}

export default function Timespan(props: DurationProps) {

    const [state, setState] = React.useState({
        duration: props.timespan && props.timespan.duration ? props.timespan.duration : 1,
        unit: calculateUnit(props),
        type: calculateType(props)
    } as DurationState);

    function updateState(newState: DurationState) {
        if (props.onChange) {
            if (newState.type === "IMMEDIATE") {
                props.onChange({
                    duration: 0
                } as BackupTimespan);
            } else {
                if (newState.type === "AFTER") {
                    props.onChange({
                        duration: newState.duration,
                        unit: newState.unit
                    } as BackupTimespan);
                } else {
                    props.onChange({
                        duration: newState.duration,
                        unit: newState.type
                    } as BackupTimespan);
                }
            }
        }
        setState(newState);
    }

    const pluralS = state.duration == 1 ? "" : "s";

    // noinspection TypeScriptValidateTypes
    return <div style={{display: "flex", alignItems: "center"}}>
        <Typography style={{margin: "4px"}}>{props.title}</Typography>
        {
            !props.requireTime &&
            <Select style={{minWidth: "10em", margin: "4px"}} disabled={props.disabled} value={state.type}
                    onChange={(e) => updateState({
                        duration: state.duration,
                        unit: state.unit,
                        type: e.target.value
                    })}>
                <MenuItem value={"IMMEDIATE"}>all changes to a file</MenuItem>
                <MenuItem value={"FOREVER"}>only one version of a file</MenuItem>
                <MenuItem value={"AFTER"}>one version of a file every</MenuItem>
            </Select>
        }
        {state.type === "AFTER" &&
            <>
                <TextField disabled={props.disabled}
                           variant="standard"
                           defaultValue={state.duration}
                           inputProps={{min: 1, style: {textAlign: "right"}}}
                           style={{width: "80px", margin: "4px"}}
                           type={"number"}
                           onBlur={(e) => updateState({
                               duration: parseInt(e.target.value),
                               unit: state.unit,
                               type: state.type
                           })}/>
                <Select style={{minWidth: "10em", margin: "4px"}} disabled={props.disabled} value={state.unit}
                        onChange={(e) => updateState({
                            duration: state.duration,
                            unit: e.target.value,
                            type: state.type
                        })}>
                    <MenuItem value={"SECONDS"}>second{pluralS}</MenuItem>
                    <MenuItem value={"MINUTES"}>minute{pluralS}</MenuItem>
                    <MenuItem value={"HOURS"}>hour{pluralS}</MenuItem>
                    <MenuItem value={"DAYS"}>day{pluralS}</MenuItem>
                    <MenuItem value={"WEEKS"}>week{pluralS}</MenuItem>
                    <MenuItem value={"MONTHS"}>month{pluralS}</MenuItem>
                    <MenuItem value={"YEARS"}>year{pluralS}</MenuItem>
                </Select>
            </>
        }
    </div>;
}