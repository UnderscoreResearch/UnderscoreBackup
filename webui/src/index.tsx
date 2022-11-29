import * as React from 'react';
import {createRoot} from 'react-dom/client';
import CssBaseline from '@mui/material/CssBaseline';
import {ThemeProvider} from '@mui/material/styles';
import App from './App';
import theme from './theme';

if (window.location.pathname === "/") {
    window.location.pathname = "/fixed/";
} else {
    const container = document.querySelector('#root');
    if (container) {
        const root = createRoot(container);
        root.render(<ThemeProvider theme={theme}>
            <CssBaseline/>
            <App/>
        </ThemeProvider>);
    }
}