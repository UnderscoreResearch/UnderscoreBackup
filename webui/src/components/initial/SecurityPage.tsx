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
    Stack,
    TextField,
    Tooltip
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
import SavePrivateKey from "../SavePrivateKey";

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

export default function SecurityPage(props: SecurityPageProps) {
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
                let error: string | undefined = undefined;
                const ret = await updateSource(props.selectedSource.name, props.selectedSource.sourceId, state.password, force,
                    (err) => {
                        error = err;
                        return false;
                    });

                if (ret) {
                    await appContext.update(state.password);
                    await activityContext.update();
                } else {
                    if (error === "Trying to adopt a source with existing config") {
                        setState((oldState) => ({
                            ...oldState,
                            force: true
                        }));
                    }
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
                    if (state.saveSecret && state.secretRegion) {
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
            <DividerWithText>Private key recovery</DividerWithText>
            <SavePrivateKey hasKey={appContext.hasKey} secretRegion={props.secretRegion} enterEmail={false} onChange={
                (saveSecret, email, secretRegion) => setState({
                    ...state,
                    saveSecret: saveSecret,
                    secretRegion: secretRegion
                })
            }/>
        </Paper>

        <UIAuthentication manifest={appContext.currentConfiguration.manifest} onChange={updateManifest}/>

        <Paper sx={{p: 2}}>
            <Tooltip
                title={"Usage information include the size of your backup and how many files are stored as well as any potential errors. No private information is included."}>
                <FormControlLabel control={<Checkbox
                    id={"reportUsage"}
                    checked={appContext.backendState.serviceConnected &&
                        (appContext.currentConfiguration.manifest.reportStats || appContext.currentConfiguration.manifest.reportStats === undefined)}
                    disabled={!appContext.backendState.serviceConnected}
                    onChange={(e) => updateManifest({
                        ...appContext.currentConfiguration.manifest,
                        reportStats: e.target.checked
                    })}
                />} label="Record backup usage with service"/>
            </Tooltip>
        </Paper>

        <NextButton disabled={(!state.passwordValid && !appContext.hasKey) ||
            (state.saveSecret === undefined)} onClick={createKey}/>
    </Stack>
}