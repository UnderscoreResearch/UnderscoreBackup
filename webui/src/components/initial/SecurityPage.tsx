import {useApplication} from "../../utils/ApplicationContext";
import {createSecret, SourceResponse, updateSource} from "../../api/service";
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
    Link,
    MenuItem,
    Select,
    SelectChangeEvent,
    Stack,
    TextField
} from "@mui/material";
import Paper from "@mui/material/Paper";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import PasswordStrengthBar from "../../3rdparty/react-password-strength-bar";
import * as React from "react";
import {useEffect, useState} from "react";
import DividerWithText from "../../3rdparty/react-js-cron-mui/components/DividerWithText";
import UIAuthentication from "../UIAuthentication";
import {authorizationRedirect, hash} from "../../api/utils";
import base64url from "base64url";
import {BackupManifest, createEncryptionKey, startRemoteRestore} from "../../api";
import {NextButton} from "./NextButton";
import {useActivity} from "../../utils/ActivityContext";

export interface SecurityPageProps {
    onPageChange: (page: string) => void,
    selectedSource?: SourceResponse,
    secretRegion?: string
}

interface SecurityPageState {
    password: string,
    passwordReclaim: string,
    passwordConfirm: string,
    passwordValid: boolean,
    showChangePassword: boolean,
    saveSecret?: boolean,
    secretRegion?: string,
    awaitingValidation: boolean,
    force: boolean
}

export function SecurityPage(props: SecurityPageProps) {
    const appContext = useApplication();
    const activityContext = useActivity();
    const [state, setState] = useState({
        password: "",
        passwordReclaim: "",
        passwordConfirm: "",
        secretRegion: props.secretRegion,
        passwordValid: false,
        showChangePassword: false,
        awaitingValidation: false,
        force: false
    } as SecurityPageState);

    function updatedPasswordScore(newScore: number) {
        setState((oldState) => ({
            ...oldState,
            passwordValid: newScore >= 2
        }));
    }

    function handleChangePasswordClose() {
        setState({
            ...state,
            showChangePassword: false
        });
    }

    function reclaimPassword() {
        window.sessionStorage.setItem("newPassword", state.passwordReclaim);
        authorizationRedirect(appContext.backendState.siteUrl, "/contents",
            `emailHash=${
                encodeURIComponent(hash(base64url.decode(window.localStorage.getItem("email") as string)))}&sourceId=${
                props.selectedSource?.sourceId}&region=${
                props.selectedSource?.secretRegion}`,
            `sourceId=${encodeURIComponent(props.selectedSource?.sourceId as string)}&region=${
                encodeURIComponent(props.selectedSource?.secretRegion as string)}`);
    }

    function submitPassword(force: boolean) {
        setState((oldState) => ({
            ...oldState,
            awaitingValidation: true
        }));
        appContext.busyOperation(async () => {
            if (props.selectedSource) {
                if (await updateSource(props.selectedSource.name, props.selectedSource.sourceId, state.password, force)) {
                    await appContext.update(state.password);
                    await activityContext.update();
                } else {
                    setState((oldState) => ({
                        ...oldState,
                        force: true
                    }));
                }
            } else if (await startRemoteRestore(state.password)) {
                await appContext.update(state.password);
                await activityContext.update();
            }
        });
    }

    useEffect(() => {
        if (appContext.validatedPassword && state.awaitingValidation) {
            props.onPageChange("contents");
            setState({
                ...state,
                awaitingValidation: false
            })
        }
    }, [state.awaitingValidation, appContext.validatedPassword])


    if (!appContext.currentConfiguration && !appContext.rebuildAvailable && !props.selectedSource) {
        return <></>;
    }

    if (appContext.rebuildAvailable || props.selectedSource) {
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
                        you need to provide the original password used to create the backup.
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
                     style={{marginTop: 4, marginLeft: "-8px", marginRight: "8px"}}>
                    <TextField label="Password" variant="outlined"
                               fullWidth={true}
                               required={true}
                               id={"restorePassword"}
                               value={state.password}
                               error={!state.password}
                               type="password"
                               onKeyDown={(e) => {
                                   if (e.key === "Enter" && state.password.length)
                                       submitPassword(false);
                               }}
                               onChange={(e) => setState({
                                   ...state,
                                   password: e.target.value
                               })}/>
                </Box>
                {props.selectedSource && props.selectedSource.secretRegion && window.localStorage.getItem("email") &&
                    <Box component="div" textAlign={"center"}>
                        <Button variant="contained" id="reclaimPassword" color={"error"}
                                onClick={() => setState({...state, showChangePassword: true})}>
                            Recover private key
                        </Button>
                    </Box>
                }
            </Paper>

            <NextButton disabled={state.password.length == 0} force={state.force}
                        onClick={() => submitPassword(state.force)}/>

            <Dialog open={state.showChangePassword} onClose={handleChangePasswordClose}>
                <DialogTitle>New Password</DialogTitle>
                <DialogContent>
                    <DialogContentText>
                        Choose a new password used to protect your backup.
                    </DialogContentText>

                    <PasswordStrengthBar password={state.passwordReclaim}
                                         onChangeScore={(newScore) => updatedPasswordScore(newScore)}/>
                    <Box
                        component="div"
                        sx={{
                            '& .MuiTextField-root': {m: 1},
                        }}
                        style={{marginTop: 4, marginLeft: "-8px", marginRight: "8px"}}
                    >
                        <TextField label="New Password" variant="outlined"
                                   fullWidth={true}
                                   required={true}
                                   value={state.passwordReclaim}
                                   error={!state.passwordReclaim}
                                   id={"passwordReclaim"}
                                   type="password"
                                   onChange={(e) => setState({
                                       ...state,
                                       passwordReclaim: e.target.value
                                   })}/>
                        <TextField label="Confirm New Password" variant="outlined"
                                   fullWidth={true}
                                   required={true}
                                   helperText={state.passwordConfirm !== state.passwordReclaim ? "Does not match" : null}
                                   value={state.passwordConfirm}
                                   error={state.passwordConfirm !== state.passwordReclaim || !state.passwordConfirm}
                                   id={"passwordReclaimSecond"}
                                   type="password"
                                   onChange={(e) => setState({
                                       ...state,
                                       passwordConfirm: e.target.value
                                   })}/>
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={handleChangePasswordClose}>Cancel</Button>
                    <Button disabled={!state.passwordValid || state.passwordReclaim !== state.passwordConfirm}
                            onClick={() => reclaimPassword()} id={"submitPasswordChange"}>OK</Button>
                </DialogActions>
            </Dialog>
        </Stack>
    }
    let privateKeyDisabled = !appContext.backendState.serviceSourceId || !window.localStorage.getItem("email") || appContext.hasKey;

    function updateManifest(manifest: BackupManifest) {
        appContext.setState((oldState) => ({
            ...oldState,
            currentConfiguration: {
                ...oldState.currentConfiguration,
                manifest: manifest
            }
        }));
    }

    function createKey() {
        setState((oldState) => ({
            ...oldState,
            awaitingValidation: true
        }));
        appContext.busyOperation(async () => {
            if (appContext.hasKey) {
                if (state.password) {
                    await appContext.update(state.password);
                } else {
                    props.onPageChange("contents");
                }
            } else {
                if (await createEncryptionKey(state.password)) {
                    if (state.saveSecret && state.secretRegion && !privateKeyDisabled) {
                        await createSecret(state.password as string, state.secretRegion,
                            base64url.decode(window.localStorage.getItem("email") as string))
                    }

                    const currentConfiguration = appContext.currentConfiguration;
                    await appContext.update(state.password);
                    appContext.setState((oldState) => ({
                        ...oldState,
                        currentConfiguration: currentConfiguration
                    }));
                }
            }
        });
    }

    return <Stack spacing={2} style={{width: "100%"}}>
        <Paper sx={{p: 2}}>
            <Typography variant="h3" component="div">
                Enter backup password
            </Typography>

            {appContext.hasKey ?
                <>
                    <Typography variant="body1" component="div">
                        <p>
                            You have already created a backup password. To complete your setup please enter it below.
                        </p>
                    </Typography>

                    <Box
                        component="div"
                        sx={{
                            '& .MuiTextField-root': {m: 1},
                        }}
                        style={{marginTop: "4px", marginLeft: "-8px", marginRight: "8px"}}
                    >
                        <TextField label="Password" variant="outlined"
                                   fullWidth={true}
                                   required={true}
                                   value={state.password}
                                   type="password"
                                   onChange={(e) => setState({
                                       ...state,
                                       password: e.target.value
                                   })}/>
                    </Box>
                </>
                :
                <>
                    <Typography variant="body1" component="div">
                        <p>
                            Enter the password with which your backup will be protected.
                        </p>
                    </Typography>
                    <Alert severity="warning">Please keep your password safe.
                        There is no way to recover a lost password unless you enable private key recovery!</Alert>

                    <PasswordStrengthBar password={state.password}
                                         onChangeScore={(newScore) => updatedPasswordScore(newScore)}/>

                    <Box
                        component="div"
                        sx={{
                            '& .MuiTextField-root': {m: 1},
                        }}
                        style={{marginTop: "4px", marginLeft: "-8px", marginRight: "8px"}}
                    >
                        <TextField label="Password" variant="outlined"
                                   fullWidth={true}
                                   required={true}
                                   value={state.password}
                                   error={!state.password && !appContext.hasKey}
                                   id={"passwordFirst"}
                                   type="password"
                                   onChange={(e) => setState({
                                       ...state,
                                       password: e.target.value
                                   })}/>

                        <TextField label="Confirm Password" variant="outlined"
                                   fullWidth={true}
                                   required={true}
                                   helperText={state.passwordConfirm !== state.password ? "Does not match" : null}
                                   value={state.passwordConfirm}
                                   error={(state.passwordConfirm !== state.password || !state.password) && !appContext.hasKey}
                                   id={"passwordSecond"}
                                   type="password"
                                   onChange={(e) => setState({
                                       ...state,
                                       passwordConfirm: e.target.value
                                   })}/>
                    </Box>
                </>
            }
        </Paper>

        <Paper sx={{p: 2}}>
            <Grid container spacing={2} alignItems={"center"}>
                <Grid item xs={12}>
                    <DividerWithText>Private key recovery</DividerWithText>
                </Grid>
                <Grid item md={6} xs={12}>
                    <FormControlLabel
                        style={{color: !privateKeyDisabled && state.saveSecret === undefined ? "#d32f2f" : "inherit"}}
                        control={<Checkbox
                            id={"saveSecret"}
                            disabled={privateKeyDisabled}
                            defaultChecked={false}
                            onChange={(e) => setState({
                                ...state,
                                saveSecret: e.target.checked
                            })}
                            indeterminate={state.saveSecret === undefined}
                        />} label={"Enable private key recovery from" + (!privateKeyDisabled ? " *" : "")}/>
                </Grid>
                <Grid item md={6} xs={12}>
                    <Select style={{marginRight: "8px"}}
                            fullWidth={true}
                            value={props.secretRegion}
                            disabled={!appContext.backendState.serviceSourceId || !state.saveSecret}
                            label="Region"
                            onChange={(event: SelectChangeEvent) => {
                                setState((oldState) => ({
                                    ...oldState,
                                    secretRegion: event.target.value as string,
                                }));
                            }}>
                        <MenuItem value={"us-west"}>North America (Oregon)</MenuItem>
                        <MenuItem value={"eu-central"}>Europe (Frankfurt)</MenuItem>
                        <MenuItem value={"ap-southeast"}>Asia (Singapore)</MenuItem>
                    </Select>
                </Grid>
                <Grid item xs={12}>
                    {!!window.localStorage.getItem("email") ?
                        <Alert severity="warning">
                            Enabling private key recover will store your private
                            encryption key with online service! For more information see&nbsp;
                            <Link rel="noreferrer" target="_blank" underline={"hover"}
                                  href={"https://underscorebackup.com/blog?source=https%3A%2F%2Fblog.underscorebackup.com%2F2023%2F02%2Fhow-does-private-key-recovery-work.html"}>
                                this documentation article.
                            </Link>
                        </Alert>
                        :
                        <Alert severity="warning">This option is only available if you are using a
                            service account and complete the setup in a single browser session!
                            {!!appContext.backendState.serviceSourceId &&
                                <span> To enable go back to the beginning of the setup, disconnect from the service and reconnect!</span>
                            }
                        </Alert>
                    }
                </Grid>
            </Grid>
        </Paper>

        <UIAuthentication manifest={appContext.currentConfiguration.manifest} onChange={updateManifest}/>

        <NextButton disabled={(!state.passwordValid && !appContext.hasKey) ||
            (state.saveSecret === undefined && !privateKeyDisabled)} onClick={createKey}/>
    </Stack>
}