import React from 'react';
import {createRoot} from 'react-dom/client';
import CssBaseline from '@mui/material/CssBaseline';
import App from './App';
import {AppThemeProvider} from "./utils/Theme";
import {SnackbarProvider} from "notistack";
import {ApplicationContextProvider} from "./utils/ApplicationContext";
import {ActivityContextProvider} from "./utils/ActivityContext";
import {ButtonContextProvider} from "./utils/ButtonContext";

if (window.location.pathname === "/") {
    window.location.pathname = "/fixed/";
} else {
    const container = document.querySelector('#root');
    if (container) {
        const root = createRoot(container);
        root.render(<AppThemeProvider>
            <CssBaseline/>
            <SnackbarProvider>
                <ApplicationContextProvider>
                    <ActivityContextProvider>
                        <ButtonContextProvider>
                            <App/>
                        </ButtonContextProvider>
                    </ActivityContextProvider>
                </ApplicationContextProvider>
            </SnackbarProvider>
        </AppThemeProvider>);
    }
}