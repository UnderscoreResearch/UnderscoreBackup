import * as React from "react";
import Typography from "@mui/material/Typography";
import {MenuItem, Select, TextField} from "@mui/material";
import {BackupTimespan} from "../api";
import {calculateType, calculateUnit} from "./Timespan";

export interface DeletionDurationProps {
    timespan?: BackupTimespan,
    onChange: (timespan?: BackupTimespan) => void,
    disabled?: boolean,
    title: string
}

interface DeletionDurationState {
    duration: number,
    unit: string,
    type: string
}

export default function DeletionTimespan(props: DeletionDurationProps) {

    const [state, setState] = React.useState({
        duration: props.timespan && props.timespan.duration ? props.timespan.duration : 1,
        unit: calculateUnit(props),
        type: calculateType(props)
    } as DeletionDurationState);

    function updateState(newState: DeletionDurationState) {
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
        {
            <Select style={{minWidth: "10em", margin: "4px"}} disabled={props.disabled} value={state.type}
                    onChange={(e) => updateState({
                        duration: state.duration,
                        unit: state.unit,
                        type: e.target.value
                    })}>
                <MenuItem value={"IMMEDIATE"}>Immediately</MenuItem>
                <MenuItem value={"FOREVER"}>Never</MenuItem>
                <MenuItem value={"AFTER"}>After</MenuItem>
            </Select>
        }
        {state.type === "AFTER" &&
            <>
                <TextField disabled={props.disabled}
                           variant="standard"
                           defaultValue={state.duration}
                           inputProps={{min: 1, style: {textAlign: "right"}}}
                           style={{width: "80px"}}
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
        <Typography style={{margin: "4px"}}>
            {props.title}
        </Typography>
    </div>;
}