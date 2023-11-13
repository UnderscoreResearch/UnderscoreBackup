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
    TextField
} from "@mui/material";
import Box from "@mui/material/Box";
import PasswordStrengthBar from "../3rdparty/react-password-strength-bar";
import SavePrivateKey from "./SavePrivateKey";
import * as React from "react";
import {useApplication} from "../utils/ApplicationContext";
import {DisplayMessage} from "../App";
import {changeEncryptionKey} from "../api";
import DividerWithText from "../3rdparty/react-js-cron-mui/components/DividerWithText";

export interface ChangePasswordDialogProps {
    open: boolean;
    onClose: () => void;
}

function defaultState() {
    return {
        oldPassword: "",
        password: "",
        passwordValid: false,
        passwordConfirm: "",
        email: undefined as string | undefined,
        secretRegion: undefined as string | undefined,
        saveSecret: false,
        regeneratePrivateKey: false
    }
}

export default function ChangePasswordDialog(props: ChangePasswordDialogProps) {
    const appContext = useApplication();
    const [state, setState] = React.useState(defaultState())

    function close() {
        props.onClose();
        setState(defaultState());
    }

    async function handleChangePassword() {
        try {
            if (!state.oldPassword) {
                DisplayMessage("Missing old password");
            } else if (!state.passwordValid) {
                DisplayMessage("Password too weak");
            } else if (!state.password) {
                DisplayMessage("Missing new password");
            } else if (state.password !== state.passwordConfirm) {
                DisplayMessage("Password does not match");
            } else if (await changeEncryptionKey(state.oldPassword, state.password, state.regeneratePrivateKey, state.saveSecret, state.secretRegion, state.email)) {
                if (!state.regeneratePrivateKey) {
                    await appContext.update(state.password);
                } else {
                    await appContext.update();
                }
                close()
            }
        } catch (e: any) {
            DisplayMessage(e.toString());
        }
    }

    function validSecret(): boolean {
        if (!state.saveSecret)
            return true;
        return !!state.secretRegion && !!state.email;
    }

    return <Dialog open={props.open} onClose={close} id={"passwordChangeDialog"}>
        <DialogTitle>Change Password</DialogTitle>
        <DialogContent>
            <DialogContentText>
                Change the password used to protect your backup.
            </DialogContentText>
            <Alert severity="warning">Keep your password safe.
                There is no way to recover a lost password unless you enable private key recovery!</Alert>

            <Box
                component="div"
                sx={{
                    '& .MuiTextField-root': {m: 1},
                }}
                style={{marginTop: 4, marginLeft: "-8px", marginRight: "8px"}}
            >
                <TextField label="Existing Password" variant="outlined"
                           fullWidth={true}
                           required={true}
                           value={state.oldPassword}
                           error={!state.oldPassword}
                           id={"oldPassword"}
                           type="password"
                           onChange={(e) => setState({
                               ...state,
                               oldPassword: e.target.value
                           })}/>
                <Box style={{marginLeft: "8px", marginRight: "-8px"}}>
                    <PasswordStrengthBar password={state.password} onChangeScore={(newScore) =>
                        setState((oldState) => ({
                            ...oldState,
                            passwordValid: newScore >= 2
                        }))
                    }/>
                </Box>
                <TextField label="New Password" variant="outlined"
                           fullWidth={true}
                           required={true}
                           value={state.password}
                           error={!state.password}
                           id={"passwordFirst"}
                           type="password"
                           onChange={(e) => setState({
                               ...state,
                               password: e.target.value
                           })}/>
                <TextField label="Confirm New Password" variant="outlined"
                           fullWidth={true}
                           required={true}
                           helperText={state.passwordConfirm !== state.password ? "Does not match" : null}
                           value={state.passwordConfirm}
                           error={state.passwordConfirm !== state.password || !state.password}
                           id={"passwordSecond"}
                           type="password"
                           onChange={(e) => setState({
                               ...state,
                               passwordConfirm: e.target.value
                           })}/>
                <FormControlLabel id="regeneratePrivateKey"
                                  control={<Checkbox checked={state.regeneratePrivateKey} onChange={(e) => {
                                      setState({
                                          ...state,
                                          regeneratePrivateKey: e.target.checked
                                      });
                                  }
                                  }/>} label="Regenerate private key" style={{marginLeft: "-4px"}}/>

                {state.regeneratePrivateKey &&
                    <Alert style={{marginLeft: "8px"}} severity="info">
                        Regenerating your private key should be used if you believe your current password and your
                        existing private key has been compromised. It will take a considerable amount of time
                        because all existing log files need to be regenerated to complete this change. Your backup
                        data does not though, so it is still substantially faster than a complete new backup.
                    </Alert>
                }
                {state.regeneratePrivateKey && appContext.backendState.serviceConnected &&
                    <div style={{marginLeft: "8px", marginTop: "8px"}}>
                        <DividerWithText>Private key recovery</DividerWithText>
                        <SavePrivateKey hasKey={false} onChange={(saveSecret, email, secretRegion) => {
                            setState({
                                ...state,
                                saveSecret: saveSecret,
                                email: email,
                                secretRegion: secretRegion
                            });
                        }} enterEmail={true}/>
                    </div>
                }
            </Box>
        </DialogContent>
        <DialogActions>
            <Button onClick={close}>Cancel</Button>
            <Button
                disabled={!(state.passwordValid && state.oldPassword && state.password === state.passwordConfirm && validSecret())}
                onClick={() => handleChangePassword()} id={"submitPasswordChange"}>OK</Button>
        </DialogActions>
    </Dialog>

}