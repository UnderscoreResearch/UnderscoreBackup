import React from "react";
import {useTheme} from '@mui/material';

const DividerWithText = ({ children }:any) => {
    const theme = useTheme();
    return (
        <div style={{
            display: "flex",
            alignItems: "center"
        }}>
            <div style={{
                borderBottom: "2px solid darkgray",
                width: "2em"
            }} />
            <span style={{
                paddingTop: theme.spacing(0.5),
                paddingBottom: theme.spacing(0.5),
                paddingRight: theme.spacing(2),
                paddingLeft: theme.spacing(2),
                fontWeight: 500,
                whiteSpace: "nowrap",
                color: "darkgray"
            }}>{children}</span>
            <div style={{
                borderBottom: "2px solid darkgray",
                width: "100%"
            }} />
        </div>
    );
};

export default DividerWithText;