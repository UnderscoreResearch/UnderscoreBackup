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
import {BackupDestination, BackupLimits, BackupTimespan, GetAuthEndpoint, PropertyMap} from "../api";
import DividerWithText from "../3rdparty/react-js-cron-mui/components/DividerWithText";
import SpeedLimit from "./SpeedLimit";
import Timespan from "./Timespan";

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

export interface S3DestinationProps extends DestinationProps {
    accessKeyLabel: string,
    secretKeyLabel: string,
    console: string,
    regionList?: string[],
    createState: (props: DestinationProps) => S3DestinationState,
    createDestination: (state: S3DestinationState) => BackupDestination
}

interface S3DestinationState {
    bucket: string,
    prefix: string,
    accessKeyId: string,
    secretAccessKey: string,
    maxRetention: BackupTimespan | undefined,
    encryption: string,
    errorCorrection: string,
    region: string,
    apiEndpoint?: string,
    limits: BackupLimits | undefined
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

const wasabiRegions = [
    "ap-northeast-1",
    "ap-northeast-2",
    "ap-southeast-1",
    "ap-southeast-2",
    "ca-central-1",
    "eu-central-1",
    "eu-central-2",
    "eu-west-1",
    "eu-west-2",
    "us-central-1",
    "us-east-1",
    "us-east-2",
    "us-west-1"
];

interface SharedState {
    encryption: string,
    errorCorrection: string,
    maxRetention: BackupTimespan | undefined,
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
        maxRetention: props.state.maxRetention,
        limits: props.state.limits ? props.state.limits : {}
    } as {
        encryption: string,
        errorCorrection: string,
        maxRetention: BackupTimespan | undefined,
        limits: BackupLimits
    });

    function updateLimitChange(newState: {
        encryption: string,
        errorCorrection: string,
        limits: BackupLimits
    }) {
        const sendState: {
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
            <DividerWithText>Storage options</DividerWithText>
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
        <Grid item xs={12}>
            <DividerWithText>Maximum Retention</DividerWithText>
        </Grid>
        <Grid item xs={12}>
            <Timespan timespan={state.maxRetention ? state.maxRetention : {unit: "FOREVER"}}
                      onChange={(newTimespan) => {
                          const sendState = {
                              ...state,
                              maxRetention: newTimespan
                          };
                          props.onChange(sendState);
                      }}
                      title={"Re-upload data to destination after "}/>
        </Grid>
    </Fragment>
}

function LocalFileDestination(props: DestinationProps) {
    const [state, setState] = React.useState({
        endpointUri: props.destination.endpointUri ? props.destination.endpointUri : "" as string,
        encryption: props.destination.encryption ? props.destination.encryption : "AES256" as string,
        errorCorrection: props.destination.errorCorrection ? props.destination.errorCorrection : "NONE" as string,
        maxRetention: props.destination.maxRetention,
        limits: props.destination.limits
    });

    function updateState(newState: {
        endpointUri: string
        encryption: string,
        maxRetention: BackupTimespan | undefined,
        errorCorrection: string,
        limits: BackupLimits | undefined
    }) {
        if (props.destinationUpdated) {
            props.destinationUpdated(!(!newState.endpointUri), {
                type: "FILE",
                endpointUri: newState.endpointUri,
                encryption: newState.encryption,
                maxRetention: newState.maxRetention,
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
                       id={"localFileText"}
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
        maxRetention: props.destination.maxRetention,
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
        maxRetention: BackupTimespan | undefined,
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
                maxRetention: newState.maxRetention,
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
        maxRetention: props.destination.maxRetention,
        encryption: props.destination.encryption ? props.destination.encryption : "AES256",
        errorCorrection: props.destination.errorCorrection ? props.destination.errorCorrection : "NONE",
        limits: props.destination.limits
    });

    function updateState(newState: {
        endpointUri: string,
        username: string,
        password: string,
        domain: string,
        maxRetention: BackupTimespan | undefined,
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
                maxRetention: newState.maxRetention,
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

function BaseS3Destination(props: S3DestinationProps) {
    const [state, setState] = React.useState(props.createState(props));

    function updateState(newState: S3DestinationState) {
        if (props.destinationUpdated) {
            const destination = props.createDestination(newState);
            const valid = !(!(destination.endpointUri && destination.principal && destination.credential && destination.properties && (destination.properties["region"] || destination.properties["apiEndpoint"])));
            props.destinationUpdated(valid, destination);
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
        <Grid item xs={5}>
            <TextField label="Bucket" variant="outlined"
                       required={true}
                       fullWidth={true}
                       value={state.bucket}
                       error={!state.bucket}
                       onChange={(e) => updateState({
                           ...state,
                           bucket: e.target.value
                       })}/>
        </Grid>
        <Grid item xs={5}>
            <TextField label="Prefix" variant="outlined"
                       required={true}
                       fullWidth={true}
                       value={state.prefix}
                       error={!state.prefix}
                       onChange={(e) => updateState({
                           ...state,
                           prefix: e.target.value
                       })}/>
        </Grid>
        <Grid item xs={2}>
            <div style={{height: "100%", width: "100%", display: "flex", alignItems: "center"}}>
            <Button fullWidth={true} onClick={e => window.open(props.console)}>Console</Button>
            </div>
        </Grid>
        <Grid item xs={12}>
            <DividerWithText>Authentication</DividerWithText>
        </Grid>
        <Grid item xs={12}>
            <TextField label={props.accessKeyLabel} variant="outlined"
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
            <TextField label={props.secretKeyLabel} variant="outlined"
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
        {
            props.regionList &&
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
        }
        {
            state.apiEndpoint !== undefined &&
            <Grid item xs={12}>
                <TextField label={props.regionList ? "Alternate API Endpoint" : "API Endpoint"} variant="outlined"
                           required={!props.regionList}
                           fullWidth={true}
                           value={state.apiEndpoint}
                           error={!state.apiEndpoint && !props.regionList}
                           onChange={(e) => updateState({
                               ...state,
                               apiEndpoint: e.target.value
                           })}/>
            </Grid>
        }
        <SharedProperties manifestDestination={props.manifestDestination}
                          state={state} onChange={(newSate => updateState({
            ...state,
            ...newSate
        }))}/>
    </Grid>
}

function expandProtocolIfMissing(endpoint: string) : string {
    if (endpoint.indexOf("://") < 0) {
        return "https://" + endpoint;
    }
    return endpoint;
}

function decodeS3Bucket(endpointUri?: string) : string {
    if (endpointUri) {
        const match = endpointUri.match(/^s3\:\/\/([^\/]+)/);
        if (match && match && match[1]) {
            return match[1];
        }
    }
    return ""
}

function decodeS3Prefix(endpointUri?: string) : string {
    if (endpointUri) {
        const match = endpointUri.match(/^s3\:\/\/[^\/]+\/(.*)$/);
        if (match && match[1]) {
            return match[1];
        }
    }
    return ""
}

function createS3EndpointUri(bucket: string, prefix: string) : string {
    if (prefix.startsWith("/"))
        prefix = prefix.substring(1);
    return `s3://${bucket}/${prefix}`;
}

function createS3Destination(newState: S3DestinationState, properties?: PropertyMap) : BackupDestination {
    return {
        type: "S3",
        endpointUri: createS3EndpointUri(newState.bucket, newState.prefix),
        principal: newState.accessKeyId,
        credential: newState.secretAccessKey,
        encryption: newState.encryption,
        maxRetention: newState.maxRetention,
        errorCorrection: newState.errorCorrection,
        properties: properties,
        limits: newState.limits
    }
}

function createS3State(props: DestinationProps, region: string, apiEndpoint?: string) {
    return {
        bucket: decodeS3Bucket(props.destination.endpointUri),
        prefix: decodeS3Prefix(props.destination.endpointUri),
        accessKeyId: props.destination.principal ? props.destination.principal : "",
        secretAccessKey: props.destination.credential ? props.destination.credential : "",
        maxRetention: props.destination.maxRetention,
        encryption: props.destination.encryption ? props.destination.encryption : "AES256" as string,
        errorCorrection: props.destination.errorCorrection ? props.destination.errorCorrection : "NONE" as string,
        region: region,
        apiEndpoint: apiEndpoint,
        limits: props.destination.limits
    }
}

export function S3Destination(props: DestinationProps) {
    return BaseS3Destination({
        ...props,
        accessKeyLabel: "AWS Access Key Id",
        secretKeyLabel: "AWS Secret Access Key",
        regionList: s3Regions,
        console: "https://console.aws.amazon.com/console/home",
        createState: (props: DestinationProps) => createS3State(props,
            props.destination.properties && props.destination.properties["region"] ? props.destination.properties["region"] : "us-east-1",
            props.destination.properties && props.destination.properties["apiEndpoint"] ? props.destination.properties["apiEndpoint"] : ""),
        createDestination: (newState: S3DestinationState) => {
            const properties = {} as PropertyMap;
            if (newState.region) {
                properties["region"] = newState.region;
            }
            if (newState.apiEndpoint) {
                properties["apiEndpoint"] = expandProtocolIfMissing(newState.apiEndpoint);
            }
            return createS3Destination(newState, properties);
        }
    });
}

export function WasabiDestination(props: DestinationProps) {
    return BaseS3Destination({
        ...props,
        accessKeyLabel: "Access Key",
        secretKeyLabel: "Secret Key",
        console: "https://console.wasabisys.com/",
        regionList: wasabiRegions,
        createState: (props: DestinationProps) => createS3State(props,
            props.destination.properties && props.destination.properties["region"] ? props.destination.properties["region"] : "us-east-1",
            undefined),
        createDestination: (newState: S3DestinationState) => {
            const properties = {} as PropertyMap;
            if (newState.region) {
                properties["region"] = newState.region;
                properties["apiEndpoint"] = `https://s3.${newState.region}.wasabisys.com`;
            }
            return createS3Destination(newState, properties);
        }
    });
}

export function BackblazeDestination(props: DestinationProps) {
    return BaseS3Destination({
        ...props,
        accessKeyLabel: "Key ID",
        secretKeyLabel: "Application Key",
        console: "https://secure.backblaze.com/b2_buckets.htm",
        createState: (props: DestinationProps) => createS3State(props,
            "",
            props.destination.properties && props.destination.properties["apiEndpoint"] ? props.destination.properties["apiEndpoint"] : ""),
        createDestination: (newState: S3DestinationState) => {
            const properties = {} as PropertyMap;
            if (newState.apiEndpoint) {
                properties["apiEndpoint"] = expandProtocolIfMissing(newState.apiEndpoint);
            }
            return createS3Destination(newState, properties);
        }
    });
}

export function IDriveDestination(props: DestinationProps) {
    return BaseS3Destination({
        ...props,
        accessKeyLabel: "Access key",
        secretKeyLabel: "Secret Key",
        console: "https://app.idrivee2.com/dashboard",
        createState: (props: DestinationProps) => createS3State(props,
            "",
            props.destination.properties && props.destination.properties["apiEndpoint"] ? props.destination.properties["apiEndpoint"] : ""),
        createDestination: (newState: S3DestinationState) => {
            const properties = {} as PropertyMap;
            if (newState.apiEndpoint) {
                properties["apiEndpoint"] = expandProtocolIfMissing(newState.apiEndpoint);
            }
            return createS3Destination(newState, properties);
        }
    });
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
                    const apiEndpoint = destination.properties && destination.properties["apiEndpoint"] ? destination.properties["apiEndpoint"] : "";
                    if (apiEndpoint.endsWith(".backblazeb2.com")) {
                        defaultType = 3;
                    } else if (apiEndpoint.endsWith(".wasabisys.com")){
                        defaultType = 4;
                    } else if (apiEndpoint.match(/\.idrivee2-\d\.com$/)){
                        defaultType = 5;
                    } else {
                        defaultType = 2;
                    }
                    break;
                case "DROPBOX":
                    defaultType = 6;
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

    const handleChange = (event: any, newValue: number) => {
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
        <DividerWithText>Type</DividerWithText>

        <Select id="selectType" fullWidth={true} value={state.type}
                style={{ marginLeft: "8px", marginRight: "-8px", marginTop: "4px" }}
                onChange={e => handleChange(undefined, parseInt(e.target.value.toString()))}>
            <MenuItem value="0" id={"typeLocalDirectory"}>Local Directory</MenuItem>
            <MenuItem value="1" id={"typeWindowsShare"}>Windows Share</MenuItem>
            <MenuItem value="2" id={"typeS3"}>Amazon S3</MenuItem>
            <MenuItem value="3" id={"typeBackblaze"}>Backblaze B2 Cloud Storage</MenuItem>
            <MenuItem value="4" id={"typeWasabi"}>Wasabi Cloud Storage</MenuItem>
            <MenuItem value="5" id={"typeIDrive"}>iDrive E2</MenuItem>
            <MenuItem value="6" id={"typeDropbox"}>Dropbox</MenuItem>
        </Select>

        <DividerWithText>Location</DividerWithText>

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
            <BackblazeDestination
                manifestDestination={props.manifestDestination}
                destination={getTabState(3).destination}
                id={props.id}
                destinationUpdated={(valid, dest) => destinationUpdated(3, valid, dest)}/>
        </TabPanel>
        <TabPanel value={state.type} index={4}>
            <WasabiDestination
                manifestDestination={props.manifestDestination}
                destination={getTabState(4).destination}
                id={props.id}
                destinationUpdated={(valid, dest) => destinationUpdated(4, valid, dest)}/>
        </TabPanel>
        <TabPanel value={state.type} index={5}>
            <IDriveDestination
                manifestDestination={props.manifestDestination}
                destination={getTabState(5).destination}
                id={props.id}
                destinationUpdated={(valid, dest) => destinationUpdated(5, valid, dest)}/>
        </TabPanel>
        <TabPanel value={state.type} index={6}>
            <DropboxDestination
                manifestDestination={props.manifestDestination}
                destination={getTabState(6).destination}
                id={props.id}
                destinationUpdated={(valid, dest) => destinationUpdated(6, valid, dest)}/>
        </TabPanel>
    </Paper>
}