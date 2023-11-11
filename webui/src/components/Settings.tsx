import * as React from "react";
import {
    BackupConfiguration,
    BackupGlobalLimits,
    BackupManifest,
    BackupRetention,
    PropertyMap,
    resetSettings
} from "../api";
import {
    Button,
    Checkbox,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    FormControlLabel,
    Grid,
    Link,
    Paper,
    Stack,
    TextField
} from "@mui/material";
import DividerWithText from "../3rdparty/react-js-cron-mui/components/DividerWithText";
import SpeedLimit from "./SpeedLimit";
import PropertyMapEditor from "./PropertyMapEditor";
import UIAuthentication from "./UIAuthentication";
import ServiceAuthentication from "./ServiceAuthentication";
import Cron from "../3rdparty/react-js-cron-mui";
import {DisplayMessage} from "../App";
import Box from "@mui/material/Box";
import Retention from "./Retention";
import Typography from "@mui/material/Typography";
import Timespan from "./Timespan";
import {useApplication} from "../utils/ApplicationContext";
import {Warning} from "@mui/icons-material";
import ChangePasswordDialog from "./ChangePasswordDialog";

interface SettingsState {
    manifest: BackupManifest,
    showConfig: boolean,
    showChangePassword: boolean,
    showResetWarning: boolean,
    configData: string,
    limits: BackupGlobalLimits,
    missingRetention?: BackupRetention,
    properties?: PropertyMap,
    hasRandomizedSchedule: boolean
}

function createInitialState(config: BackupConfiguration): SettingsState {
    return {
        manifest: config.manifest,
        showConfig: false,
        showChangePassword: false,
        showResetWarning: false,
        hasRandomizedSchedule: !!config.manifest.scheduleRandomize,
        properties: config.properties,
        missingRetention: config.missingRetention,
        configData: JSON.stringify(config, null, 2),
        limits: config.limits ? config.limits : {}
    }
}

export default function Settings() {
    const appContext = useApplication();
    const [state, setState] = React.useState(createInitialState(appContext.currentConfiguration));

    function updateState(newState: SettingsState) {

        const newManifest = {
            ...newState.manifest,
            scheduleRandomize: newState.hasRandomizedSchedule ?
                (newState.manifest.scheduleRandomize ?
                    newState.manifest.scheduleRandomize :
                    {duration: 1, unit: "HOURS"}) :
                undefined
        }

        const sendState = {
            ...appContext.currentConfiguration,
            properties: newState.properties,
            missingRetention: newState.missingRetention,
            limits: newState.limits,
            manifest: newManifest
        } as BackupConfiguration;


        setState({
            ...newState,
            configData: JSON.stringify(sendState, undefined, 2)
        });

        if (sendState.properties && Object.keys(sendState.properties).length == 0)
            sendState.properties = undefined;
        if (sendState.limits && Object.keys(sendState.limits).length == 0)
            sendState.limits = undefined;
        appContext.setState((oldState) => ({
            ...oldState,
            currentConfiguration: sendState
        }));
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
            configData: JSON.stringify(appContext.currentConfiguration, null, 2)
        });
    }

    function handleConfigSubmit() {
        try {
            const newConfig = JSON.parse(state.configData) as BackupConfiguration;
            setState(createInitialState(newConfig));
            appContext.setState((oldState) => ({
                ...oldState,
                currentConfiguration: newConfig
            }));
        } catch (e: any) {
            DisplayMessage(e.toString());
        }
    }

    function handleShowChangePassword() {
        setState({
            ...state,
            showChangePassword: true
        });
    }


    function handlesResetWarning() {
        setState({
            ...state,
            showResetWarning: true
        });
    }

    function handleResetWarningClose() {
        setState({
            ...state,
            showResetWarning: false
        });
    }

    async function performResetWarningClose() {
        await resetSettings();

        location.href = location.href.substring(0, location.href.lastIndexOf("/")) + "/";
    }

    return <Stack spacing={2}>
        <Paper sx={{p: 2}}>
            <Grid container spacing={2} alignItems={"center"}>
                <Grid item xs={12}>
                    <DividerWithText>Underscore Backup Service Account</DividerWithText>
                </Grid>
                <ServiceAuthentication includeSkip={false}
                                       needSubscription={false}
                                       backendState={appContext.backendState}
                                       updatedToken={() => appContext.updateBackendState()}/>
            </Grid>
        </Paper>
        <UIAuthentication manifest={state.manifest} onChange={(manifest) => updateState({
            ...state,
            manifest: manifest
        })}/>
        <Paper sx={{p: 2}}>
            <Grid container spacing={2}>
                <Grid item xs={12}>
                    <DividerWithText>Global limits</DividerWithText>
                </Grid>
                <Grid item xs={9}>
                    <Grid container spacing={2}>
                        <Grid item xs={8}>
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
                        <Grid item xs={4}>
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
                        <Grid item xs={8}>
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
                        <Grid item xs={4}>
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
                </Grid>
                <Grid item xs={3}>
                    {((state.limits.maximumUploadThreads ?? 0) > 4 || (state.limits.maximumDownloadThreads ?? 0) > 4) &&
                        <>
                            <Typography>
                                <Warning sx={{color: "warning.main"}}/>
                                A value higher than 4 may cause the service to run out of memory with
                                default settings.
                            </Typography>
                            <Link rel="noreferrer" target="_blank"
                                  href={`https://underscorebackup.com/blog/2023/02/how-to-change-memory-configuration.html`}
                                  underline={"hover"}>See how to increase memory in this article.</Link>
                        </>
                    }
                </Grid>
            </Grid>
        </Paper>
        <Paper sx={{p: 2}}>
            <DividerWithText>Missing Retention</DividerWithText>
            <Typography variant={"body2"} style={{marginBottom: "16px"}}>The retention settings to use for any files
                that are not covered by a set</Typography>
            <Retention retention={state.missingRetention} retentionUpdated={(newState) => updateState({
                ...state,
                missingRetention: newState
            })}/>
        </Paper>
        <Paper sx={{p: 2}}>
            <DividerWithText>Advanced settings</DividerWithText>
            <Grid container spacing={2}>
                <Grid item xs={12}>
                    <FormControlLabel control={<Checkbox
                        checked={state.manifest.versionCheck || state.manifest.versionCheck === undefined}
                        onChange={(e) => updateState({
                            ...state,
                            manifest: {
                                ...state.manifest,
                                versionCheck: e.target.checked
                            }
                        })}
                    />} label="Check for new versions"/>
                </Grid>
            </Grid>
            <Grid container spacing={2}>
                <Grid item xs={12}>
                    <FormControlLabel style={{paddingLeft: "1em"}} control={<Checkbox
                        checked={state.manifest.automaticUpgrade || state.manifest.automaticUpgrade === undefined}
                        disabled={!state.manifest.versionCheck && state.manifest.versionCheck !== undefined}
                        onChange={(e) => updateState({
                            ...state,
                            manifest: {
                                ...state.manifest,
                                automaticUpgrade: e.target.checked
                            }
                        })}
                    />} label="Automatically install new versions if possible"/>
                </Grid>
            </Grid>
            <Grid container spacing={2}>
                <Grid item xs={12}>
                    <FormControlLabel control={<Checkbox
                        checked={state.manifest.reportStats || state.manifest.reportStats === undefined}
                        disabled={!appContext.backendState.serviceConnected}
                        onChange={(e) => updateState({
                            ...state,
                            manifest: {
                                ...state.manifest,
                                reportStats: e.target.checked
                            }
                        })}
                    />} label="Report backup statistics to service"/>
                </Grid>
            </Grid>
            <Grid container spacing={2}>
                <Grid item xs={12}>
                    <FormControlLabel control={<Checkbox
                        checked={state.manifest.hideNotifications}
                        onChange={(e) => updateState({
                            ...state,
                            manifest: {
                                ...state.manifest,
                                hideNotifications: e.target.checked
                            }
                        })}
                    />} label="Hide UI notifications"/>
                </Grid>
            </Grid>
            <Grid container spacing={2}>
                <Grid item xs={12}>
                    <FormControlLabel control={<Checkbox
                        checked={state.manifest.pauseOnBattery || state.manifest.pauseOnBattery === undefined}
                        onChange={(e) => updateState({
                            ...state,
                            manifest: {
                                ...state.manifest,
                                pauseOnBattery: e.target.checked
                            }
                        })}
                    />} label="Pause backup when running on battery power or when CPU load is high"/>
                </Grid>
            </Grid>
            <Grid container spacing={2}>
                <Grid item xs={12}>
                    <FormControlLabel control={<Checkbox
                        checked={!!state.manifest.ignorePermissions}
                        onChange={(e) => updateState({
                            ...state,
                            manifest: {
                                ...state.manifest,
                                ignorePermissions: e.target.checked
                            }
                        })}
                    />} label="Dont record file permissions"/>
                </Grid>
            </Grid>
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
                    />} label="Automatically optimize log and validate file blocks"/>
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
            <Grid container spacing={2}>
                <Grid item xs={12}>
                    <FormControlLabel control={<Checkbox
                        checked={state.manifest.trimSchedule !== undefined}
                        onChange={(e) => updateState({
                            ...state,
                            manifest: {
                                ...state.manifest,
                                trimSchedule: e.target.checked ? "0 0 * * *" : undefined
                            }
                        })}
                    />} label="At most scan for unused blocks while trimming"/>
                </Grid>
                <Grid item xs={12}>
                    <Cron disabled={state.manifest.trimSchedule === undefined}
                          value={state.manifest.trimSchedule ? state.manifest.trimSchedule : "0 0 * * *"}
                          setValue={(newSchedule: string) => {
                              if (state.manifest.trimSchedule !== undefined &&
                                  state.manifest.trimSchedule !== newSchedule) {
                                  updateState({
                                      ...state,
                                      manifest: {
                                          ...state.manifest,
                                          trimSchedule: newSchedule
                                      }
                                  })
                              }
                          }} clockFormat='12-hour-clock'
                          clearButton={false}/>
                </Grid>
            </Grid>
            <div style={{marginLeft: "-8px"}}>
                <div style={{display: "flex"}}>
                    <Checkbox
                        checked={state.hasRandomizedSchedule}
                        onChange={(e) => {
                            updateState({
                                ...state,
                                hasRandomizedSchedule: e.target.checked
                            })
                        }}
                    />
                    <Timespan disabled={!state.hasRandomizedSchedule} onChange={(newVal) => {
                        updateState({
                            ...state,
                            manifest: {
                                ...state.manifest,
                                scheduleRandomize: newVal
                            }
                        })
                    }} timespan={state.manifest.scheduleRandomize ? state.manifest.scheduleRandomize : {
                        unit: "HOURS",
                        duration: 1
                    }} title={"Randomize start of schedules by"} requireTime={true}/>
                </div>
            </div>
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

        <div style={{display: "flex", width: "100%"}}>
            <Box textAlign={"left"} width={"60%"}>
                <Button variant="contained" id="showConfiguration" onClick={handleShowConfig}
                        style={{marginRight: "16px", marginBottom: "16px"}}>
                    Edit Configuration
                </Button>
                <Button variant="contained" id="showChangePassword" onClick={handleShowChangePassword}
                        style={{marginRight: "16px", marginBottom: "16px"}}>
                    Change Password
                </Button>
            </Box>
            <Box textAlign={"right"} width={"40%"}>
                <Button variant="contained" id="deleteConfiguration" onClick={handlesResetWarning} color="error">
                    Delete Configuration
                </Button>
            </Box>
        </div>

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

        <ChangePasswordDialog open={state.showChangePassword}
                              onClose={() => setState({...state, showChangePassword: false})}/>

        <Dialog
            open={state.showResetWarning}
            onClose={handleResetWarningClose}
            aria-labelledby="alert-dialog-title"
            aria-describedby="alert-dialog-description"
        >
            <DialogTitle id="alert-dialog-title">
                {"Delete local configuration"}
            </DialogTitle>
            <DialogContent>
                <DialogContentText id="alert-dialog-description">
                    This action will remove all local configuration for this backup and move you back to the
                    initial setup. This operation can not be reversed.
                </DialogContentText>
                <hr/>
                <DialogContentText>
                    This will not remove any data from the backup destinations.
                </DialogContentText>
            </DialogContent>
            <DialogActions>
                <Button onClick={handleResetWarningClose} autoFocus={true}>Cancel</Button>
                <Button onClick={performResetWarningClose} color="error" id={"confirmDelete"}>
                    Agree
                </Button>
            </DialogActions>
        </Dialog>
    </Stack>
}
