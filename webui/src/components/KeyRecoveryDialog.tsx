import {
    Button,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle, Grid,
    TextField
} from "@mui/material";
import * as React from "react";
import ServiceRegion from "./ServiceRegion";
import {createSecret, deleteSecret, getSource, SourceResponse} from "../api/service";
import base64url from "base64url";
import PasswordStrengthBar from "../3rdparty/react-password-strength-bar";
import {authorizationRedirect, hash} from "../api/utils";
import {useApplication} from "../utils/ApplicationContext";

interface KeyRecoveryDialogProps {
    open: boolean,
    onClose: () => void,
}

export default function KeyRecoveryDialog(props: KeyRecoveryDialogProps) {
    const appContext = useApplication();
    const [source, setSource] = React.useState(undefined as SourceResponse | undefined);
    const [busy, setBusy] = React.useState(true);
    const [deleteConfirmation, setDeleteConfirmation] = React.useState("");
    const [email, setEmail] = React.useState(() => base64url.decode(window.localStorage.getItem("email") as string??""));
    const [password, setPassword] = React.useState("");
    const [passwordScore, setPasswordScore] = React.useState(0);
    const [confirmPassword, setConfirmPassword] = React.useState("");
    const [region, setRegion] = React.useState("");

    React.useEffect(() => {
        async function readSource() {
            const source = await getSource();
            setSource(source.sources[0]);
            setBusy(false);
        }

        if (props.open) {
            setBusy(true);
            setDeleteConfirmation("");
            readSource();
        }
    }, [props.open]);

    function clearState() {
        setDeleteConfirmation("");
        setPassword("");
        setConfirmPassword("");
        setRegion("");
        setPasswordScore(0)
    }

    async function executeDelete() {
        if (source) {
            setBusy(true);
            await deleteSecret();
            setSource({
                ...source,
                secretRegion: undefined,
            });
            clearState();
            setBusy(false);
        }
    }

    async function executeRecover() {
        if (source) {
            setBusy(true);
            try {
                window.localStorage.setItem("email", base64url.encode(email));
                window.sessionStorage.setItem("newPassword", password);
                authorizationRedirect(appContext.backendState.siteUrl, "/settings",
                    `emailHash=${
                        encodeURIComponent(hash(email))}&sourceId=${
                        source.sourceId}&region=${
                        source.secretRegion}`,
                    `sourceId=${encodeURIComponent(source.sourceId as string)}&region=${
                        encodeURIComponent(source.secretRegion as string)}`);
            } finally {
                setBusy(false);
            }
        }
    }

    async function executeEnable() {
        if (source) {
            setBusy(true);
            try {
                if (await createSecret(password, region, email)) {
                    setSource({
                        ...source,
                        secretRegion: region
                    });
                }
            } finally {
                clearState();
                setBusy(false);
            }
        }
    }

    function close() {
        clearState();
        props.onClose();
    }

    return <Dialog open={props.open} onClose={() => close()} fullWidth={true} maxWidth={"md"}>
        <DialogTitle>Private Key Recovery</DialogTitle>
        <DialogContent>
            {busy ?
                <div style={{textAlign: "center"}}>
                    <CircularProgress color="inherit" size={"2em"}/>
                </div>
                :(source?.secretRegion ?
                        <>
                            <DialogContentText style={{marginTop: "8px", marginBottom: "8px"}}>
                                Private key recovery is currently enabled from region <b>{source.secretRegion}</b>.
                            </DialogContentText>

                            <DialogContentText style={{marginTop: "8px", marginBottom: "8px"}}>
                                To recover the private key enter the service email and the new password below and press the <b>Recover</b> button.
                            </DialogContentText>

                            <Grid container spacing={2}>
                                <Grid item xs={12}>
                                    <TextField value={email} fullWidth={true} id="email"
                                               type={"email"}
                                               label={"Email"}
                                               required={true}
                                               error={!email}
                                               onChange={(event) => {
                                                   setEmail(event.target.value)
                                               }}/>
                                </Grid>
                                <Grid item xs={12}>
                                    <PasswordStrengthBar password={password}
                                                         onChangeScore={(newScore) => setPasswordScore(newScore)}/>
                                </Grid>
                                <Grid item xs={12}>
                                    <TextField value={password} fullWidth={true} id="password"
                                               type={"password"}
                                               required={true}
                                               label={"Password"}
                                               error={!password}
                                               onChange={(event) => {
                                                   setPassword(event.target.value)
                                               }}/>
                                </Grid>
                                <Grid item xs={12}>
                                    <TextField value={confirmPassword} fullWidth={true} id="confirmPassword"
                                               type={"password"}
                                               required={true}
                                               helperText={confirmPassword !== password ? "Does not match" : null}
                                               error={confirmPassword !== password || !confirmPassword}
                                               label={"Confirm password"}
                                               onChange={(event) => {
                                                   setConfirmPassword(event.target.value)
                                               }}/>
                                </Grid>
                            </Grid>

                            <div style={{textAlign: "center", margin: "8px"}}>
                                <Button variant={"contained"} id="deleteKeyConfirm" disabled={!email || passwordScore < 2 || password !== confirmPassword}
                                        onClick={executeRecover} autoFocus>
                                    Recover Private Key
                                </Button>
                            </div>

                            <hr/>

                            <DialogContentText style={{marginTop: "8px", marginBottom: "8px"}}>
                                To delete the private key recovery data please type <b>REMOVE</b> in the box below and press the <b>Remove</b> button.
                            </DialogContentText>

                            <TextField value={deleteConfirmation} fullWidth={true} id="deleteKeyConfirmation"
                                       onChange={(event) => {
                                           setDeleteConfirmation(event.target.value)
                                       }}/>

                            <div style={{textAlign: "center", margin: "8px"}}>
                                <Button variant={"contained"} id="deleteKeyConfirm" disabled={deleteConfirmation !== "REMOVE"}
                                        onClick={executeDelete} color={"error"}>
                                    Remove
                                </Button>
                            </div>
                        </>
                        :
                        <>
                            <DialogContentText style={{marginTop: "8px", marginBottom: "8px"}}>
                                Private key recovery is currently not enabled.
                            </DialogContentText>
                            <DialogContentText style={{marginTop: "8px", marginBottom: "8px"}}>
                                To enable enter the current email for your account and the backup password (Not the service
                                password) below, select the region to store the secret and the click the <b>Enable</b>.
                            </DialogContentText>

                            <Grid container spacing={2}>
                                <Grid item xs={12} sm={12}>
                                    <TextField value={email} fullWidth={true} id="email"
                                               type={"email"}
                                               label={"Email"}
                                               onChange={(event) => {
                                                   setEmail(event.target.value)
                                               }}/>
                                </Grid>
                                <Grid item xs={12} sm={8}>
                                    <TextField value={password} fullWidth={true} id="password"
                                               type={"password"}
                                               label={"Password"}
                                               onChange={(event) => {
                                                   setPassword(event.target.value)
                                               }}/>
                                </Grid>
                                <Grid item xs={12} sm={4}>
                                    <ServiceRegion region={region} onChange={(newRegion) => setRegion(newRegion)}/>
                                </Grid>
                            </Grid>

                            <div style={{textAlign: "center", margin: "8px"}}>
                                <Button variant={"contained"} id="enableConfirm" disabled={!password || !region || !email}
                                        onClick={executeEnable} autoFocus>
                                    Enable
                                </Button>
                            </div>
                        </>
                )
            }
            <DialogContentText>
            </DialogContentText>
        </DialogContent>
        <DialogActions>
            <Button onClick={() => close()} id={"submitConfigChange"}>Close</Button>
        </DialogActions>
    </Dialog>

}