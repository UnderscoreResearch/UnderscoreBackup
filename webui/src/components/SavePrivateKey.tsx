import {
    Alert,
    FormControl,
    FormControlLabel,
    InputLabel,
    Link,
    MenuItem,
    Radio,
    RadioGroup,
    Select,
    SelectChangeEvent,
    TextField
} from "@mui/material";
import * as React from "react";
import {useEffect, useState} from "react";
import {useApplication} from "../utils/ApplicationContext";
import {getBestRegion} from "../api/service";
import base64url from "base64url";

export interface SavePrivateKeyProps {
    secretRegion?: string,
    hasKey: boolean,
    enterEmail: boolean,
    onChange: (saveSecret: boolean, email?: string, secretRegion?: string) => void
}

export default function SavePrivateKey(props: SavePrivateKeyProps) {
    const appContext = useApplication();

    const [state, setState] = useState(() => {
        const email = window.localStorage.getItem("email");
        return {
            secretRegion: props.secretRegion,
            saveSecret: undefined as boolean | undefined,
            email: email ? base64url.decode(email) : undefined,
            autoDetecting: false
        }
    });

    let privateKeyDisabled = !appContext.backendState.serviceConnected || (!props.enterEmail && !state.email) || props.hasKey;

    async function autoDetectRegion() {
        setState({
            ...state,
            autoDetecting: true
        });

        let data = await getBestRegion();
        if (data) {
            setState((oldState) => ({
                ...oldState,
                secretRegion: data as string,
                autoDetecting: false
            }));
        } else {
            setState((oldState) => ({
                ...oldState,
                autoDetecting: false
            }));
        }
    }

    useEffect(() => {
        if (!state.secretRegion) {
            autoDetectRegion();
        }
        if (privateKeyDisabled) {
            props.onChange(false, undefined, undefined);
        }
    }, [])

    useEffect(() => {
        if (state.saveSecret !== undefined) {
            props.onChange(state.saveSecret, state.email ?? undefined, state.secretRegion);
        }
    }, [state.secretRegion, state.saveSecret, state.email]);

    return <FormControl fullWidth={true}>
        <RadioGroup onChange={(e, value) => {
            setState({
                ...state,
                saveSecret: value === "true"
            })
        }}>
            <FormControlLabel disabled={privateKeyDisabled} value="true"
                              id={"saveSecret"}
                              control={<Radio
                                  style={{color: !privateKeyDisabled && state.saveSecret === undefined ? "#d32f2f" : "inherit"}}/>}
                              label="Store the encrypted private key with service in case I forget my password."/>

            <FormControl fullWidth={true}>
                <InputLabel id={"selectId"} style={{marginTop: "8px"}}>Store in region</InputLabel>
                <Select style={{marginRight: "8px", marginTop: "8px", marginBottom: "8px"}}
                        fullWidth={true}
                        value={state.secretRegion ?? ""}
                        labelId={"selectId"}
                        label={"Store in region"}
                        disabled={!appContext.backendState.serviceSourceId || !state.saveSecret}
                        onChange={(event: SelectChangeEvent) => {
                            setState({
                                ...state,
                                secretRegion: event.target.value as string,
                            });
                        }}>
                    <MenuItem key="us-west" value={"us-west"}>North America (Oregon)</MenuItem>
                    <MenuItem key="eu-central" value={"eu-central"}>Europe (Frankfurt)</MenuItem>
                    <MenuItem key="ap-southeast" value={"ap-southeast"}>Asia (Singapore)</MenuItem>
                </Select>
            </FormControl>
            <FormControlLabel disabled={privateKeyDisabled} value="false"
                              control={<Radio
                                  style={{color: !privateKeyDisabled && state.saveSecret === undefined ? "#d32f2f" : "inherit"}}/>}
                              label="Don't enable private key recovery, I will not forget my password."/>
        </RadioGroup>
        {!!window.localStorage.getItem("email") || props.enterEmail ?
            <>
                <Alert severity="warning">
                    For more information about the implications of this option see&nbsp;
                    <Link rel="noreferrer" target="_blank" underline={"hover"}
                          href={"https://underscorebackup.com/blog?source=https%3A%2F%2Fblog.underscorebackup.com%2F2023%2F02%2Fhow-does-private-key-recovery-work.html"}>
                        this documentation article.
                    </Link>
                </Alert>
                {props.enterEmail && !window.localStorage.getItem("email") &&
                    <TextField style={{marginLeft: "0px", marginRight: "-8px", marginTop: "16px"}}
                               label="Account email" variant="outlined"
                               fullWidth={true}
                               required={true}
                               helperText={!state.email ? "Enter your account email address" : undefined}
                               value={state.email ?? ""}
                               error={!state.email}
                               id={"secretEmail"}
                               type="email"
                               onChange={(e) => setState({
                                   ...state,
                                   email: e.target.value
                               })}/>
                }
            </>
            :
            <Alert severity="warning">This option is only available if you are using a
                service account and complete the setup in a single browser session!
                {!!appContext.backendState.serviceSourceId &&
                    <span> To enable go back to the beginning of the setup, disconnect from the service and reconnect!</span>
                }
            </Alert>
        }
    </FormControl>

}