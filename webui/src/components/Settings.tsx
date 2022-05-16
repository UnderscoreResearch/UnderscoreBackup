import * as React from "react";
import {BackupConfiguration, BackupGlobalLimits, BackupManifest, PropertyMap} from "../api";
import {Checkbox, FormControlLabel, Grid, Paper, Stack, TextField} from "@mui/material";
import DividerWithText from "../3rdparty/react-js-cron-mui/components/DividerWithText";
import SpeedLimit from "./SpeedLimit";
import PropertyMapEditor from "./PropertyMapEditor";
import UIAuthentication from "./UIAuthentication";
import Cron from "../3rdparty/react-js-cron-mui";

export interface SettingsProps {
    config: BackupConfiguration,
    onChange: (BackupConfiguration) => void
}

interface SettingsState {
    manifest: BackupManifest,
    limits: BackupGlobalLimits,
    properties?: PropertyMap
}

export default function Settings(props: SettingsProps) {
    const [state, setState] = React.useState({
        manifest: props.config.manifest,
        properties: props.config.properties,
        limits: props.config.limits ? props.config.limits : {}
    } as SettingsState);

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

    return <Stack spacing={2}>
        <UIAuthentication manifest={state.manifest} onChange={(manifest) => updateState({
            ...state,
            manifest: manifest
        })}/>
        <Paper sx={{p: 2}}>
            <Grid container spacing={2}>
                <Grid item xs={12}>
                    <DividerWithText>Global Limits</DividerWithText>
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
                                       maximumUploadThreads: e.target.value as number
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
                                       maximumDownloadThreads: e.target.value as number
                                   }
                               })}/>
                </Grid>
            </Grid>
        </Paper>
        <Paper sx={{p: 2}}>
            <DividerWithText>Advanced Settings</DividerWithText>
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
            <DividerWithText>Custom Properties</DividerWithText>
            <PropertyMapEditor properties={state.properties} onChange={(newProperties) => {
                const newState = {
                    ...state,
                    properties: newProperties
                }
                updateState(newState);
            }
            }/>
        </Paper>
    </Stack>
}