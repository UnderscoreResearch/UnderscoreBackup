import React from "react";
import createLocalStorageState from "use-local-storage-state";
import {PaletteMode, Theme, ThemeProvider, useMediaQuery} from "@mui/material";
import {createTheme} from "@mui/material/styles";

import {red} from '@mui/material/colors';
import {invertedLogo, logo} from "../images";

interface ThemeContext {
    mode: PaletteMode,
    theme: Theme,
    logo: string,
    invertedLogo: string,
    toggle: () => void
}

const themeContext = React.createContext({
    mode: "light",
    "logo": logo,
    invertedLogo: invertedLogo,
    toggle: () => {
    }
} as ThemeContext);

export function useTheme(): ThemeContext {
    return React.useContext(themeContext);
}

export function AppThemeProvider(props: { children: React.ReactNode }) {
    let [storedTheme, setStoredTheme] = createLocalStorageState(
        "storedTheme",
        {
            defaultValue: undefined as string | undefined,
        }
    );

    // Get system dark mode preference
    const prefersDarkMode = useMediaQuery("(prefers-color-scheme: dark)", {
        noSsr: true,
    });

    // Get theme preference with fallback to light/dark based on system setting
    const themeName = (storedTheme || (prefersDarkMode ? "dark" : "light")) as PaletteMode;

    // Create final theme object
    const theme = createTheme({
        palette: {
            primary: {
                main: '#556cd6',
            },
            secondary: {
                main: '#19857b',
            },
            error: {
                main: red.A400,
            },
            mode: themeName
        }
    })

    function toggleTheme() {
        if (themeName === "light") {
            setStoredTheme("dark");
        } else {
            setStoredTheme("light");
        }
    }

    return (
        <themeContext.Provider value={{
            mode: themeName,
            theme: theme,
            logo: themeName === "dark" ? invertedLogo : logo,
            invertedLogo: invertedLogo,
            toggle: toggleTheme
        }}>
            <ThemeProvider theme={theme}>
                {props.children}
            </ThemeProvider>
        </themeContext.Provider>
    );
}

