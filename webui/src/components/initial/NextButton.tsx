import {Button, Grid} from "@mui/material";
import * as React from "react";

export function NextButton(props: { onClick: () => void, disabled?: boolean }) {
    return <Grid container spacing={2} alignItems={"center"} style={{marginTop: "8px"}}>
        <Grid item md={8} sm={6} xs={12}/>
        <Grid item md={4} sm={6} xs={12} textAlign={"center"}>
            <Button variant="contained"
                    fullWidth={true}
                    disabled={props.disabled}
                    onClick={props.onClick}
                    size="large" id="next">
                Next
            </Button>
        </Grid>
    </Grid>
}
