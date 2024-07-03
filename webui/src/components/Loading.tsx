import {Backdrop, CircularProgress} from "@mui/material";
import React from "react";

export function Loading(props: { open: boolean }) {
    return <Backdrop
        sx={{color: '#fff', zIndex: 10000}}
        id={"loading"}
        open={props.open}>
        <CircularProgress color="inherit" size={"10em"}/>
    </Backdrop>
}
