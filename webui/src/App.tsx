import React from "react";
import MainApp from "./MainApp";
import {Alert, Snackbar, ThemeProvider} from "@mui/material";
import {SnackbarCloseReason} from "@mui/material/Snackbar/Snackbar";
import {BrowserRouter} from "react-router-dom";
import {createTheme} from "@mui/material/styles";

var internalDisplayError: (newMessage: string) => void;

export function DisplayError(newMessage: string) {
    internalDisplayError(newMessage);
}

interface AppState {
    open: boolean,
    message: string
}

const firstPath = `/${window.location.pathname.split('/')[1]}/`;
const mdTheme = createTheme();


export default function App() {
    const [state, setState] = React.useState<AppState>({
        open: false,
        message: ""
    });

    internalDisplayError = (newMessage: string) => {
        setState({
            open: true,
            message: newMessage
        })
    };

    const handleClose = (event: React.SyntheticEvent | Event, reason?: SnackbarCloseReason) => {
        if (reason === 'clickaway') {
            return;
        }

        setState({
            open: false,
            message: ""
        });
    };

    return <React.Fragment>
        <Snackbar open={state.open} autoHideDuration={6000} onClose={handleClose}>
            <Alert onClose={handleClose} severity="error" sx={{width: '100%'}}>
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