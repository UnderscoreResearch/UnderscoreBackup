import Paper from "@mui/material/Paper";
import {Stack, TextField} from "@mui/material";
import React from "react";
import Typography from "@mui/material/Typography";
import {IndividualButtonProps} from "../utils/ButtonContext";
import {MainAppSkeleton} from "./MainAppSkeleton";
import {submitPrivateKeyPassword} from "../api";
import {useApplication} from "../utils/ApplicationContext";
import {Loading} from "./Loading";

export default function UILogin() {
    const appContext = useApplication();
    const [state, setState]
        = React.useState("");

    function submitPassword() {
        appContext.busyOperation(async () => {
            await submitPrivateKeyPassword(state);
            await appContext.update(state);
        });
    }

    const acceptButton: IndividualButtonProps = {
        title: "Login",
        action: submitPassword,
        disabled: !state,
    }

    return <>
        <MainAppSkeleton
            title={"Backup password required"}
            processing={false}
            acceptButton={acceptButton}
            navigation={<></>} disallowClose={false}>
            <Stack spacing={2} style={{width: "100%"}}>
                <Paper sx={{
                    p: 2,
                }}>
                    <Typography variant="body1" component="div">
                        <p>
                            Please enter your backup password.
                        </p>
                    </Typography>
                    <TextField label="Password" variant="outlined"
                               fullWidth={true}
                               required={true}
                               id={"uiPassword"}
                               value={state}
                               error={!state}
                               type="password"
                               onKeyDown={(e) => {
                                   if (e.key === "Enter") {
                                       submitPassword();
                                   }
                               }}
                               onChange={(e) => {
                                   setState(e.target.value as string);
                               }}
                    />
                </Paper>
            </Stack>
        </MainAppSkeleton>
        <Loading open={appContext.isBusy()}/>
    </>
}