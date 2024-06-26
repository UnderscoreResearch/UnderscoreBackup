import * as React from "react";
import {Fragment, useEffect, useState} from "react";
import {
    AdditionalKey,
    BackupDestination,
    BackupFileSpecification,
    BackupSetRoot,
    BackupShare,
    BackupState,
    createAdditionalEncryptionKey,
    getBackupFiles,
    listAdditionalEncryptionKeys,
    ShareMap
} from '../api';
import Destination from './Destination';
import {EditableList} from './EditableList';
import {
    Button,
    Checkbox,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    FormControlLabel,
    InputAdornment,
    Stack,
    TableHead,
    TextField,
    Tooltip
} from "@mui/material";
import DividerWithText from "../3rdparty/react-js-cron-mui/components/DividerWithText";
import FileTreeView from "./FileTreeView";
import Paper from "@mui/material/Paper";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import TableContainer from "@mui/material/TableContainer";
import Table from "@mui/material/Table";
import TableRow from "@mui/material/TableRow";
import TableCell from "@mui/material/TableCell";
import TableBody from "@mui/material/TableBody";
import {AddCircle, ContentCopy} from "@mui/icons-material";
import IconButton from "@mui/material/IconButton";
import {expandRoots} from "../api/utils";
import ExclusionList from "./ExclusionList";
import {ApplicationContext, useApplication} from "../utils/ApplicationContext";
import {useButton} from "../utils/ButtonContext";
import KeyTooltip, {truncateKey} from "./KeyTooltip";
import {base32} from "rfc4648";

interface ShareState {
    valid: boolean,
    share: BackupShare,
    exists: boolean,
    name: string,
    id: string,
    encryptionKey: string
}

export interface ShareProps {
    id: string,
    exists: boolean,
    share: BackupShare
}

export interface SharesProps {
    password?: string,
    sharesUpdated: (valid: boolean, password: string) => void
}

function getKeyId(encryptionKey: string): string {
    if (encryptionKey && encryptionKey.startsWith("{")) {
        const decode = JSON.parse(encryptionKey);
        if (decode.k && decode.k.p && decode.k.p.u) {
            return base32.stringify(Buffer.from(decode.k.p.u, "base64"), {pad: false});
        }
    }
    return encryptionKey;
}

function getAdditionalKeyId(key: AdditionalKey) {
    if (key.publicKey && key.publicKey.startsWith("{")) {
        const decode = JSON.parse(key.publicKey);
        if (decode.k && decode.k.p && decode.k.p.u) {
            return base32.stringify(Buffer.from(decode.k.p.u, "base64"), {pad: false});
        }
    }
    return key.publicKey;
}

function validKey(encryptionKey: string): boolean {
    if (!encryptionKey) {
        return false;
    }
    if (encryptionKey.startsWith("=")) {
        encryptionKey = encryptionKey.substring(1);
    }
    if (encryptionKey.length == 52 && encryptionKey.match(/[^A-Z2-7]/)) {
        return true;
    }
    try {
        const str = JSON.parse(encryptionKey)
        if (str.k && str.k.p && str.k.p.u) {
            return true;
        }
        return false;
    } catch (e) {
        return false;
    }
}

interface ShareItemState {
    share: BackupShare,
    serviceSharing: boolean,
    generatingKey: boolean,
    encryptionKey: string,
    destinationValid: boolean,
    missingUpdate: number
}

function validState(state: ShareItemState): boolean {
    return state.destinationValid && !!state.share.name &&
        (!state.serviceSharing || (!!state.share.targetEmail && state.share.targetEmail.indexOf("@") > 0)) &&
        validKey(state.encryptionKey) && state.share.contents.roots.length > 0;
}

function externalShare(state: ShareItemState): BackupShare {
    if (state.serviceSharing) {
        return {
            ...state.share,
            targetEmail: state.share.targetEmail
        };
    } else {
        return state.share;
    }
}

function getSharesList(appContext: ApplicationContext): ShareProps[] {
    const keys = appContext.currentConfiguration.shares
        ? Object.keys(appContext.currentConfiguration.shares)
        : [];
    // @ts-ignore
    keys.sort((a, b) => appContext.currentConfiguration.shares[a].name.localeCompare(appContext.currentConfiguration.shares[b].name));

    return keys.map(key => {
        return {
            share: (appContext.currentConfiguration.shares as ShareMap)[key] as BackupShare,
            exists: (!!appContext.originalConfiguration.shares) &&
                !!((appContext.originalConfiguration.shares as ShareMap)[key]),
            id: key
        }
    });
}

function ShareItem(props: {
    id: string,
    encryptionKey: string,
    backendState: BackupState,
    share: BackupShare,
    additionalKeys: AdditionalKey[],
    addNewKey: (privateKey?: string) => Promise<AdditionalKey | undefined>,
    exists: boolean,
    shareUpdated: (valid: boolean, key: string, val: BackupShare) => void
}) {
    const [state, setState] = useState(() => {
        return {
            share: {
                ...props.share,
                contents: expandRoots(props.share.contents, props.backendState) as BackupFileSpecification
            },
            serviceSharing: (!!props.backendState.serviceSourceId && (props.share.targetEmail || !props.exists)) as boolean,
            generatingKey: false,
            encryptionKey: props.encryptionKey,
            destinationValid: true,
            missingUpdate: 1
        } as ShareItemState;
    });

    useEffect(() => {
        if (props.exists) {
            setState((oldState) => ({
                ...oldState,
                encryptionKey: props.encryptionKey
            }));
        }
    }, [props.encryptionKey]);

    function destinationUpdated(valid: boolean, destination: BackupDestination) {
        setState((oldState) => ({
            ...oldState,
            destinationValid: valid,
            share: {
                ...oldState.share,
                destination: destination
            }
        }));
    }

    useEffect(() => {
        props.shareUpdated(validState(state), state.encryptionKey, externalShare(state));
    }, [state.destinationValid, state.share, state.serviceSharing, state.encryptionKey, state.share.destination]);

    function updatedName(value: string) {
        setState((oldState) => ({
            ...oldState,
            share: {
                ...oldState.share,
                name: value
            }
        }));
    }

    function updateKey(value: string) {
        const isValid = validKey(value);
        if (isValid) {
            if (value.startsWith("=")) {
                const key = props.additionalKeys.find((t) => t.privateKey === value);
                if (!key) {
                    props.addNewKey(value);
                } else {
                    value = key.publicKey;
                }
            } else {
                const key = props.additionalKeys.find((t) => getAdditionalKeyId(t) === value);
                if (key) {
                    value = key.publicKey;
                }
            }
        }
        setState({
            ...state,
            encryptionKey: value
        });
    }

    function updateSelection(roots: BackupSetRoot[]) {
        setState((oldState) => ({
            ...oldState,
            share: {
                ...oldState.share,
                contents: {
                    ...oldState.share.contents,
                    roots: roots
                }
            }
        }));
    }

    function fetchContents(path: string) {
        return getBackupFiles(path, true);
    }

    async function generateAndAssignKey() {
        if (state.generatingKey) {
            return;
        }
        setState((oldState) => ({...oldState, generatingKey: true}));
        const newKey = await props.addNewKey();
        if (newKey) {
            setState((oldState) => {
                return {
                    ...oldState,
                    encryptionKey: newKey.publicKey,
                    missingUpdate: oldState.missingUpdate + 1,
                    generatingKey: false
                }
            })
        } else {
            setState((oldState) => ({...oldState, generatingKey: false}));
        }
    }

    useEffect(() => {
        if (validKey(state.encryptionKey) && state.encryptionKey.startsWith("=")) {
            const key = props.additionalKeys.find((t) => t.privateKey === state.encryptionKey);
            if (!key) {
                props.addNewKey(state.encryptionKey);
            } else {
                setState((oldState) => {
                    return {
                        ...oldState,
                        encryptionKey: key.publicKey
                    };
                })
            }
        }
    }, [state.encryptionKey, props.additionalKeys])

    useEffect(() => {
        if (props.id !== state.encryptionKey) {
            props.shareUpdated(validState(state), state.encryptionKey, state.share);
        }
    }, [state.missingUpdate])

    const keyId = getKeyId(props.encryptionKey);

    let privateKey: string = "";
    if (state.encryptionKey && !state.encryptionKey.startsWith("=")) {
        const key = props.additionalKeys.find((t) => getAdditionalKeyId(t) === keyId);
        if (key) {
            privateKey = key.privateKey;
        }
    }

    function exclusionsChanged(items: string[]) {
        setState((oldState) => ({
            ...oldState,
            share: {
                ...oldState.share,
                contents: {
                    ...oldState.share.contents,
                    exclusions: items
                }
            }
        }));
    }

    const postElement = <Fragment>
        <DividerWithText>Included contents</DividerWithText>
        <FileTreeView roots={state.share.contents.roots}
                      backendState={props.backendState}
                      fileFetcher={(path) => fetchContents(path)}
                      stateValue={""}
                      onChange={updateSelection}/>
        <DividerWithText>Exclusions</DividerWithText>
        <ExclusionList exclusions={state.share.contents.exclusions}
                       exclusionsChanged={exclusionsChanged}/>
    </Fragment>

    function updateTargetEmail(value: string) {
        if (value && !state.encryptionKey) {
            generateAndAssignKey();
        }
        setState((oldState) => ({
            ...oldState,
            share: {
                ...oldState.share,
                targetEmail: value
            }
        }));
    }

    return <Destination id={props.id}
                        backendState={props.backendState}
                        destination={state.share.destination}
                        typeLabel={"Share Manifest Destination Type"}
                        manifestDestination={true}
                        shareDestination={true}
                        destinationUpdated={destinationUpdated}
                        postElement={postElement}
    >
        <DividerWithText>Name</DividerWithText>
        <div style={{marginLeft: "0px", marginRight: "0px", marginTop: "8px"}}>
            <TextField label="Share Name" variant="outlined"
                       id="share-name"
                       required={true}
                       fullWidth={true}
                       value={state.share.name}
                       error={!state.share.name}
                       onChange={(e) => updatedName(e.target.value)}
            />
        </div>
        {(!!props.backendState.serviceSourceId) &&
            <>
                <DividerWithText>Service sharing</DividerWithText>
                <div style={{marginLeft: "0px", marginRight: "0px", marginBottom: "8px"}}>
                    <FormControlLabel control={<Checkbox
                        checked={state.serviceSharing}
                        onChange={(e) => setState((oldState) => ({...oldState, serviceSharing: e.target.checked}))}
                    />} label="Share through Underscore Backup service"/>
                </div>
                {state.serviceSharing &&
                    <div style={{marginLeft: "0px", marginRight: "0px", marginTop: "8px"}}>
                        <TextField label="Recipient service account email address" variant="outlined"
                                   id="recipient-email"
                                   required={true}
                                   fullWidth={true}
                                   value={state.share.targetEmail}
                                   error={!state.share.targetEmail}
                                   onChange={(e) => updateTargetEmail(e.target.value)}
                        />
                    </div>
                }
            </>
        }
        {(!props.backendState.serviceSourceId || !state.serviceSharing) &&
            <>
                <DividerWithText>Encryption key</DividerWithText>
                <div style={{marginLeft: "0px", marginRight: "0px", marginTop: "8px", display: "flex"}}>
                    <div style={{width: "100%", marginRight: "8px"}}>
                        <KeyTooltip encryptionKey={state.encryptionKey}>
                            <TextField label={privateKey || props.exists ? "Public Key" : "Encryption Key"}
                                       variant="outlined"
                                       required={true}
                                       fullWidth={true}
                                       InputProps={{
                                           style: {fontFamily: 'Monospace'}
                                       }}
                                       value={state.encryptionKey ? state.encryptionKey : ""}
                                       disabled={props.exists}
                                       error={!state.encryptionKey}
                                       onChange={(e) => updateKey(e.target.value)}
                            />
                        </KeyTooltip>
                        <KeyTooltip encryptionKey={keyId}>
                            <TextField label={"Key ID"}
                                       variant="outlined"
                                       required={true}
                                       style={{marginTop: "8px"}}
                                       fullWidth={true}
                                       InputProps={{
                                           style: {fontFamily: 'Monospace'}
                                       }}
                                       value={keyId}
                                       disabled={true}
                            />
                        </KeyTooltip>
                        {privateKey &&
                            <KeyTooltip encryptionKey={privateKey}>
                                <TextField label="Private Key" variant="outlined"
                                           style={{marginTop: "8px"}}
                                           fullWidth={true}
                                           InputProps={{
                                               style: {fontFamily: 'Monospace'},
                                               endAdornment: (
                                                   <InputAdornment position={"end"}>
                                                       <IconButton onClick={() => {
                                                           navigator.clipboard.writeText(privateKey)
                                                       }}>
                                                           <ContentCopy/>
                                                       </IconButton>
                                                   </InputAdornment>
                                               )
                                           }}
                                           value={privateKey}
                                           disabled={true}
                                />
                            </KeyTooltip>
                        }
                    </div>
                    {!props.exists && !state.encryptionKey &&
                        <IconButton id="generate-key" aria-label="generate key" size="large"
                                    onClick={() => {
                                        generateAndAssignKey()
                                    }}>
                            <AddCircle fontSize="inherit"/>
                        </IconButton>
                    }
                </div>
            </>
        }
    </Destination>;
}

interface SharesState {
    password: string,
    additionalKeys: AdditionalKey[] | undefined,
    shares: ShareState[],
    showKeys: boolean,
    newPrivateKey: string
}

function getSharePublicKey(encryptionKey: string, keys: AdditionalKey[]) {
    const key = keys.find(t => getAdditionalKeyId(t) === encryptionKey)
    if (key && key.publicKey) {
        return key.publicKey;
    }
    return encryptionKey;
}

export default function Shares(props: SharesProps) {
    const appContext = useApplication();
    const buttonContext = useButton();
    const [state, setState] = React.useState(() => {
        return {
            password: props.password ? props.password : "",
            additionalKeys: undefined,
            showKeys: false,
            shares: getSharesList(appContext).map(share => {
                return {
                    valid: true,
                    share: share.share,
                    exists: share.exists,
                    name: share.share.name,
                    encryptionKey: share.id.startsWith("-") ? undefined : share.id,
                    id: share.id
                }
            }),
            newPrivateKey: ""
        } as SharesState
    });

    async function fetchAdditionalKeys(password: string) {
        const keys = await listAdditionalEncryptionKeys(password);
        if (keys) {
            setState((oldState) => {
                return {
                    ...oldState,
                    shares: oldState.shares.map(item => {
                        return {
                            ...item,
                            encryptionKey: getSharePublicKey(item.encryptionKey, keys.keys)
                        }
                    }),
                    additionalKeys: keys.keys
                }
            });
        }
    }

    useEffect(() => {
        if (appContext.validatedPassword && props.password && state.additionalKeys === undefined) {
            fetchAdditionalKeys(props.password);
        }
    }, [props.password, appContext.validatedPassword])

    function updateState(newState: SharesState) {
        const ids: string[] = newState.shares.map(t => t.encryptionKey);
        const deDuped = new Set(ids);

        let shares: ShareMap = {};
        newState.shares.forEach(item => {
            shares[getKeyId(item.encryptionKey ? item.encryptionKey : item.id)] = item.share;
        })

        setState({
            ...newState
        });

        props.sharesUpdated(
            deDuped.size == ids.length &&
            !newState.shares.some(item => !item.valid),
            newState.password ? newState.password : ""
        );

        appContext.setState((oldState) => ({
            ...oldState,
            currentConfiguration: {
                ...oldState.currentConfiguration,
                shares: shares
            }
        }));
    }

    function destinationChanged(items: ShareState[]) {
        updateState({
            ...state,
            shares: items
        });
    }

    function findNewId() {
        let i = 1;
        while (state.shares.some(item => item.id === "-d" + i)) {
            i++;
        }
        return "-d" + i;
    }

    async function addNewKey(privateKey?: string): Promise<AdditionalKey | undefined> {
        const newKey = await createAdditionalEncryptionKey(state.password, privateKey);
        if (newKey && newKey.privateKey && newKey.publicKey) {
            setState((oldState) => {
                let newKeys;
                if (oldState.additionalKeys) {
                    newKeys = [...oldState.additionalKeys, newKey];
                } else {
                    newKeys = [newKey];
                }
                return {
                    ...oldState,
                    additionalKeys: newKeys
                }
            });
        }
        return newKey;
    }

    if (!props.password || !appContext.validatedPassword) {
        return <Stack spacing={2} style={{width: "100%"}}>
            <Paper sx={{
                p: 2
            }}>
                <Typography variant="body1" component="div">
                    <p>
                        To create or modify shares you must provide your backup password.
                    </p>
                </Typography>
                <Box component="div"
                     sx={{
                         '& .MuiTextField-root': {m: 1},
                     }}
                     style={{marginTop: 4}}>
                    <TextField label="Password" variant="outlined"
                               fullWidth={true}
                               required={true}
                               id={"restorePassword"}
                               value={state.password}
                               error={!state.password}
                               type="password"
                               onKeyDown={(e) => {
                                   if (e.key === "Enter") {
                                       buttonContext.accept();
                                   }
                               }}
                               onChange={(e) => {
                                   const newState = {
                                       ...state,
                                       password: e.target.value
                                   };
                                   setState(newState);
                                   props.sharesUpdated(
                                       true,
                                       newState.password ? newState.password : ""
                                   );
                               }}/>
                </Box>
            </Paper>
        </Stack>
    } else {
        return <Stack spacing={2} style={{width: "100%"}}>
            <div>
                <Button style={{float: "right"}} variant="contained" onClick={() => {
                    setState({...state, showKeys: true})
                }}>
                    Key Management
                </Button>
            </div>

            {state.additionalKeys &&
                <EditableList
                    deleteBelow={true}
                    createNewItem={() => {
                        return {
                            id: findNewId(),
                            valid: false,
                            exists: false,
                            share: {
                                name: "",
                                destination: {
                                    type: !!appContext.backendState.serviceSourceId ? "UB" : "FILE",
                                    endpointUri: ''
                                },
                                contents: {
                                    roots: []
                                } as BackupFileSpecification
                            }
                        } as ShareState
                    }}
                    allowDrop={() => true}
                    onItemChanged={destinationChanged}
                    items={state.shares}
                    createItem={(item, itemUpdated: (item: ShareState) => void) => {
                        return <ShareItem id={item.id}
                                          share={item.share}
                                          exists={item.exists}
                                          encryptionKey={item.encryptionKey}
                                          additionalKeys={state.additionalKeys ? state.additionalKeys : []}
                                          backendState={appContext.backendState}
                                          addNewKey={addNewKey}
                                          shareUpdated={(valid, encryptionKey, share) => {
                                              itemUpdated({
                                                  valid: valid,
                                                  share: share,
                                                  exists: item.exists,
                                                  id: item.id,
                                                  encryptionKey: encryptionKey,
                                                  name: share.name
                                              });
                                          }}
                        />
                    }}
                />
            }

            <Dialog open={state.showKeys} onClose={() => setState({...state, showKeys: false})} fullWidth={true}
                    maxWidth={"xl"}>
                <DialogTitle>Additional Keys For Sharing</DialogTitle>
                <DialogContent>
                    <DialogContentText>
                        Below are all the currently knows keys that can be used for sharing.
                    </DialogContentText>
                    <TableContainer component={Paper} style={{marginTop: "8px"}}>
                        <Table sx={{minWidth: 650}} size="small" aria-label="a dense table">
                            <TableHead>
                                <TableRow>
                                    <TableCell>
                                        <Tooltip
                                            title="Public keys are needed by whoever is sharing data. You do not need the private key to create and activate a share.">
                                            <div>Public Key</div>
                                        </Tooltip>
                                    </TableCell>
                                    <TableCell>
                                        <Tooltip
                                            title="Private keys are needed by the recipient of a share. The public key can be derived from the private key, but not the other way around.">
                                            <div>Private Key</div>
                                        </Tooltip>
                                    </TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {(state.additionalKeys ? state.additionalKeys : []).map((row) => (
                                    <TableRow
                                        key={row.publicKey}
                                        sx={{'&:last-child td, &:last-child th': {border: 0}}}
                                    >
                                        <TableCell component="th" scope="row" sx={{fontFamily: 'Monospace'}}>
                                            {truncateKey(row.publicKey)}
                                            <KeyTooltip encryptionKey={row.publicKey}>
                                                <IconButton aria-label="copy" size="small"
                                                            onClick={() => {
                                                                navigator.clipboard.writeText(row.publicKey)
                                                            }}>
                                                    <ContentCopy fontSize="inherit"/>
                                                </IconButton>
                                            </KeyTooltip>
                                        </TableCell>
                                        <TableCell component="th" scope="row" sx={{fontFamily: 'Monospace'}}>
                                            {truncateKey(row.privateKey)}
                                            <KeyTooltip encryptionKey={row.privateKey}>
                                                <IconButton aria-label="copy" size="small"
                                                            onClick={() => {
                                                                navigator.clipboard.writeText(row.privateKey)
                                                            }}>
                                                    <ContentCopy fontSize="inherit"/>
                                                </IconButton>
                                            </KeyTooltip>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </TableContainer>

                    <DividerWithText>Import Private Key</DividerWithText>
                    <div style={{display: "flex"}}>
                        <div style={{width: "100%", marginRight: "8px"}}>
                            <TextField label={"Private Key"} variant="outlined"
                                       fullWidth={true}
                                       InputProps={{
                                           style: {fontFamily: 'Monospace'}
                                       }}
                                       value={state.newPrivateKey}
                                       onChange={(e) => setState({
                                           ...state,
                                           newPrivateKey: e.target.value
                                       })}
                            />
                        </div>
                        <div style={{display: "flex", alignItems: "center"}}>
                            <Button aria-label="delete" disabled={!validKey(state.newPrivateKey)}
                                    onClick={() => {
                                        setState({
                                            ...state,
                                            newPrivateKey: ""
                                        });
                                        addNewKey(state.newPrivateKey)
                                    }} variant={"contained"}>
                                Add
                            </Button>
                        </div>
                    </div>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => addNewKey()} color={"secondary"}>Create New</Button>
                    <Button onClick={() => setState({...state, showKeys: false})} autoFocus={true}>Close</Button>
                </DialogActions>
            </Dialog>
        </Stack>
    }
}