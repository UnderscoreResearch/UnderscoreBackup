import * as React from "react";
import {Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle} from "@mui/material";

export interface ConfirmationDialogProps {
    label: string,
    dialogText: string[],
    open: boolean,
    close: () => void,
    action: () => void
}

export default function ConfirmationDialog(props: ConfirmationDialogProps) {
    return <Dialog open={props.open} onClose={() => props.close()}>
        <DialogTitle>{props.label}</DialogTitle>
        <DialogContent>
            {props.dialogText.map((text) =>
                <DialogContentText key={text} style={{marginBottom: "16px"}}>
                    {text}
                </DialogContentText>
            )}
        </DialogContent>
        <DialogActions>
            <Button onClick={() => props.close()}>Cancel</Button>
            <Button onClick={() => {
                props.close();
                props.action();
            }} id={"confirmationButton"}>OK</Button>
        </DialogActions>
    </Dialog>
}
