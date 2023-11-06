import {
    Button,
    Checkbox,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    FormControlLabel,
    Grid,
    Link
} from "@mui/material";
import * as React from "react";
import {createSupportBundle} from "../api/service";
import {useApplication} from "../utils/ApplicationContext";

export default function SupportBundleDialog(props: { open: boolean, onClose: () => void }) {
    const appContext = useApplication();
    const [busy, setBusy] = React.useState(false);
    const [location, setLocation] = React.useState(undefined as string | undefined);

    const [state, setState] = React.useState({
        includeLogs: true,
        includeConfig: true,
        includeMetadata: true,
        includeKey: false
    });

    async function fetchBundle() {
        setBusy(true);
        try {
            setLocation(await createSupportBundle(state));
        } finally {
            setBusy(false);
        }
    }

    return <>

        <Dialog
            open={busy || location !== undefined}
            aria-labelledby="alert-dialog-title"
            aria-describedby="alert-dialog-description"
        >
            {busy &&
                <DialogTitle id="alert-dialog-title">
                    Busy generating bundle please be patient.
                </DialogTitle>
            }

            <DialogContent>
                {busy ?
                    <DialogContentText id="alert-dialog-description" textAlign={"center"}>
                        <CircularProgress/>
                    </DialogContentText>
                    :
                    <DialogContentText id="alert-dialog-description">
                        Support bundle is available from:
                        <br/>
                        <br/>
                        <code><b>{location}</b></code>.
                    </DialogContentText>
                }
            </DialogContent>

            {!busy &&
                <DialogActions>
                    <Button onClick={() => {
                        setLocation(undefined)
                        props.onClose();
                    }}>
                        Close
                    </Button>
                </DialogActions>
            }
        </Dialog>

        <Dialog
            open={props.open && !busy && location === undefined}
            onClose={props.onClose}
            aria-labelledby="alert-dialog-title"
            aria-describedby="alert-dialog-description"
        >
            <DialogTitle id="alert-dialog-title">
                Generate Support Bundle
            </DialogTitle>
            <DialogContent>
                <DialogContentText id="alert-dialog-description">
                    Create a bundle of files to include in a support bundle to help with diagnosing issues.
                    A zip file will be generated with the option below. Data is not automatically submitted
                    to support but has to be done manually through&nbsp;
                    <Link rel="noreferrer" target="_blank" href={`${appContext.backendState.siteUrl}`}
                          underline={"hover"}>the service website</Link>.
                </DialogContentText>
                <hr/>
                <DialogContentText id="alert-dialog-description">
                    <Grid container spacing={0}>
                        <Grid item xs={12}>
                            <FormControlLabel control={<Checkbox
                                checked={state.includeConfig}
                                onChange={(e) => setState({...state, includeConfig: e.target.checked})}
                            />} label="Include configuration without credentials"/>
                        </Grid>
                        <Grid item xs={12} paddingLeft={"2em"}>
                            Contains all your settings <b>except for any login credentials</b>. This includes what files
                            are included in your backup.
                        </Grid>
                        <Grid item xs={12}>
                            <FormControlLabel control={<Checkbox
                                checked={state.includeLogs}
                                onChange={(e) => setState({...state, includeLogs: e.target.checked})}
                            />} label="Include log files"/>
                        </Grid>
                        <Grid item xs={12} paddingLeft={"2em"}>
                            Will contain the location and name of all the files that have been backed up or have had
                            changes detected in the in the last couple of weeks.
                        </Grid>
                        <Grid item xs={12}>
                            <FormControlLabel control={<Checkbox
                                checked={state.includeMetadata}
                                onChange={(e) => setState({...state, includeMetadata: e.target.checked})}
                            />} label="Include metadata repository"/>
                        </Grid>
                        <Grid item xs={12} paddingLeft={"2em"}>
                            Contains metadata about all the files and directories in your backup with history. It does
                            not contain the contents of any of those files.
                        </Grid>
                        <Grid item xs={12}>
                            <FormControlLabel control={<Checkbox
                                checked={state.includeKey}
                                onChange={(e) => setState({...state, includeKey: e.target.checked})}
                            />} label="Include key definition"/>
                        </Grid>
                        <Grid item xs={12} paddingLeft={"2em"}>
                            Include the key definition for your backup. This only includes the public key, <b>it does
                            not include the plain text private encryption key</b>.
                        </Grid>
                    </Grid>
                </DialogContentText>
                <hr/>
                <DialogContentText id="alert-dialog-description">
                    Generating the bundle may take a while. Please be patient after clicking generate.
                </DialogContentText>
            </DialogContent>
            <DialogActions>
                <Button onClick={props.onClose}>Cancel</Button>
                <Button onClick={() => fetchBundle()} autoFocus={true}>
                    Generate Bundle
                </Button>
            </DialogActions>
        </Dialog>
    </>;
}