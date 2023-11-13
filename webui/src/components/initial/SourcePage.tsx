import Paper from "@mui/material/Paper";
import Typography from "@mui/material/Typography";
import {
    Alert,
    Button,
    CircularProgress,
    Collapse,
    FormControl,
    FormControlLabel,
    Grid,
    Radio,
    RadioGroup,
    Stack,
    TextField
} from "@mui/material";
import * as React from "react";
import {Fragment, useEffect} from "react";
import Box from "@mui/material/Box";
import {createSource, listSources, SourceResponse, updateSource} from "../../api/service";
import {useApplication} from "../../utils/ApplicationContext";
import {getState} from "../../api";

export interface SourcePageProps {
    onSourceChange: (source: SourceResponse | undefined, page: string) => void,
}

export interface SourcePageState {
    busy: boolean,
    type: string | undefined,
    sourceName: string,
    sourceList: SourceResponse[] | undefined,
}

function formatDate(lastUsage: number | undefined) {
    if (lastUsage) {
        const d = new Date(0);
        d.setUTCSeconds(lastUsage);
        return ", last updated " + d.toLocaleDateString();
    }
    return "";
}

export function formatNumber(num: number) {
    return num.toLocaleString();
}

export default function SourcePage(props: SourcePageProps) {
    const appContext = useApplication();
    const [state, setState] = React.useState({
        busy: true,
        type: undefined,
        sourceName: appContext.backendState.sourceName
    } as SourcePageState);

    async function fetchSources() {
        if (!state.sourceList) {
            let sources = await listSources(false);
            setState((oldState) => ({
                ...oldState,
                sourceList: sources.sources,
                busy: false
            }));
        }
    }

    useEffect(() => {
        fetchSources();
    }, []);


    async function newSource() {
        await appContext.busyOperation(async () => {
            await createSource(state.sourceName);
            const newBackendState = await getState();
            setState((oldState) => ({...oldState, backendState: newBackendState}));
        });
        props.onSourceChange(undefined, "destination");
    }

    async function adoptSource(sourceId: string, name: string) {
        setState((oldState) => ({
            ...oldState,
            busy: true
        }));

        const source = state.sourceList?.find(item => item.sourceId === sourceId);

        try {
            if (source) {
                if (source.destination && source.encryptionMode && source.key) {
                    props.onSourceChange(source, "security");
                } else {
                    await updateSource(name, sourceId);
                    props.onSourceChange(undefined, "destination");
                }
            }
        } finally {
            setState((oldState) => ({
                ...oldState,
                busy: false,
            }));
        }
    }

    return <Stack spacing={2} style={{width: "100%"}}>
        <Paper sx={{
            p: 2,
        }}>
            {state.sourceList === undefined ?
                <Grid container spacing={2} alignItems={"center"} marginTop={"8px"}>
                    <Grid item xs={12}>
                        <Box textAlign={"center"}>
                            <CircularProgress/>
                        </Box>
                    </Grid>
                </Grid>
                :
                <>
                    {state.sourceList.length > 0 ?
                        <>
                            <Typography variant="h3" component="div" marginBottom={"16px"}>
                                Adopt existing source?
                            </Typography>
                            <Typography variant="body1" component="div">
                                <p>
                                    Would you like to adopt an existing backup source or start a new one?
                                </p>
                                <Alert severity="warning">
                                    Adopting an existing source will stop any backup currently backing up to that
                                    source.
                                </Alert>
                            </Typography>
                        </>
                        :
                        <Typography variant="h3" component="div" marginBottom={"16px"}>
                            Name your backup source
                        </Typography>
                    }

                    {state.sourceList.length > 0 &&
                        <Grid container spacing={2} alignItems={"center"} marginTop={"8px"}>
                            <Grid item xs={12}>
                                <FormControl>
                                    <RadioGroup row
                                                onChange={(ignored, val) => setState({...state, type: val})}>
                                        <FormControlLabel id="newChoice" value="new" control={<Radio/>}
                                                          label={<Typography fontSize={"larger"}>Create a new
                                                              backup</Typography>}/>
                                        <FormControlLabel id="adoptChoice" value="adopt" control={<Radio/>}
                                                          label={<Typography fontSize={"larger"}>Take over an
                                                              already
                                                              existing backup</Typography>}/>
                                    </RadioGroup>
                                </FormControl>
                            </Grid>
                        </Grid>
                    }
                    <Collapse in={state.type == "adopt" && state.sourceList.length > 0}>
                        {state.type == "adopt" && state.sourceList.length > 0 &&
                            <Grid container spacing={2} alignItems={"center"} marginTop={"8px"}>
                                <Grid item xs={12}>
                                    <Typography variant="h6" component="div" marginBottom={"16px"}>
                                        Existing backup sources
                                    </Typography>
                                </Grid>
                            </Grid>
                        }
                        {state.type == "adopt" &&
                            <Grid container spacing={2} alignItems={"center"} marginTop={"8px"}>
                                {state.sourceList.map(source =>
                                    <Fragment key={source.sourceId}>
                                        <Grid item md={9} xs={12}>
                                            <b>{source.name}</b>

                                            {(!source.destination || !source.key || !source.encryptionMode) ?
                                                <> (No configuration)</>
                                                :
                                                <>
                                                    {source.dailyUsage.length > 0 && source.dailyUsage[0].usage ?
                                                        <> ({formatNumber(source.dailyUsage[0].usage)} GB{formatDate(source.lastUsage)})</>
                                                        :
                                                        <> (No service data)</>
                                                    }
                                                </>
                                            }
                                        </Grid>
                                        <Grid item md={3} xs={12}>
                                            <Button fullWidth={true} disabled={state.busy} variant="contained"
                                                    id="adoptSource"
                                                    onClick={() => adoptSource(source.sourceId, source.name)}>
                                                Adopt
                                            </Button>
                                        </Grid>
                                    </Fragment>
                                )}
                            </Grid>
                        }
                    </Collapse>

                    <Collapse in={(state.type === "new" || state.sourceList.length === 0)}>
                        {(state.type === "new" || state.sourceList.length === 0) &&
                            <Grid container spacing={2} alignItems={"center"} marginTop={"8px"}>
                                <Grid item xs={12}>
                                    <Typography variant="h6" component="div" marginBottom={"16px"}>
                                        Create a new backup source
                                    </Typography>
                                </Grid>
                                <Grid item md={9} xs={12}>
                                    <TextField
                                        id={"sourceName"}
                                        fullWidth={true}
                                        value={state.sourceName}
                                        onChange={e => setState({
                                            ...state,
                                            sourceName: e.target.value
                                        })}
                                    />
                                </Grid>
                                <Grid item md={3} xs={12}>
                                    <Button fullWidth={true} disabled={state.busy || !state.sourceName}
                                            variant="contained"
                                            id="newSource"
                                            onClick={() => newSource()}>
                                        <>New Source</>
                                    </Button>
                                </Grid>
                            </Grid>
                        }
                    </Collapse>
                </>
            }
        </Paper>
    </Stack>
}