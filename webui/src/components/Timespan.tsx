import * as React from "react";
import Typography from "@mui/material/Typography";
import {MenuItem, Select, TextField} from "@mui/material";
import {BackupTimespan} from "../api";

export interface DurationProps {
    timespan?: BackupTimespan,
    onChange: (timespan?: BackupTimespan) => void,
    requireTime?: boolean,
    title: string
}

interface DurationState {
    duration: number,
    unit: string
};

function calculateUnit(props: DurationProps) {
    if (props.timespan) {
        if (props.timespan.unit) {
            return props.timespan.unit;
        }
        if (props.timespan.duration == 0) {
            return "IMMEDIATE";
        }
    }
    return props.requireTime ? "MONTH" : "IMMEDIATE";
}

export default function Timespan(props: DurationProps) {

    const [state, setState] = React.useState({
        duration: props.timespan && props.timespan.duration ? props.timespan.duration : 1,
        unit: calculateUnit(props)
    } as DurationState);

    function updateState(newState: DurationState) {
        if (props.onChange) {
            if (newState.unit === "IMMEDIATE") {
                props.onChange({
                    duration: 0
                } as BackupTimespan);
            } else {
                props.onChange({
                    duration: newState.duration,
                    unit: newState.unit
                } as BackupTimespan);
            }
        }
        setState(newState);
    }

    const pluralS = state.duration == 1 ? "" : "s";

    return <div style={{display: "flex", alignItems: "center", marginRight: "8px"}}>
        <Typography>{props.title}</Typography>
        {state.unit !== "FOREVER" && state.unit !== "IMMEDIATE" &&
            <TextField variant="standard"
                       defaultValue={state.duration}
                       inputProps={{min: 1, style: {textAlign: "right"}}}
                       style={{width: "80px"}}
                       type={"number"}
                       onBlur={(e) => updateState({
                           duration: parseInt(e.target.value),
                           unit: state.unit
                       })}/>
        }
        <Select style={{minWidth: "10em", margin: "4px"}} value={state.unit} onChange={(e) => updateState({
            duration: state.duration,
            unit: e.target.value
        })}>
            {!props.requireTime && <MenuItem value={"IMMEDIATE"}>immediate</MenuItem>}
            <MenuItem value={"SECONDS"}>second{pluralS}</MenuItem>
            <MenuItem value={"MINUTES"}>minute{pluralS}</MenuItem>
            <MenuItem value={"HOURS"}>hour{pluralS}</MenuItem>
            <MenuItem value={"DAYS"}>day{pluralS}</MenuItem>
            <MenuItem value={"WEEKS"}>week{pluralS}</MenuItem>
            <MenuItem value={"MONTHS"}>month{pluralS}</MenuItem>
            <MenuItem value={"YEARS"}>year{pluralS}</MenuItem>
            {!props.requireTime && <MenuItem value={"FOREVER"}>forever</MenuItem>}
        </Select>
    </div>;
}