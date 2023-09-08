import {Alert, Checkbox, FormControlLabel, Grid, Paper} from "@mui/material";
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
        enabled: !!props.manifest.authenticationRequired,
        manifest: props.manifest
    } as UIAuthenticationState);

    function updateState(newState: UIAuthenticationState) {
        setState(newState);

        if (newState.enabled) {
            const newManifest = {
                ...newState.manifest
            };
            newManifest.authenticationRequired = true;

            props.onChange(newManifest);
        } else {
            const newManifest = {
                ...newState.manifest
            };
            delete newManifest.authenticationRequired;

            props.onChange(newManifest);
        }
    }

    return <Paper sx={{p: 2}}>
        <Grid container spacing={2}>
            <Grid item xs={12}>
                <DividerWithText>Interface authentication</DividerWithText>
            </Grid>
            <Grid item xs={12}>
                <Alert severity="info">Require knowledge of the private key password to access the
                    administration interface.<br/><br/><b>This is recommended when running as a service or root user</b>.</Alert>
                <FormControlLabel control={<Checkbox
                    checked={state.enabled}
                    id={"uiAuthentication"}
                    onChange={(e) => updateState({
                        ...state,
                        enabled: e.target.checked
                    })}
                />} label="Require authentication for user interface"/>
            </Grid>
        </Grid>
    </Paper>;
}