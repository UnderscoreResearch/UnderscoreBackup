import React from "react";
import MainApp from "./MainApp";
import {Alert, AlertColor, Snackbar, ThemeProvider} from "@mui/material";
import {SnackbarCloseReason} from "@mui/material/Snackbar/Snackbar";
import {BrowserRouter} from "react-router-dom";
import {createTheme} from "@mui/material/styles";

var internalDisplayError: (newMessage: string, severity?: AlertColor) => void;

export function DisplayMessage(newMessage: string, severity?: AlertColor) {
    internalDisplayError(newMessage, severity);
}

interface AppState {
    open: boolean,
    message: string,
    severity: AlertColor
}

const firstPath = `/${window.location.pathname.split('/')[1]}/`;
const mdTheme = createTheme();


export default function App() {
    const [state, setState] = React.useState<AppState>({
        open: false,
        message: "",
        severity: "error"
    });

    internalDisplayError = (newMessage: string, severity?: AlertColor) => {
        setState({
            open: true,
            message: newMessage,
            severity: severity ? severity : "error"
        })
    };

    const handleClose = (event: React.SyntheticEvent | Event, reason?: SnackbarCloseReason) => {
        if (reason === 'clickaway') {
            return;
        }

        setState({
            open: false,
            message: "",
            severity: state.severity
        });
    };

    return <React.Fragment>
        <Snackbar open={state.open} autoHideDuration={6000} onClose={handleClose}>
            <Alert onClose={handleClose} severity={state.severity} sx={{width: '100%'}}>
                {state.message}
            </Alert>
        </Snackbar>
        <ThemeProvider theme={mdTheme}>
            <BrowserRouter basename={firstPath}>
                <MainApp/>
            </BrowserRouter>
        </ThemeProvider>
    </React.Fragment>
}