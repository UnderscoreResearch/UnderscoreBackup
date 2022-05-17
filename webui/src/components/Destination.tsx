import Paper from "@mui/material/Paper";
import * as React from "react";
import {Fragment} from "react";
import {
    Autocomplete,
    Button,
    FormControl,
    Grid,
    InputLabel,
    MenuItem,
    Select,
    SelectChangeEvent,
    Tab,
    Tabs,
    TextField
} from "@mui/material";
import Box from "@mui/material/Box";
import {BackupDestination, BackupLimits, GetAuthEndpoint, PropertyMap} from "../api";
import DividerWithText from "../3rdparty/react-js-cron-mui/components/DividerWithText";
import SpeedLimit from "./SpeedLimit";

const DROPBOX_CLIENT_ID = 'tlt1aw0jc8wlcox';
const temporaryStorage = window.sessionStorage;

interface TabPanelProps {
    children?: React.ReactNode;
    index: number;
    value: number;
}

export interface DestinationProps {
    id: string,
    destination: BackupDestination;
    destinationUpdated: (valid: boolean, val: BackupDestination) => void;
    manifestDestination?: boolean
}

export interface TabState {
    destination: BackupDestination
    valid: boolean
}

function TabPanel(props: TabPanelProps) {
    const {children, value, index, ...other} = props;

    return (
        <div
            role="tabpanel"
            hidden={value !== index}
            {...other}
        >
            {value === index && (
                <Box
                    component="div"
                    sx={{
                        '& .MuiTextField-root': {m: 1},
                    }}
                    style={{marginTop: 4}}
                >
                    {children}
                </Box>
            )}
        </div>
    );
}

const s3Regions = [
    "af-south-1",
    "ap-east-1",
    "ap-northeast-1",
    "ap-northeast-2",
    "ap-northeast-3",
    "ap-south-1",
    "ap-southeast-1",
    "ap-southeast-2",
    "ap-southeast-3",
    "ca-central-1",
    "cn-north-1",
    "cn-northwest-1",
    "eu-central-1",
    "eu-north-1",
    "eu-south-1",
    "eu-west-1",
    "eu-west-2",
    "eu-west-3",
    "me-south-1",
    "sa-east-1",
    "us-east-1",
    "us-east-2",
    "us-gov-east-1",
    "us-gov-west-1",
    "us-west-1",
    "us-west-2"
];

interface SharedState {
    encryption: string,
    errorCorrection: string,
    limits: BackupLimits | undefined
}

function SharedProperties(props: {
    manifestDestination?: boolean,
    state: SharedState,
    onChange: (newState: SharedState) => void
}) {

    const [state, setState] = React.useState({
        encryption: props.state.encryption,
        errorCorrection: props.state.errorCorrection,
        limits: props.state.limits ? props.state.limits : {}
    } as {
        encryption: string,
        errorCorrection: string,
        limits: BackupLimits
    });

    function updateLimitChange(newState : {
        encryption: string,
        errorCorrection: string,
        limits: BackupLimits
    }) {
        const sendState : {
            encryption: string,
            errorCorrection: string,
            limits: BackupLimits | undefined
        } = {...newState}
        if (!newState.limits.maximumDownloadBytesPerSecond && !newState.limits.maximumUploadBytesPerSecond) {
            sendState.limits = undefined;
        }
        props.onChange(sendState);
    }

    return <Fragment>
        <Grid item xs={12}>
            <DividerWithText>Storage Options</DividerWithText>
        </Grid>
        <Grid item xs={6}>
            <FormControl fullWidth={true} style={{margin: "8px"}}>
                <InputLabel id="encryption-id-label">Encryption</InputLabel>
                <Select
                    labelId="encryption-id-label"
                    value={state.encryption}
                    label="Encryption"
                    onChange={(event: SelectChangeEvent) => {
                        const newState = {
                            ...state,
                            encryption: event.target.value as string,
                        }
                        setState(newState);
                        props.onChange(newState);
                    }}>
                    <MenuItem value={"NONE"}>None</MenuItem>
                    <MenuItem value={"AES256"}>AES 256</MenuItem>
                </Select>
            </FormControl>
        </Grid>
        {!props.manifestDestination &&
            <Grid item xs={6}>
                <FormControl fullWidth={true} style={{margin: "8px"}}>
                    <InputLabel id="errorcorrection-id-label">Error Correction</InputLabel>
                    <Select
                        labelId="errorcorrection-id-label"
                        value={state.errorCorrection}
                        label="Error Correction"
                        onChange={(event: SelectChangeEvent) => {
                            const newState = {
                                ...state,
                                errorCorrection: event.target.value as string,
                            }
                            setState(newState);
                            props.onChange(newState);
                        }}>
                        <MenuItem value={"NONE"}>None</MenuItem>
                        <MenuItem value={"RS"}>Reed Solomon</MenuItem>
                    </Select>
                </FormControl>
            </Grid>
        }
        <Grid item xs={12}>
            <DividerWithText>Limits</DividerWithText>
        </Grid>
        <Grid item xs={6}>
            <SpeedLimit
                speed={state.limits.maximumUploadBytesPerSecond}
                onChange={(newSpeed) => {
                    const newState = {
                        ...state,
                        limits: {
                            ...state.limits,
                            maximumUploadBytesPerSecond: newSpeed
                        }
                    }
                    setState(newState);
                    updateLimitChange(newState);
                }} title={"Maximum upload speed"}/>
        </Grid>
        <Grid item xs={6}>
            <SpeedLimit
                speed={state.limits.maximumDownloadBytesPerSecond}
                onChange={(newSpeed) => {
                    const newState = {
                        ...state,
                        limits: {
                            ...state.limits,
                            maximumDownloadBytesPerSecond: newSpeed
                        }
                    }
                    setState(newState);
                    updateLimitChange(newState);
                }} title={"Maximum download speed"}/>
        </Grid>
    </Fragment>
}

function LocalFileDestination(props: DestinationProps) {
    const [state, setState] = React.useState({
        endpointUri: props.destination.endpointUri ? props.destination.endpointUri : "" as string,
        encryption: props.destination.encryption ? props.destination.encryption : "AES256" as string,
        errorCorrection: props.destination.errorCorrection ? props.destination.errorCorrection : "NONE" as string,
        limits: props.destination.limits
    });

    function updateState(newState: {
        endpointUri: string
        encryption: string,
        errorCorrection: string,
        limits: BackupLimits | undefined
    }) {
        if (props.destinationUpdated) {
            props.destinationUpdated(!(!newState.endpointUri), {
                type: "FILE",
                endpointUri: newState.endpointUri,
                encryption: newState.encryption,
                errorCorrection: newState.errorCorrection,
                limits: newState.limits
            });
        }
        setState(newState);
    }

    return <Grid container spacing={2}>
        <Grid item xs={12}>
            <TextField label="Local Directory" variant="outlined"
                       required={true}
                       fullWidth={true}
                       value={state.endpointUri}
                       error={!state.endpointUri}
                       onChange={(e) => updateState({
                           ...state,
                           endpointUri: e.target.value
                       })}/>
        </Grid>
        <SharedProperties manifestDestination={props.manifestDestination}
                          state={state} onChange={(newSate => updateState({
            ...state,
            ...newSate
        }))}/>
    </Grid>;
}

function loadDropbox(callback: () => void) {
    if (document.getElementById("dropboxscript")) {
        callback();
    } else {
        let script = document.createElement('script');
        script.onload = function () {
            callback();
        };
        script.src = 'https://cdnjs.cloudflare.com/ajax/libs/dropbox.js/10.28.0/Dropbox-sdk.min.js';
        script.id = "dropboxscript";
        document.head.appendChild(script);
    }
}

function DropboxDestination(props: DestinationProps) {
    const [state, setState] = React.useState({
        endpointUri: props.destination.endpointUri ? props.destination.endpointUri : "",
        accessToken: props.destination.principal ? props.destination.principal : "",
        refreshToken: props.destination.credential ? props.destination.credential : "",
        encryption: props.destination.encryption ? props.destination.encryption : "AES256",
        errorCorrection: props.destination.errorCorrection ? props.destination.errorCorrection : "NONE",
        limits: props.destination.limits
    });

    let lastDestination = props.destination;
    lastDestination.type = "DROPBOX";

    function updateState(newState: {
        endpointUri: string,
        accessToken: string,
        refreshToken: string,
        encryption: string,
        errorCorrection: string,
        limits: BackupLimits | undefined
    }) {
        if (props.destinationUpdated) {
            lastDestination = {
                type: "DROPBOX",
                endpointUri: newState.endpointUri,
                principal: newState.accessToken,
                credential: newState.refreshToken,
                encryption: newState.encryption,
                errorCorrection: newState.errorCorrection,
                limits: newState.limits
            };
            props.destinationUpdated(!(!newState.accessToken || !newState.refreshToken), lastDestination);
        }
        setState(newState);
    }

    async function fetchAccessToken(codeVerified: string, code: string) {
        let redirectUri = await GetAuthEndpoint();
        const dbxAuth = new Dropbox.DropboxAuth({
            clientId: DROPBOX_CLIENT_ID,
        });
        dbxAuth.setCodeVerifier(codeVerifier);
        dbxAuth.getAccessTokenFromCode(redirectUri, code)
            .then((response) => {
                updateState({
                    ...state,
                    accessToken: response.result.access_token,
                    refreshToken: response.result.refresh_token
                });
            })
            .catch((error) => {
                console.error(error)
            });
    }

    let codeVerifier = temporaryStorage.getItem("codeVerifier") as string;
    let codeLocation = window.location.href.indexOf("code=");
    if (codeVerifier && codeLocation) {
        let code = decodeURIComponent(window.location.href.substring(codeLocation + 5));
        loadDropbox(() => fetchAccessToken(codeVerifier, code))
        temporaryStorage.clear();
    }

    async function dropboxAuthenticateAsync() {
        let redirectUri = await GetAuthEndpoint();
        const dbxAuth = new Dropbox.DropboxAuth({
            clientId: DROPBOX_CLIENT_ID,
        });

        dbxAuth.getAuthenticationUrl(redirectUri, undefined, 'code', 'offline', undefined, undefined, true)
            .then(authUrl => {
                temporaryStorage.clear();
                temporaryStorage.setItem("codeVerifier", dbxAuth.codeVerifier);
                temporaryStorage.setItem("destination", JSON.stringify(lastDestination));
                temporaryStorage.setItem("destinationId", props.id);
                window.location.href = authUrl;
            })
            .catch((error) => console.error(error));
    }

    function launchDropboxAuthentication() {
        loadDropbox(() => dropboxAuthenticateAsync());
    }

    return <Grid container spacing={2}>
        <Grid item xs={12}>
            {state.accessToken ?
                <Button variant="contained" style={{margin: "auto", display: "block", marginTop: "8px"}}
                        onClick={launchDropboxAuthentication}>Re Authorize</Button>
                :
                <Button variant="contained" style={{margin: "auto", display: "block", marginTop: "8px"}}
                        onClick={launchDropboxAuthentication}>Authorize</Button>
            }
        </Grid>
        <Grid item xs={12}>
            <TextField label="Subfolder" variant="outlined"
                       fullWidth={true}
                       value={state.endpointUri}
                       onChange={(e) => updateState({
                           ...state,
                           endpointUri: e.target.value
                       })}/>
        </Grid>
        <SharedProperties manifestDestination={props.manifestDestination}
                          state={state} onChange={(newSate => updateState({
            ...state,
            ...newSate
        }))}/>
    </Grid>;
}

function WindowsShareDestination(props: DestinationProps) {
    const [state, setState] = React.useState({
        endpointUri: props.destination.endpointUri ? props.destination.endpointUri : "\\\\",
        username: props.destination.principal ? props.destination.principal : "",
        password: props.destination.credential ? props.destination.credential : "",
        domain: props.destination.properties && props.destination.properties["domain"] ? props.destination.properties["domain"] : "WORKSPACE",
        encryption: props.destination.encryption ? props.destination.encryption : "AES256",
        errorCorrection: props.destination.errorCorrection ? props.destination.errorCorrection : "NONE",
        limits: props.destination.limits
    });

    function updateState(newState: {
        endpointUri: string,
        username: string,
        password: string,
        domain: string,
        encryption: string,
        errorCorrection: string,
        limits: BackupLimits | undefined
    }) {
        if (props.destinationUpdated) {
            const valid = !(!(newState.endpointUri && newState.username && newState.password));
            var properties = {} as PropertyMap;
            if (newState.domain) {
                properties["domain"] = newState.domain;
            }
            props.destinationUpdated(valid, {
                type: "SMB",
                endpointUri: newState.endpointUri,
                principal: newState.username,
                credential: newState.password,
                encryption: newState.encryption,
                errorCorrection: newState.errorCorrection,
                properties: properties,
                limits: newState.limits
            });
        }

        setState(newState);
    }

    return <Grid container spacing={2}>
        <Grid item xs={12}>
            <TextField label="Share Path" variant="outlined"
                       required={true}
                       fullWidth={true}
                       value={state.endpointUri}
                       error={!state.endpointUri}
                       onChange={(e) => updateState({
                           ...state,
                           endpointUri: e.target.value
                       })}/>
        </Grid>
        <Grid item xs={12}>
            <DividerWithText>Authentication</DividerWithText>
        </Grid>
        <Grid item xs={12}>
            <TextField label="Username" variant="outlined"
                       required={true}
                       fullWidth={true}
                       value={state.username}
                       error={!state.username}
                       onChange={(e) => updateState({
                           ...state,
                           username: e.target.value
                       })}/>
        </Grid>
        <Grid item xs={12}>
            <TextField label="Password" variant="outlined"
                       required={true}
                       fullWidth={true}
                       value={state.password}
                       error={!state.password}
                       type="password"
                       onChange={(e) => updateState({
                           ...state,
                           password: e.target.value
                       })}/>
        </Grid>
        <Grid item xs={12}>
            <TextField label="Domain" variant="outlined"
                       fullWidth={true}
                       value={state.domain}
                       onChange={(e) => updateState({
                           ...state,
                           domain: e.target.value
                       })}/>
        </Grid>
        <SharedProperties manifestDestination={props.manifestDestination}
                          state={state} onChange={(newSate => updateState({
            ...state,
            ...newSate
        }))}/>
    </Grid>
}


function S3Destination(props: DestinationProps) {
    const [state, setState] = React.useState({
        endpointUri: props.destination.endpointUri ? props.destination.endpointUri : "s3://",
        accessKeyId: props.destination.principal ? props.destination.principal : "",
        secretAccessKey: props.destination.credential ? props.destination.credential : "",
        encryption: props.destination.encryption ? props.destination.encryption : "AES256" as string,
        errorCorrection: props.destination.errorCorrection ? props.destination.errorCorrection : "NONE" as string,
        region: props.destination.properties && props.destination.properties["region"] ? props.destination.properties["region"] : "us-east-1",
        apiEndpoint: props.destination.properties && props.destination.properties["apiEndpoint"] ? props.destination.properties["apiEndpoint"] : "",
        limits: props.destination.limits
    });

    function updateState(newState: {
        endpointUri: string,
        accessKeyId: string,
        secretAccessKey: string,
        encryption: string,
        errorCorrection: string,
        region: string,
        apiEndpoint: string,
        limits: BackupLimits | undefined
    }) {
        if (props.destinationUpdated) {
            const valid = !(!(newState.endpointUri && newState.accessKeyId && newState.secretAccessKey && newState.region));
            const properties = {} as PropertyMap;
            if (newState.region) {
                properties["region"] = newState.region;
            }
            if (newState.apiEndpoint) {
                properties["apiEndpoint"] = newState.apiEndpoint;
            }
            props.destinationUpdated(valid, {
                type: "S3",
                endpointUri: newState.endpointUri,
                principal: newState.accessKeyId,
                credential: newState.secretAccessKey,
                encryption: newState.encryption,
                errorCorrection: newState.errorCorrection,
                properties: properties,
                limits: newState.limits
            });
        }

        setState(newState);
    }

    function updateRegion(newRegion: string) {
        if (newRegion !== state.region) {
            updateState({
                ...state,
                region: newRegion
            });
        }
    }

    return <Grid container spacing={2}>
        <Grid item xs={12}>
            <TextField label="S3 URL" variant="outlined"
                       required={true}
                       fullWidth={true}
                       value={state.endpointUri}
                       error={!state.endpointUri}
                       onChange={(e) => updateState({
                           ...state,
                           endpointUri: e.target.value
                       })}/>
        </Grid>
        <Grid item xs={12}>
            <DividerWithText>Authentication</DividerWithText>
        </Grid>
        <Grid item xs={12}>
            <TextField label="AWS Access Key Id" variant="outlined"
                       required={true}
                       fullWidth={true}
                       value={state.accessKeyId}
                       error={!state.accessKeyId}
                       onChange={(e) => updateState({
                           ...state,
                           accessKeyId: e.target.value
                       })}/>
        </Grid>
        <Grid item xs={12}>
            <TextField label="AWS Secret Access Key" variant="outlined"
                       required={true}
                       fullWidth={true}
                       value={state.secretAccessKey}
                       error={!state.secretAccessKey}
                       type="password"
                       onChange={(e) => updateState({
                           ...state,
                           secretAccessKey: e.target.value
                       })}/>
        </Grid>
        <Grid item xs={12}>
            <DividerWithText>Endpoint</DividerWithText>
        </Grid>
        <Grid item xs={12}>
            <Autocomplete
                disablePortal
                options={s3Regions}
                value={state.region}
                onInputChange={(event, newInputVale) => {
                    updateRegion(newInputVale)
                }
                }
                renderInput={(params) =>
                    <TextField {...params} label="region" variant="outlined"
                               required={true}
                               fullWidth={true}
                               error={!state.region}/>}
            />
        </Grid>
        <Grid item xs={12}>
            <TextField label="Alternate API Endpoint" variant="outlined"
                       fullWidth={true}
                       value={state.apiEndpoint}
                       onChange={(e) => updateState({
                           ...state,
                           apiEndpoint: e.target.value
                       })}/>
        </Grid>
        <SharedProperties manifestDestination={props.manifestDestination}
                          state={state} onChange={(newSate => updateState({
            ...state,
            ...newSate
        }))}/>
    </Grid>
}

export default function Destination(props: DestinationProps) {

    const [state, setState] = React.useState(() => {
        var destinationByTab = new Map<number, TabState>();

        var destination = props.destination;

        var defaultType = 0;
        if (destination !== undefined) {
            switch (destination.type) {
                case "SMB":
                    defaultType = 1;
                    break;
                case "S3":
                    defaultType = 2;
                    break;
                case "DROPBOX":
                    defaultType = 3;
                    break;
            }

            destinationByTab.set(defaultType, {
                destination: destination,
                valid: true
            });
        }
        return {
            type: defaultType,
            destinationsByTab: destinationByTab
        }
    });

    const handleChange = (event: React.SyntheticEvent, newValue: number) => {
        if (props.destinationUpdated) {
            const currentType = state.destinationsByTab.get(newValue);
            if (currentType) {
                props.destinationUpdated(currentType.valid, currentType.destination);
            } else {
                props.destinationUpdated(false, {
                    type: "",
                    endpointUri: ""
                });
            }
        }
        setState({
            ...state,
            type: newValue
        });
    };

    function getTabState(index: number): TabState {
        const ret = state.destinationsByTab.get(index);
        if (!ret) {
            return {
                valid: false,
                destination: {
                    type: "",
                    endpointUri: ""
                }
            }
        }
        return ret;
    }

    function destinationUpdated(type: number, valid: boolean, dest: BackupDestination) {
        var newState = {
            destinationsByTab: new Map<number, TabState>(state.destinationsByTab),
            type: state.type
        };
        newState.destinationsByTab.set(type, {
            destination: dest,
            valid: valid
        });
        setState(newState);
        props.destinationUpdated(valid, dest);
    }

    return <Paper sx={{p: 2}}>
        <DividerWithText><span style={{fontSize: 22}}>Destination Type</span></DividerWithText>
        <Tabs value={state.type} onChange={handleChange}>
            <Tab label="Local Directory"/>
            <Tab label="Windows Share"/>
            <Tab label="S3"/>
            <Tab label="Dropbox"/>
        </Tabs>

        <TabPanel value={state.type} index={0}>
            <LocalFileDestination
                manifestDestination={props.manifestDestination}
                destination={getTabState(0).destination}
                id={props.id}
                destinationUpdated={(valid, dest) => destinationUpdated(0, valid, dest)}/>
        </TabPanel>

        <TabPanel value={state.type} index={1}>
            <WindowsShareDestination
                manifestDestination={props.manifestDestination}
                destination={getTabState(1).destination}
                id={props.id}
                destinationUpdated={(valid, dest) => destinationUpdated(1, valid, dest)}/>
        </TabPanel>

        <TabPanel value={state.type} index={2}>
            <S3Destination
                manifestDestination={props.manifestDestination}
                destination={getTabState(2).destination}
                id={props.id}
                destinationUpdated={(valid, dest) => destinationUpdated(2, valid, dest)}/>
        </TabPanel>
        <TabPanel value={state.type} index={3}>
            <DropboxDestination
                manifestDestination={props.manifestDestination}
                destination={getTabState(3).destination}
                id={props.id}
                destinationUpdated={(valid, dest) => destinationUpdated(3, valid, dest)}/>
        </TabPanel>
    </Paper>
}