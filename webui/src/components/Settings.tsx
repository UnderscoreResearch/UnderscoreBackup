import * as React from "react";
import {BackupConfiguration, BackupGlobalLimits, BackupManifest, PostChangeEncryptionKey, PropertyMap} from "../api";
import {
    Alert,
    Button,
    Checkbox,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    FormControlLabel,
    Grid,
    Paper,
    Stack,
    TextField
} from "@mui/material";
import DividerWithText from "../3rdparty/react-js-cron-mui/components/DividerWithText";
import SpeedLimit from "./SpeedLimit";
import PropertyMapEditor from "./PropertyMapEditor";
import UIAuthentication from "./UIAuthentication";
import Cron from "../3rdparty/react-js-cron-mui";
import {DisplayMessage} from "../App";
import Box from "@mui/material/Box";

export interface SettingsProps {
    config: BackupConfiguration,
    onChange: (newConfig: BackupConfiguration) => void
}

interface SettingsState {
    manifest: BackupManifest,
    showConfig: boolean,
    passphrase: string,
    passphraseConfirm: string,
    oldPassphrase: string,
    showChangePassword: boolean,
    configData: string,
    limits: BackupGlobalLimits,
    properties?: PropertyMap
}

function createInitialState(config: BackupConfiguration): SettingsState {
    return {
        manifest: config.manifest,
        showConfig: false,
        showChangePassword: false,
        passphrase: "",
        passphraseConfirm: "",
        oldPassphrase: "",
        properties: config.properties,
        configData: JSON.stringify(config, null, 2),
        limits: config.limits ? config.limits : {}
    }
}

export default function Settings(props: SettingsProps) {
    const [state, setState] = React.useState(createInitialState(props.config));

    function updateState(newState: SettingsState) {
        setState(newState);

        const sendState = {
            ...props.config,
            properties: newState.properties,
            limits: newState.limits,
            manifest: newState.manifest
        } as BackupConfiguration;

        if (sendState.properties && Object.keys(sendState.properties).length == 0)
            sendState.properties = undefined;
        if (sendState.limits && Object.keys(sendState.limits).length == 0)
            sendState.limits = undefined;
        props.onChange(sendState);
    }

    function handleShowConfig() {
        setState({
            ...state,
            showConfig: true
        });
    }

    function handleConfigClose() {
        setState({
            ...state,
            showConfig: false,
            configData: JSON.stringify(props.config, null, 2)
        });
    }

    function handleConfigSubmit() {
        try {
            const newConfig = JSON.parse(state.configData);
            setState(createInitialState(newConfig));
            props.onChange(newConfig);
        } catch (e: any) {
            DisplayMessage(e.toString());
        }
    }

    function handleShowChangePassword() {
        setState({
            ...state,
            oldPassphrase: "",
            passphrase: "",
            passphraseConfirm: "",
            showChangePassword: true
        });
    }

    function handleChangePasswordClose() {
        setState({
            ...state,
            showChangePassword: false
        });
    }

    async function handleChangePassword() {
        try {
            if (!state.oldPassphrase) {
                DisplayMessage("Missing old passphrase");
            } else if (!state.passphrase) {
                DisplayMessage("Missing new passphrase");
            } else if (state.passphrase !== state.passphraseConfirm) {
                DisplayMessage("Passphrase does not match");
            } else if (await PostChangeEncryptionKey(state.oldPassphrase, state.passphrase)) {
                setState((oldState) => {
                    return {
                        ...oldState,
                        showChangePassword: false
                    }
                });
            }
        } catch (e: any) {
            DisplayMessage(e.toString());
        }
    }

    return <Stack spacing={2}>
        <UIAuthentication manifest={state.manifest} onChange={(manifest) => updateState({
            ...state,
            manifest: manifest
        })}/>
        <Paper sx={{p: 2}}>
            <Grid container spacing={2}>
                <Grid item xs={12}>
                    <DividerWithText>Global limits</DividerWithText>
                </Grid>
                <Grid item xs={6}>
                    <SpeedLimit
                        speed={state.limits.maximumUploadBytesPerSecond}
                        onChange={(newSpeed) => {
                            const newState = {
                                ...state,
                                limits: {
                                    ...state.limits,
                                    maximumUploadBytesPerSecond: newSpeed
                                }
                            }
                            updateState(newState);
                        }} title={"Maximum total upload speed"}/>
                </Grid>
                <Grid item xs={6}>
                    <TextField label="Maximum concurrent uploads" variant="outlined"
                               value={state.limits.maximumUploadThreads ? state.limits.maximumUploadThreads : 4}
                               type={"number"}
                               inputProps={{min: 1, style: {textAlign: "right"}}}
                               onChange={(e) => updateState({
                                   ...state,
                                   limits: {
                                       ...state.limits,
                                       maximumUploadThreads: parseInt(e.target.value)
                                   }
                               })}/>
                </Grid>
                <Grid item xs={6}>
                    <SpeedLimit
                        speed={state.limits.maximumDownloadBytesPerSecond}
                        onChange={(newSpeed) => {
                            const newState = {
                                ...state,
                                limits: {
                                    ...state.limits,
                                    maximumDownloadBytesPerSecond: newSpeed
                                }
                            }
                            updateState(newState);
                        }} title={"Maximum total download speed"}/>
                </Grid>
                <Grid item xs={6}>
                    <TextField label="Maximum concurrent downloads" variant="outlined"
                               value={state.limits.maximumDownloadThreads ? state.limits.maximumDownloadThreads : 4}
                               type={"number"}
                               inputProps={{min: 1, style: {textAlign: "right"}}}
                               onChange={(e) => updateState({
                                   ...state,
                                   limits: {
                                       ...state.limits,
                                       maximumDownloadThreads: parseInt(e.target.value)
                                   }
                               })}/>
                </Grid>
            </Grid>
        </Paper>
        <Paper sx={{p: 2}}>
            <DividerWithText>Advanced settings</DividerWithText>
            <Grid container spacing={2}>
                <Grid item xs={12}>
                    <FormControlLabel control={<Checkbox
                        checked={state.manifest.optimizeSchedule !== undefined}
                        onChange={(e) => updateState({
                            ...state,
                            manifest: {
                                ...state.manifest,
                                optimizeSchedule: e.target.checked ? "0 0 1 * *" : undefined
                            }
                        })}
                    />} label="Automatically optimize log"/>
                </Grid>
                <Grid item xs={12}>
                    <Cron disabled={state.manifest.optimizeSchedule === undefined}
                          value={state.manifest.optimizeSchedule ? state.manifest.optimizeSchedule : "0 0 1 * *"}
                          setValue={(newSchedule: string) => {
                              if (state.manifest.optimizeSchedule !== undefined &&
                                  state.manifest.optimizeSchedule !== newSchedule) {
                                  updateState({
                                      ...state,
                                      manifest: {
                                          ...state.manifest,
                                          optimizeSchedule: newSchedule
                                      }
                                  })
                              }
                          }} clockFormat='12-hour-clock'
                          clearButton={false}/>
                </Grid>
            </Grid>
        </Paper>
        <Paper sx={{p: 2}}>
            <DividerWithText>Custom properties</DividerWithText>
            <PropertyMapEditor properties={state.properties} onChange={(newProperties) => {
                const newState = {
                    ...state,
                    properties: newProperties
                }
                updateState(newState);
            }
            }/>
        </Paper>

        <Grid container spacing={2}>
            <Grid item xs={3}>
                <Button variant="contained" id="showConfiguration" onClick={handleShowConfig}>
                    Edit Configuration
                </Button>
            </Grid>
            <Grid item xs={3}>
                <Button variant="contained" id="showChangePassword" onClick={handleShowChangePassword}>
                    Change Password
                </Button>
            </Grid>
        </Grid>

        <Dialog open={state.showConfig} onClose={handleConfigClose} fullWidth={true} maxWidth={"xl"}>
            <DialogTitle>Configuration</DialogTitle>
            <DialogContent>
                <DialogContentText>
                    This is the complete configuration file for Underscore Backup.
                </DialogContentText>
                <TextField
                    id="configurationTextField"
                    inputProps={{style: {fontFamily: 'monospace'}}}
                    fullWidth={true}
                    multiline
                    defaultValue={state.configData}
                    onBlur={(e) => {
                        try {
                            setState({
                                ...state,
                                configData: e.target.value
                            });
                        } catch (e: any) {
                            DisplayMessage(e.toString());
                        }
                    }}
                />
            </DialogContent>
            <DialogActions>
                <Button onClick={handleConfigClose}>Cancel</Button>
                <Button onClick={handleConfigSubmit} id={"submitConfigChange"}>OK</Button>
            </DialogActions>
        </Dialog>

        <Dialog open={state.showChangePassword} onClose={handleChangePasswordClose}>
            <DialogTitle>Change Passphrase</DialogTitle>
            <DialogContent>
                <DialogContentText>
                    Change the passphrase used to protect your backup.
                </DialogContentText>
                <Alert severity="warning">Please keep your passphrase safe.
                    There is no way to recover a lost passphrase!</Alert>

                <Box
                    component="div"
                    sx={{
                        '& .MuiTextField-root': {m: 1},
                    }}
                    style={{marginTop: 4}}
                >
                    <TextField label="Existing Passphrase" variant="outlined"
                               fullWidth={true}
                               required={true}
                               value={state.oldPassphrase}
                               error={!state.oldPassphrase}
                               id={"oldPassphrase"}
                               type="password"
                               onChange={(e) => setState({
                                   ...state,
                                   oldPassphrase: e.target.value
                               })}/>
                    <TextField label="New Passphrase" variant="outlined"
                               fullWidth={true}
                               required={true}
                               value={state.passphrase}
                               error={!state.passphrase}
                               id={"passphraseFirst"}
                               type="password"
                               onChange={(e) => setState({
                                   ...state,
                                   passphrase: e.target.value
                               })}/>
                    <TextField label="Confirm New Passphrase" variant="outlined"
                               fullWidth={true}
                               required={true}
                               helperText={state.passphraseConfirm !== state.passphrase ? "Does not match" : null}
                               value={state.passphraseConfirm}
                               error={state.passphraseConfirm !== state.passphrase || !state.passphrase}
                               id={"passphraseSecond"}
                               type="password"
                               onChange={(e) => setState({
                                   ...state,
                                   passphraseConfirm: e.target.value
                               })}/>
                </Box>
            </DialogContent>
            <DialogActions>
                <Button onClick={handleChangePasswordClose}>Cancel</Button>
                <Button onClick={() => handleChangePassword()} id={"submitPasswordChange"}>OK</Button>
            </DialogActions>
        </Dialog>
    </Stack>
}