import * as React from "react";
import {Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, TextField} from "@mui/material";
import Box from "@mui/material/Box";

export interface PasswordDialogProps {
    label: string,
    dialogText: string,
    open: boolean,
    close: () => void,
    action: (password: string) => void
}

export default function PasswordDialog(props: PasswordDialogProps) {
    const [password, setPassword] = React.useState<string>("");

    return <Dialog open={props.open} onClose={() => props.close()}>
        <DialogTitle>{props.label}</DialogTitle>
        <DialogContent>
            <DialogContentText>
                {props.dialogText}
            </DialogContentText>

            <Box
                component="div"
                sx={{
                    '& .MuiTextField-root': {m: 1},
                }}
                style={{marginTop: 4, marginLeft: "-8px", marginRight: "8px"}}
            >
                <TextField label="Password" variant="outlined"
                           fullWidth={true}
                           required={true}
                           value={password}
                           error={!password}
                           id={"password"}
                           type="password"
                           onKeyDown={(e) => {
                               if (e.key === "Enter" && password.length) {
                                   () => props.close();
                                   props.action(password);
                               }
                           }}
                           onChange={(e) => setPassword(e.target.value)}/>
            </Box>
        </DialogContent>
        <DialogActions>
            <Button onClick={() => props.close()}>Cancel</Button>
            <Button disabled={!password}
                    onClick={() => {
                        props.close();
                        props.action(password);
                    }} id={"actionButton"}>OK</Button>
        </DialogActions>
    </Dialog>
}
