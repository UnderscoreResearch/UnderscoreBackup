import Paper from "@mui/material/Paper";
import {BackupConfiguration} from "../api";
import {TextField} from "@mui/material";
import {DisplayMessage} from '../App';
import DividerWithText from "../3rdparty/react-js-cron-mui/components/DividerWithText";
import * as React from "react";

export default function ConfigEditor(props: {
    config: BackupConfiguration,
    onChange: (newConfig: BackupConfiguration) => void
}) {
    return <Paper sx={{
        p: 2,
    }}>
        <DividerWithText>Configuration file</DividerWithText>
        <TextField
            id="outlined-multiline-flexible"
            inputProps={{style: {fontFamily: 'monospace'}}}
            fullWidth={true}
            multiline
            defaultValue={JSON.stringify(props.config, null, 2)}
            onBlur={(e) => {
                try {
                    const newConfig = JSON.parse(e.target.value);
                    props.onChange(newConfig);
                } catch (e: any) {
                    DisplayMessage(e.toString());
                }
            }}
        />
    </Paper>
}