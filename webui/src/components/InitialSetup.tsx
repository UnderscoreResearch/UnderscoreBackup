import * as React from "react";
import Destination from "./Destination";
import {BackupConfiguration, BackupDestination, BackupManifest} from "../api";
import Paper from "@mui/material/Paper";
import {Alert, Stack, TextField} from "@mui/material";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import UIAuthentication from "./UIAuthentication";

const temporaryStorage = window.sessionStorage;

export interface InitialSetupProps {
    originalConfig: BackupConfiguration,
    currentConfig: BackupConfiguration,
    rebuildAvailable: boolean,
    configUpdated: (valid: boolean, configuration: BackupConfiguration, passphrase?: string) => void
}

interface InitialSetupState {
    manifest: BackupManifest,
    passphrase: string,
    passphraseConfirm: string
}

export default function InitialSetup(props: InitialSetupProps) {
    const [state, setState] = React.useState({
        passphrase: "",
        passphraseConfirm: "",
        manifest: props.currentConfig && props.currentConfig.manifest ? props.currentConfig.manifest : {destination: "do"}
    } as InitialSetupState)

    function configurationUpdate(valid: boolean, val?: BackupDestination) {
        if (valid) {
            var manifest = {
                destination: "d0",
                optimizeSchedule: "0 0 1 * *"
            };
            if (props.currentConfig && props.currentConfig.manifest) {
                manifest = {
                    ...props.currentConfig.manifest,
                    ...manifest
                }
            }
            const config = {
                ...props.currentConfig,
                ...{
                    destinations: {
                        "d0": val as BackupDestination
                    },
                    manifest: manifest
                }
            }

            props.configUpdated(true, config);
        } else {
            props.configUpdated(false, props.currentConfig)
        }
    }

    let destinationId: string | null = temporaryStorage.getItem("destinationId");
    if (destinationId) {
        const pendingDestination = JSON.parse(temporaryStorage.getItem("destination") as string);
        props.currentConfig.destinations[destinationId] = pendingDestination;
    }

    function updateState(newState: InitialSetupState) {
        setState(newState);
        if (newState.passphrase === newState.passphraseConfirm && newState.passphrase) {
            props.configUpdated(true, {
                ...props.currentConfig,
                manifest: state.manifest
            }, newState.passphrase);
        } else {
            props.configUpdated(false, {
                ...props.currentConfig,
                manifest: state.manifest
            }, newState.passphrase);
        }
    }

    if (props.originalConfig.destinations && Object.keys(props.originalConfig.destinations).length == 0) {
        return <Stack spacing={2} style={{width: "100%"}}>
            <Paper sx={{
                p: 2,
            }}>
                <Typography variant="h3" component="div">
                    Specify backup destination for metadata
                </Typography>
                <Typography variant="body1" component="div">
                    <p>
                        The first thing you need to specify is where the metadata for your backup should be placed.
                    </p>
                </Typography>
                <Typography variant="body1" component="div">
                    <p>
                        If you are planning to restore from an existing backup you need to specify the
                        same metadata location as your backup was pointing to before.
                    </p>
                </Typography>
                <Destination manifestDestination={true} destinationUpdated={configurationUpdate}
                             id={"d0"}
                             destination={
                                 props.currentConfig &&
                                 props.currentConfig.manifest &&
                                 props.currentConfig.manifest.destination &&
                                 props.currentConfig.destinations
                                     ? props.currentConfig.destinations[props.currentConfig.manifest.destination]
                                     : {type: "DROPBOX", endpointUri: ""}
                             }/>
            </Paper>
        </Stack>;
    } else if (props.rebuildAvailable) {
        return <Stack spacing={2} style={{width: "100%"}}>
            <Paper sx={{
                p: 2
            }}>
                <Typography variant="h3" component="div">
                    Restoring from backup
                </Typography>
                <Typography variant="body1" component="div">
                    <p>
                        The destination you have chosen already contains a backup. To start the restore
                        you need to provide the original passphrase used to create the backup.
                    </p>
                </Typography>
                <Alert severity="info">
                    The metadata for the backup will need to be downloaded that might take
                    a few minutes before you can start actually restoring data.
                </Alert>
                <Box component="div"
                     sx={{
                         '& .MuiTextField-root': {m: 1},
                     }}
                     style={{marginTop: 4}}>
                    <TextField label="Passphrase" variant="outlined"
                               fullWidth={true}
                               required={true}
                               value={state.passphrase}
                               error={!state.passphrase}
                               type="password"
                               onChange={(e) => updateState({
                                   ...state,
                                   passphrase: e.target.value,
                                   passphraseConfirm: e.target.value
                               })}/>
                </Box>
            </Paper>
        </Stack>
    } else {
        return <Stack spacing={2} style={{width: "100%"}}>
            <Paper sx={{p: 2}}>
                <Typography variant="h3" component="div">
                    Enter backup passphrase
                </Typography>
                <Typography variant="body1" component="div">
                    <p>
                        Enter the passphrase with which your backup will be protected.
                    </p>
                </Typography>
                <Alert severity="warning">Please keep your passphrase safe.
                    There is no way to recover a lost passphrase!</Alert>

                <Box
                    component="div"
                    sx={{
                        '& .MuiTextField-root': {m: 1},
                    }}
                    style={{marginTop: 4}}
                >
                    <TextField label="Passphrase" variant="outlined"
                               fullWidth={true}
                               required={true}
                               value={state.passphrase}
                               error={!state.passphrase}
                               type="password"
                               onChange={(e) => updateState({
                                   ...state,
                                   passphrase: e.target.value
                               })}/>
                    <TextField label="Confirm Passphrase" variant="outlined"
                               fullWidth={true}
                               required={true}
                               helperText={state.passphraseConfirm !== state.passphrase ? "Does not match" : null}
                               value={state.passphraseConfirm}
                               error={state.passphraseConfirm !== state.passphrase || !state.passphrase}
                               type="password"
                               onChange={(e) => updateState({
                                   ...state,
                                   passphraseConfirm: e.target.value
                               })}/>
                </Box>
            </Paper>
            <UIAuthentication manifest={state.manifest} onChange={(manifest => updateState({
                ...state,
                manifest: manifest
            }))}/>
        </Stack>
    }
}