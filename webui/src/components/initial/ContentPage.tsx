import {BackupSet, BackupSetRoot, getLocalFiles} from "../../api";
import FileTreeView from "../FileTreeView";
import * as React from "react";
import {ApplicationContext, useApplication} from "../../utils/ApplicationContext";
import Paper from "@mui/material/Paper";
import {Button, Checkbox, FormControlLabel, Grid, LinearProgress, Stack} from "@mui/material";
import DividerWithText from "../../3rdparty/react-js-cron-mui/components/DividerWithText";
import Cron from "../../3rdparty/react-js-cron-mui";
import {useNavigate} from "react-router-dom";
import {useActivity} from "../../utils/ActivityContext";
import Typography from "@mui/material/Typography";
import LogTable from "../LogTable";

export interface SetState {
    set: BackupSet
}

function defaultSet(appContext: ApplicationContext): BackupSet {
    if (appContext.currentConfiguration && appContext.currentConfiguration.sets && appContext.currentConfiguration.sets.length > 0) {
        return appContext.currentConfiguration.sets[0];
    }
    return appContext.backendState.defaultSet
}

export function ContentPage() {
    const appContext = useApplication();
    const navigate = useNavigate();
    const activityContext = useActivity();

    const [state, setState] = React.useState(() => {
        return {
            set: defaultSet(appContext)
        } as SetState
    });


    function fileSelectionChanged(roots: BackupSetRoot[]) {
        setState({
            ...state,
            set: {
                ...state.set,
                roots: roots
            }
        });
    }

    function changedSchedule(value: string) {
        if (state.set.schedule !== undefined) {
            setState({
                ...state,
                set: {
                    ...state.set,
                    schedule: value
                }
            })
        }
    }

    async function saveAndExit(start: boolean) {
        appContext.setState((oldState) => ({
            ...oldState,
            currentConfiguration: {
                ...oldState.currentConfiguration,
                sets: [state.set],
                manifest: {
                    ...oldState.currentConfiguration.manifest,
                    interactiveBackup: start,
                    initialSetup: undefined
                }
            },
            validatedPassword: false
        }));
        await appContext.applyChanges();
        if (start) {
            navigate("/");
        } else {
            navigate("/sets");
        }
    }

    async function exitBackup() {
        appContext.setState((oldState) => ({
            ...oldState,
            currentConfiguration: {
                ...oldState.currentConfiguration,
                manifest: {
                    ...oldState.currentConfiguration.manifest,
                    interactiveBackup: true,
                    initialSetup: undefined
                }
            },
            validatedPassword: false
        }));
        await appContext.applyChanges();
        navigate("/");
    }

    async function exitRestore() {
        appContext.setState((oldState) => ({
            ...oldState,
            currentConfiguration: {
                ...oldState.currentConfiguration,
                manifest: {
                    ...oldState.currentConfiguration.manifest,
                    initialSetup: undefined
                }
            },
            validatedPassword: false
        }));
        await appContext.applyChanges();
        navigate("/restore");
    }

    if (!appContext.backendState.repositoryReady ||
        (appContext.originalConfiguration.sets && appContext.originalConfiguration.sets.length > 0)) {
        const rebuildActivity = activityContext.activity.filter((a) => a.code === "REPLAY_LOG_PROCESSED_FILES");

        let progress = 0;
        if (rebuildActivity.length == 1 &&
            rebuildActivity[0].totalValue &&
            rebuildActivity[0].value !== undefined) {
            progress = 100 * rebuildActivity[0].value / rebuildActivity[0].totalValue;
        }

        return <Stack spacing={2} style={{width: "100%"}}>
            <Paper sx={{p: 2}}>
                {rebuildActivity.length === 0 ?
                    <Grid container spacing={2} alignItems={"center"}>
                        <Grid item md={12} sm={12} xs={12}>
                            <Typography>
                                Adoption completed what would you like to do next?
                            </Typography>
                        </Grid>
                        <Grid item md={4} sm={12} xs={12}/>
                        <Grid item md={4} sm={6} xs={12} textAlign={"center"}>
                            <Button variant="outlined"
                                    fullWidth={true}
                                    disabled={state.set.roots.length === 0}
                                    onClick={() => exitRestore()}
                                    size="large" id="exit">
                                Restore data
                            </Button>
                        </Grid>
                        <Grid item md={4} sm={6} xs={12} textAlign={"center"}>
                            <Button variant="contained"
                                    fullWidth={true}
                                    disabled={state.set.roots.length === 0}
                                    onClick={() => exitBackup()}
                                    size="large" id="next">
                                Restart backup
                            </Button>
                        </Grid>
                    </Grid>
                    :
                    <>
                        <Grid container spacing={2} alignItems={"center"}>
                            <Grid item md={12} sm={12} xs={12}>
                                <DividerWithText>Rebuilding repository</DividerWithText>
                                <Typography>
                                    Before you can continue your backup or start restoring, the repository needs to
                                    be rebuilt locally. This can take a while.
                                </Typography>
                            </Grid>
                            <Grid item md={12} sm={12} xs={12}>
                                <LinearProgress variant="determinate" value={progress}/>
                            </Grid>
                            <Grid item md={12} sm={12} xs={12}>
                                <LogTable onlyErrors={true}/>
                            </Grid>
                        </Grid>
                    </>
                }
            </Paper>
        </Stack>
    }

    return <Stack spacing={2} style={{width: "100%"}}>
        <Paper sx={{p: 2}}>
            <Grid container spacing={2} alignItems={"center"}>
                <Grid item xs={12}>
                    <DividerWithText>Contents</DividerWithText>
                </Grid>
                <Grid item xs={12}>
                    <FileTreeView
                        fileFetcher={getLocalFiles}
                        backendState={appContext.backendState}
                        roots={state.set.roots}
                        stateValue={""}
                        onChange={fileSelectionChanged}
                    />
                </Grid>
                <Grid item xs={12}>
                    <DividerWithText>Schedule</DividerWithText>
                </Grid>
                <Grid item xs={12}>
                    <FormControlLabel control={<Checkbox
                        checked={state.set.schedule !== undefined}
                        onChange={(e) => setState({
                            ...state,
                            set: {
                                ...state.set,
                                schedule: e.target.checked ? "0 3 * * *" : undefined
                            }
                        })}
                    />} label="Run on schedule"/>

                    <Cron disabled={state.set.schedule === undefined}
                          value={state.set.schedule ? state.set.schedule : "0 3 * * *"} setValue={changedSchedule}
                          clearButton={false}/>

                    <FormControlLabel control={<Checkbox
                        disabled={state.set.schedule === undefined}
                        checked={state.set.continuous && state.set.schedule !== undefined}
                        onChange={() => setState({
                            ...state,
                            set: {
                                ...state.set,
                                continuous: !state.set.continuous
                            }
                        })}
                    />} label="Continuously listen for file changes"/>
                </Grid>

                <Grid item md={4} sm={12} xs={12}/>
                <Grid item md={4} sm={6} xs={12} textAlign={"center"}>
                    <Button variant="outlined"
                            fullWidth={true}
                            disabled={state.set.roots.length === 0}
                            onClick={() => saveAndExit(false)}
                            size="large" id="exit">
                        Save and Exit
                    </Button>
                </Grid>
                <Grid item md={4} sm={6} xs={12} textAlign={"center"}>
                    <Button variant="contained"
                            fullWidth={true}
                            disabled={state.set.roots.length === 0}
                            onClick={() => saveAndExit(true)}
                            size="large" id="next">
                        Start Backup
                    </Button>
                </Grid>
            </Grid>
        </Paper>
    </Stack>
}