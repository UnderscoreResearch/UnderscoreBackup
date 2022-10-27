import * as React from "react";
import {Fragment, useEffect, useState} from "react";
import {
    AdditionalKey,
    BackupDefaults,
    BackupDestination,
    BackupFileSpecification,
    BackupSetRoot,
    BackupShare,
    GetBackupFiles,
    PostAdditionalEncryptionKeys,
    PutAdditionalEncryptionKey
} from '../api';
import Destination from './Destination';
import {EditableList} from './EditableList';
import {
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
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
    shares: ShareProps[],
    defaults: BackupDefaults,
    passphrase?: string,
    activeShares?: string[],
    onSubmit: () => void,
    validatedPassphrase: boolean,
    configurationUpdated: (valid: boolean, passphrase: string, shares: ShareProps[]) => void
}

function validKey(encryptionKey: string): boolean {
    if (!encryptionKey) {
        return false;
    }
    if (encryptionKey.startsWith("=")) {
        encryptionKey = encryptionKey.substring(1);
    }
    if (encryptionKey.match(/[^A-Z2-7]/)) {
        return false;
    }
    return encryptionKey.length == 52;
}

function validState(state: { share: BackupShare; encryptionKey: string; destinationValid: boolean }) {
    return state.destinationValid && !!state.share.name &&
        validKey(state.encryptionKey) && state.share.contents.roots.length > 0;
}

function Share(props: {
    id: string,
    encryptionKey: string,
    share: BackupShare,
    additionalKeys: AdditionalKey[],
    defaults: BackupDefaults,
    addNewKey: (privateKey?: string) => Promise<AdditionalKey | undefined>,
    exists: boolean,
    shareUpdated: (valid: boolean, key: string, val: BackupShare) => void
}) {
    const [state, setState] = useState(() => {
        return {
            share: {
                ...props.share,
                contents: expandRoots(props.share.contents, props.defaults) as BackupFileSpecification
            },
            encryptionKey: props.encryptionKey,
            destinationValid: true,
            missingUpdate: 1
        }
    });

    function updateState(newState: { share: BackupShare, encryptionKey: string, destinationValid: boolean, missingUpdate: number }) {
        setState(newState);

        props.shareUpdated(validState(newState), newState.encryptionKey, newState.share);
    }

    function destinationUpdated(valid: boolean, destination: BackupDestination) {
        const share = {
            ...state.share,
            destination: destination
        };
        updateState({
            ...state,
            share: share,
            destinationValid: valid
        });
    }

    function updatedName(value: string) {
        const share = {
            ...state.share,
            name: value
        };
        updateState({
            ...state,
            share: share,
        });
    }

    function updateKey(value: string) {
        const isValid = validKey(value);
        if (isValid && value.startsWith("=")) {
            const key = props.additionalKeys.find((t) => t.privateKey === value);
            if (!key) {
                props.addNewKey(value);
            } else {
                value = key.publicKey;
            }
        }
        updateState({
            ...state,
            encryptionKey: value
        });
    }

    function updateSelection(roots: BackupSetRoot[]) {
        const share = {
            ...state.share,
            contents: {
                ...state.share.contents,
                roots: roots
            }
        };
        updateState({
            ...state,
            share: share
        });
    }

    function fetchContents(path: string) {
        return GetBackupFiles(path, true);
    }

    async function generateAndAssignKey() {
        const newKey = await props.addNewKey();
        if (newKey) {
            setState((oldState) => {
                return {
                    ...oldState,
                    encryptionKey: newKey.publicKey,
                    missingUpdate: oldState.missingUpdate + 1
                }
            })
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

    let privateKey: string = "";
    if (state.encryptionKey && !state.encryptionKey.startsWith("=")) {
        const key = props.additionalKeys.find((t) => t.publicKey === state.encryptionKey);
        if (key) {
            privateKey = key.privateKey;
        }
    }

    function exclusionsChanged(items: string[]) {
        updateState({
            ...state,
            share: {
                ...state.share,
                contents: {
                    ...state.share.contents,
                    exclusions: items
                }
            }
        });
    }

    const postElement = <Fragment>
        <DividerWithText>Included Contents</DividerWithText>
        <FileTreeView roots={state.share.contents.roots}
                      defaults={props.defaults}
                      fileFetcher={(path) => fetchContents(path)}
                      stateValue={""}
                      onChange={updateSelection}/>
        <DividerWithText>Exclusions</DividerWithText>
        <ExclusionList exclusions={state.share.contents.exclusions}
                       exclusionsChanged={exclusionsChanged}/>
    </Fragment>

    return <Destination id={props.id}
                        destination={state.share.destination}
                        typeLabel={"Share Manifest Destination Type"}
                        manifestDestination={true}
                        shareDestination={true}
                        destinationUpdated={destinationUpdated}
                        postElement={postElement}
    >
        <DividerWithText>Name</DividerWithText>
        <div style={{marginLeft: "8px", marginRight: "0px", marginTop: "8px"}}>
            <TextField label="Share Name" variant="outlined"
                       id="share-name"
                       required={true}
                       fullWidth={true}
                       value={state.share.name}
                       error={!state.share.name}
                       onChange={(e) => updatedName(e.target.value)}
            />
        </div>
        <DividerWithText>Encryption Key</DividerWithText>
        <div style={{marginLeft: "8px", marginRight: "0px", marginTop: "8px", display: "flex"}}>
            <div style={{width: "100%", marginRight: "8px"}}>
                <TextField label={privateKey || props.exists ? "Public Key" : "Encryption Key"} variant="outlined"
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
                {privateKey &&
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
    </Destination>
}

interface SharesState {
    passphrase: string,
    additionalKeys: AdditionalKey[] | undefined,
    shares: ShareState[],
    showKeys: boolean,
    newPrivateKey: string
}

export default function Shares(props: SharesProps) {
    const [state, setState] = React.useState(() => {
        return {
            passphrase: props.passphrase ? props.passphrase : "",
            additionalKeys: undefined,
            showKeys: false,
            shares: props.shares.map(share => {
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

    async function fetchAdditionalKeys(passphrase: string) {
        const keys = await PostAdditionalEncryptionKeys(passphrase);
        if (keys) {
            setState((oldState) => {
                return {
                    ...oldState,
                    additionalKeys: keys.keys
                }
            });
        }
    }

    useEffect(() => {
        if (props.validatedPassphrase && props.passphrase && state.additionalKeys === undefined) {
            fetchAdditionalKeys(props.passphrase);
        }
    }, [props.passphrase, props.validatedPassphrase])

    function updateState(newState: SharesState) {
        const ids: string[] = newState.shares.map(t => t.encryptionKey);
        // @ts-ignore
        const deduped = new Set(ids);
        let shares = newState.shares.map(item => {
            return {
                share: item.share,
                additionalKeys: state.additionalKeys,
                exists: item.exists,
                id: item.encryptionKey ? item.encryptionKey : item.id
            }
        })

        setState({
            ...newState
        });

        props.configurationUpdated(
            deduped.size == ids.length &&
            !newState.shares.some(item => !item.valid),
            newState.passphrase ? newState.passphrase : "",
            shares
        );
    }

    function destinationChanged(items: ShareState[]) {
        updateState({
            ...state,
            shares: items
        });
    }

    function findNewId() {
        var i = 1;
        while (state.shares.some(item => item.id === "-d" + i)) {
            i++;
        }
        return "-d" + i;
    }

    async function addNewKey(privateKey?: string): Promise<AdditionalKey | undefined> {
        const newKey = await PutAdditionalEncryptionKey(state.passphrase, privateKey);
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

    const list = EditableList<ShareState>({
        deleteBelow: true,
        createNewItem: () => {
            return {
                id: findNewId(),
                valid: false,
                exists: false,
                share: {
                    name: "",
                    destination: {
                        type: "FILE",
                        endpointUri: ''
                    },
                    contents: {
                        roots: []
                    } as BackupFileSpecification
                }
            } as ShareState
        },
        allowDrop: (item) => true,
        onItemChanged: destinationChanged,
        items: state.shares,
        createItem: (item, itemUpdated: (item: ShareState) => void) => {
            return <Share id={item.id}
                          share={item.share}
                          exists={item.exists}
                          encryptionKey={item.encryptionKey}
                          additionalKeys={state.additionalKeys ? state.additionalKeys : []}
                          defaults={props.defaults}
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
        }
    });

    if (!props.passphrase || !props.validatedPassphrase) {
        return <Stack spacing={2} style={{width: "100%"}}>
            <Paper sx={{
                p: 2
            }}>
                <Typography variant="body1" component="div">
                    <p>
                        To create or modify shares you must provide your backup passphrase.
                    </p>
                </Typography>
                <Box component="div"
                     sx={{
                         '& .MuiTextField-root': {m: 1},
                     }}
                     style={{marginTop: 4}}>
                    <TextField label="Passphrase" variant="outlined"
                               fullWidth={true}
                               required={true}
                               id={"restorePassphrase"}
                               value={state.passphrase}
                               error={!state.passphrase}
                               type="password"
                               onKeyDown={(e) => {
                                   if (e.key === "Enter") {
                                       props.onSubmit();
                                   }
                               }}
                               onChange={(e) => {
                                   const newState = {
                                       ...state,
                                       passphrase: e.target.value
                                   };
                                   updateState(newState);
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

            {list}

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
                                            {row.publicKey}
                                            <IconButton aria-label="delete" size="small"
                                                        onClick={() => {
                                                            navigator.clipboard.writeText(row.publicKey)
                                                        }}>
                                                <ContentCopy fontSize="inherit"/>
                                            </IconButton>
                                        </TableCell>
                                        <TableCell component="th" scope="row" sx={{fontFamily: 'Monospace'}}>
                                            {row.privateKey}
                                            <IconButton aria-label="delete" size="small"
                                                        onClick={() => {
                                                            navigator.clipboard.writeText(row.privateKey)
                                                        }}>
                                                <ContentCopy fontSize="inherit"/>
                                            </IconButton>
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