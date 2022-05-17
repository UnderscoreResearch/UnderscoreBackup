import {Checkbox, FormControlLabel, Grid, Paper, TextField} from "@mui/material";
import DividerWithText from "../3rdparty/react-js-cron-mui/components/DividerWithText";
import * as React from "react";
import {BackupManifest} from "../api";

export interface UIAuthenticationProps {
    manifest: BackupManifest,
    onChange: (manifest: BackupManifest) => void
}

interface UIAuthenticationState {
    manifest: BackupManifest,
    enabled: boolean
}

export default function UIAuthentication(props: UIAuthenticationProps) {
    const [state, setState] = React.useState({
        enabled: !!(props.manifest.configUser && props.manifest.configPassword),
        manifest: props.manifest
    } as UIAuthenticationState);

    function updateState(newState: UIAuthenticationState) {
        setState(newState);

        if (newState.enabled) {
            props.onChange(newState.manifest);
        } else {
            const newManifest = {
                ...newState.manifest
            };
            delete newManifest.configUser;
            delete newManifest.configPassword;

            props.onChange(newManifest);
        }
    }

    return <Paper sx={{p: 2}}>
        <Grid container spacing={2}>
            <Grid item xs={12}>
                <DividerWithText>Interface Authentication</DividerWithText>
            </Grid>
            <Grid item xs={12}>
                <FormControlLabel control={<Checkbox
                    checked={state.enabled}
                    onChange={(e) => updateState({
                        ...state,
                        enabled: e.target.checked
                    })}
                />} label="Require authentication for user interface"/>
            </Grid>
            <Grid item xs={6}>
                <TextField label="Username" variant="outlined"
                           fullWidth={true}
                           value={state.manifest.configUser ? state.manifest.configUser : ""}
                           disabled={!state.enabled}
                           onChange={(e) => updateState({
                               ...state,
                               manifest: {
                                   ...state.manifest,
                                   configUser: e.target.value ? e.target.value : undefined
                               }
                           })}/>
            </Grid>
            <Grid item xs={6}>
                <TextField label="Password" variant="outlined"
                           fullWidth={true}
                           type={"password"}
                           disabled={!state.enabled}
                           value={state.manifest.configPassword ? state.manifest.configPassword : ""}
                           onChange={(e) => updateState({
                               ...state,
                               manifest: {
                                   ...state.manifest,
                                   configPassword: e.target.value ? e.target.value : undefined
                               }
                           })}/>
            </Grid>
        </Grid>
    </Paper>;
}