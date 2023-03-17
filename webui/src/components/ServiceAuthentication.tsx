import {Button, Grid, Link, TextField} from "@mui/material";
import * as React from "react";
import {useEffect} from "react";
import {BackupState} from "../api";
import {authorizationRedirect} from "../api/utils";
import {useLocation} from "react-router-dom";
import {deleteToken, updateSource} from "../api/service";

export interface ServiceAuthenticationProps {
    backendState: BackupState,
    includeSkip: boolean,
    needSubscription: boolean,
    updatedToken: () => void
    onSkip?: () => void
}

interface ServiceAuthenticationState {
    busy: boolean,
    sourceName: string
}

export default function ServiceAuthentication(props: ServiceAuthenticationProps) {
    const location = useLocation();

    const [state, setState] = React.useState({
        busy: false,
        sourceName: props.backendState.sourceName ?? ""
    } as ServiceAuthenticationState);

    function authorizeRedirect() {
        setState({
            ...state,
            busy: true
        });
        try {
            authorizationRedirect(props.backendState.siteUrl, location.pathname,
                `name=${encodeURIComponent(props.backendState.sourceName)}`,
                `sourceName=${encodeURIComponent(props.backendState.sourceName)}`);
        } finally {
            setState((oldState) => ({
                ...oldState,
                busy: false
            }));
        }
    }

    useEffect(() => {
        setState({
            ...state,
            sourceName: props.backendState.sourceName
        })
    }, [props.backendState.sourceName]);

    async function updateName() {
        setState({
            ...state,
            busy: true
        });
        try {
            await updateSource(state.sourceName);
            props.updatedToken();
        } finally {
            setState({
                ...state,
                busy: false
            });
        }
    }

    async function disconnect() {
        setState({
            ...state,
            busy: true
        });
        try {
            await deleteToken();
            props.updatedToken();
        } finally {
            setState((oldState) => ({
                ...oldState,
                busy: false
            }));
        }
    }

    function ConnectionButton() {
        return <>
            {props.backendState.serviceConnected ?
                (props.needSubscription && !props.backendState.activeSubscription ?
                        <Button variant="contained" fullWidth={true} disabled={state.busy} size="large"
                                onClick={authorizeRedirect} id="subscribe">
                            <>Add Subscription</>
                        </Button>
                        :
                        <Button variant="contained" fullWidth={true} disabled={state.busy} size="large"
                                onClick={disconnect} id="disconnect">
                            <>Disconnect</>
                        </Button>
                )
                :
                <Button variant="contained" fullWidth={true} disabled={state.busy} size="large"
                        onClick={authorizeRedirect} id="connect">
                    <>Connect</>
                </Button>
            }
        </>;
    }

    if (props.includeSkip) {
        return <>
            <Grid item md={8} sm={6} xs={12}>
                <Link id="skipService" rel="noreferrer" href={"."}
                      underline={"hover"}
                      onClick={(e) => {
                          e.preventDefault();
                          if (props.onSkip)
                              props.onSkip();
                      }}>
                    {props.backendState.serviceConnected ? "Continue" : "Skip"}
                </Link>
            </Grid>
            <Grid item md={4} sm={6} xs={12} textAlign={"center"}>
                <ConnectionButton/>
            </Grid>
        </>
    } else {
        return <>
            <Grid item md={4} sm={3} xs={1}>
            </Grid>
            <Grid item md={4} sm={6} xs={10} textAlign={"center"}>
                <ConnectionButton/>
            </Grid>
            <Grid item md={4} sm={3} xs={1}>
            </Grid>
            {props.backendState.serviceConnected && !props.needSubscription &&
                <>
                    <Grid item xs={12} textAlign={"center"}>
                        <Link rel="noreferrer" target="_blank"
                              href={`${props.backendState.siteUrl}/dashboard`} underline={"hover"}>Dashboard</Link>
                        &nbsp;
                        &nbsp;
                        &nbsp;
                        <Link rel="noreferrer" target="_blank" underline={"hover"}
                              href={`${props.backendState.siteUrl}/settings/general`}>Account
                            Settings</Link>
                    </Grid>
                    <Grid item md={9} xs={12}>
                        <TextField
                            fullWidth={true}
                            multiline
                            value={state.sourceName}
                            onChange={e => setState({
                                ...state,
                                sourceName: e.target.value
                            })}
                        />
                    </Grid>
                    <Grid item md={3} xs={12}>
                        <Button fullWidth={true}
                                disabled={state.busy || state.sourceName === props.backendState.sourceName}
                                variant="contained" id="newSource" onClick={() => updateName()}>
                            Change name
                        </Button>
                    </Grid>
                </>
            }
        </>

    }
}