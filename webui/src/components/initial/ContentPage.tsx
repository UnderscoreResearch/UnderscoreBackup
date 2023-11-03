import {BackupSet, BackupSetRoot, getLocalFiles} from "../../api";
import FileTreeView from "../FileTreeView";
import * as React from "react";
import {ApplicationContext, useApplication} from "../../utils/ApplicationContext";
import Paper from "@mui/material/Paper";
import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Button,
    Checkbox,
    FormControlLabel,
    Grid,
    Stack,
    Table,
    TableBody,
    TableContainer
} from "@mui/material";
import DividerWithText from "../../3rdparty/react-js-cron-mui/components/DividerWithText";
import Cron from "../../3rdparty/react-js-cron-mui";
import {useNavigate} from "react-router-dom";
import {useActivity} from "../../utils/ActivityContext";
import Typography from "@mui/material/Typography";
import LogTable from "../LogTable";
import {ExpandMore} from "@mui/icons-material";
import {StatusRow} from "../StatusRow";
import TableRow from "@mui/material/TableRow";
import TableCell from "@mui/material/TableCell";
import {useTheme} from "../../utils/Theme";

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
    const theme = useTheme();
    const navigate = useNavigate();
    const activityContext = useActivity();

    const [state, setState] = React.useState(() => {
        return {
            set: defaultSet(appContext)
        } as SetState
    });

    const [details, setDetails] = React.useState<boolean>(false);

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
        appContext.setBusy(true);
        appContext.setState({
            ...appContext.state,
            currentConfiguration: {
                ...appContext.state.currentConfiguration,
                sets: [state.set],
                manifest: {
                    ...appContext.state.currentConfiguration.manifest,
                    interactiveBackup: start,
                    initialSetup: undefined
                }
            },
            validatedPassword: false
        });
        await appContext.applyChanges();
        if (start) {
            navigate("/");
        } else {
            navigate("/sets");
        }
    }

    async function exitBackup() {
        appContext.setBusy(true);
        appContext.setState({
            ...appContext.state,
            currentConfiguration: {
                ...appContext.state.currentConfiguration,
                manifest: {
                    ...appContext.state.currentConfiguration.manifest,
                    interactiveBackup: true,
                    initialSetup: undefined
                }
            },
            validatedPassword: false
        });
        await appContext.applyChanges();
        navigate("/");
    }

    async function exitRestore() {
        appContext.setBusy(true);
        appContext.setState({
            ...appContext.state,
            currentConfiguration: {
                ...appContext.state.currentConfiguration,
                manifest: {
                    ...appContext.state.currentConfiguration.manifest,
                    initialSetup: undefined
                }
            },
            validatedPassword: false
        });
        await appContext.applyChanges();
        navigate("/restore");
    }

    if (!appContext.backendState.repositoryReady ||
        (appContext.originalConfiguration.sets && appContext.originalConfiguration.sets.length > 0)) {
        const rebuildActivity = activityContext.activity.filter((a) => a.code === "REPLAY_LOG_PROCESSED_FILES");
        const rebuildProgress = rebuildActivity.length === 1 ? {
            ...rebuildActivity[0],
            message: "Before you can continue your backup or start restoring, the repository needs to be rebuilt locally. This can take a while."
        } : undefined;

        return <Stack spacing={2} style={{width: "100%"}}>
            {rebuildProgress ?
                <>
                    <TableContainer component={Paper}>
                        <Table sx={{minWidth: 650}} aria-label="simple table">
                            <TableBody>
                                <TableRow>
                                    <TableCell colSpan={2} className={"cell-without-progress"} style={{
                                        background: theme.theme.palette.background.default,
                                        border: 0,
                                        fontSize: "1rem"
                                    }}>
                                        <DividerWithText>Rebuilding repository</DividerWithText>
                                    </TableCell>
                                </TableRow>
                                <StatusRow row={rebuildProgress} etaOnly={!details}/>
                            </TableBody>
                        </Table>
                    </TableContainer>

                    <Paper>
                        <Accordion
                            expanded={details}
                            onChange={(event, expanded) => {
                                setDetails(expanded);
                            }}
                            sx={{
                                // Remove shadow
                                boxShadow: "none",
                                // Remove default divider
                                "&:before": {
                                    display: "none",
                                }
                            }}>
                            <AccordionSummary expandIcon={<ExpandMore/>}>
                                <Typography>Details</Typography>
                            </AccordionSummary>
                            <AccordionDetails sx={{
                                margin: 0,
                                padding: 0
                            }}>
                                {activityContext.activity.length > 0 &&
                                    <>
                                        <div style={{paddingLeft: "16px", paddingRight: "16px"}}>
                                            <DividerWithText>Additional stats</DividerWithText>
                                        </div>
                                        <TableContainer style={{borderTop: 0}}>
                                            <Table sx={{minWidth: 650}} aria-label="simple table">
                                                <TableBody>
                                                    {activityContext.activity.filter(item => item.code !== "REPLAY_LOG_PROCESSED_FILES")
                                                        .map((row) => <StatusRow key={row.code} row={row}/>)}
                                                </TableBody>
                                            </Table>
                                        </TableContainer>
                                    </>
                                }
                            </AccordionDetails>
                        </Accordion>
                    </Paper>
                    <LogTable onlyErrors={!details}/>
                </>
                :
                <Paper sx={{p: 2}}>
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
                </Paper>
            }
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
                            disabled={state.set.roots.length === 0 || appContext.isBusy()}
                            onClick={() => saveAndExit(false)}
                            size="large" id="exit">
                        Save and Exit
                    </Button>
                </Grid>
                <Grid item md={4} sm={6} xs={12} textAlign={"center"}>
                    <Button variant="contained"
                            fullWidth={true}
                            disabled={state.set.roots.length === 0 || appContext.isBusy()}
                            onClick={() => saveAndExit(true)}
                            size="large" id="next">
                        Start Backup
                    </Button>
                </Grid>
            </Grid>
        </Paper>
    </Stack>
}