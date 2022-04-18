import * as React from "react";
import Typography from "@mui/material/Typography";
import {MenuItem, Select, TextField} from "@mui/material";
import {BackupTimespan} from "../api";

export interface DurationProps {
    timespan?: BackupTimespan,
    onChange: (timespan?: BackupTimespan) => void,
    noFoever? : boolean
    title: title
}

interface DurationState {
    duration: number,
    unit: string
};

export default function Timespan(props: DurationProps) {
    const [state, setState] = React.useState({
        duration: props.timespan && props.timespan.duration ? props.timespan.duration : 1,
        unit: props.timespan && props.timespan.duration ? props.timespan.unit : "FOREVER"
    } as DurationState);

    function updateState(newState: DurationState) {
        if (props.onChange) {
            if (newState.unit === "FOREVER") {
                props.onChange(undefined);
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
        {state.unit === "FOREVER" ?
            <Typography>{props.frequency ? "1 in " : ""}</Typography>
            :
            <TextField variant="standard"
                       defaultValue={state.duration}
                       inputProps={{min: 1, style: {textAlign: "right"}}}
                       style={{ width: "80px" }}
                       type={"number"}
                       onBlur={(e) => updateState({
                           duration: e.target.value as number,
                           unit: state.unit
                       })}/>
        }
        <Select style={{minWidth: "10em", margin: "4px"}} value={state.unit} onChange={(e) => updateState({
            duration: state.duration,
            unit: e.target.value
        })}>
            <MenuItem value={"SECONDS"}>second{pluralS}</MenuItem>
            <MenuItem value={"MINUTES"}>minute{pluralS}</MenuItem>
            <MenuItem value={"HOURS"}>hour{pluralS}</MenuItem>
            <MenuItem value={"DAYS"}>day{pluralS}</MenuItem>
            <MenuItem value={"WEEKS"}>week{pluralS}</MenuItem>
            <MenuItem value={"MONTHS"}>month{pluralS}</MenuItem>
            <MenuItem value={"YEARS"}>year{pluralS}</MenuItem>
            {props.noFoever ?
                "" :
                <MenuItem value={"FOREVER"}>forever</MenuItem>
            }
        </Select>
    </div>;
}