import {Button, Grid} from "@mui/material";
import * as React from "react";
import {useApplication} from "../../utils/ApplicationContext";

export function NextButton(props: { onClick: () => void, disabled?: boolean, force?: boolean }) {
    const appContext = useApplication();
    return <Grid container spacing={2} alignItems={"center"} style={{marginTop: "8px"}}>
        <Grid item md={8} sm={6} xs={12}/>
        <Grid item md={4} sm={6} xs={12} textAlign={"center"}>
            <Button variant="contained"
                    fullWidth={true}
                    disabled={props.disabled && !appContext.isBusy()}
                    onClick={props.onClick}
                    color={props.force ? "error" : "primary"}
                    size="large" id="next">
                {props.force ? "Force" : "Next"}
            </Button>
        </Grid>
    </Grid>
}
