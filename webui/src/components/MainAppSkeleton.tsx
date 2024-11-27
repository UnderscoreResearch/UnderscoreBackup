import Box from "@mui/material/Box";
import {
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    LinearProgress,
    MenuItem
} from "@mui/material";
import Toolbar from "@mui/material/Toolbar";
import IconButton from "@mui/material/IconButton";
import MoreVert from "@mui/icons-material/MoreVert";
import Typography from "@mui/material/Typography";
import ChevronLeftIcon from "@mui/icons-material/ChevronLeft";
import Divider from "@mui/material/Divider";
import Slide from "@mui/material/Slide";
import {marked} from "marked";
import * as React from "react";
import {AppBarProps as MuiAppBarProps} from "@mui/material/AppBar/AppBar";
import {styled} from "@mui/material/styles";
import MuiAppBar from "@mui/material/AppBar";
import MuiDrawer from "@mui/material/Drawer";
import {useApplication} from "../utils/ApplicationContext";
import {useTheme} from "../utils/Theme";
import {IndividualButtonProps} from "../utils/ButtonContext";
import {NightsStay, WbSunny} from "@mui/icons-material";
import Menu from "@mui/material/Menu";
import MenuIcon from "@mui/icons-material/Menu";
import SupportBundleDialog from "./SupportBundleDialog";
import PasswordDialog from "./PasswordDialog";
import {
    defragRepository,
    makeApiCall,
    optimizeRepository,
    repairRepository,
    trimRepository,
    validateBlocks
} from "../api";
import {useActivity} from "../utils/ActivityContext";
import {DisplayState} from "../utils/DisplayState";
import ConfirmationDialog, {ConfirmationDialogProps} from "./ConfirmationDialog";

const drawerWidth: number = 240;

interface AppBarProps extends MuiAppBarProps {
    open?: boolean;
}


export function AcceptButton(props: IndividualButtonProps) {
    return <Box sx={{flexGrow: 0, marginRight: "1em"}}>
        <Button sx={{my: 2, color: 'white', display: 'block'}}
                variant="contained"
                id={"acceptButton"}
                color={props.color ?? "success"}
                disabled={props.disabled}
                onClick={props.action}>
            {props.title}
        </Button>
    </Box>
}

export function CancelButton(props: IndividualButtonProps) {
    return <Box sx={{flexGrow: 0, marginRight: "2em"}}>
        <Button sx={{my: 2, color: 'white', display: 'block'}}
                id={"cancelButton"}
                color={props.color}
                disabled={props.disabled}
                onClick={props.action}>
            {props.title}
        </Button>
    </Box>
}

const AppBar = styled(MuiAppBar, {
    shouldForwardProp: (prop) => prop !== 'open',
})<AppBarProps>(({theme, open}) => ({
    zIndex: theme.zIndex.drawer + 1,
    transition: theme.transitions.create(['width', 'margin'], {
        easing: theme.transitions.easing.sharp,
        duration: theme.transitions.duration.leavingScreen,
    }),
    ...(open && {
        marginLeft: drawerWidth,
        width: `calc(100% - ${drawerWidth}px)`,
        transition: theme.transitions.create(['width', 'margin'], {
            easing: theme.transitions.easing.sharp,
            duration: theme.transitions.duration.enteringScreen,
        }),
    }),
}));

const Drawer = styled(MuiDrawer, {shouldForwardProp: (prop) => prop !== 'open'})(
    ({theme, open}) => ({
        '& .MuiDrawer-paper': {
            position: 'relative',
            whiteSpace: 'nowrap',
            width: drawerWidth,
            transition: theme.transitions.create('width', {
                easing: theme.transitions.easing.sharp,
                duration: theme.transitions.duration.enteringScreen,
            }),
            boxSizing: 'border-box',
            ...(!open && {
                overflowX: 'hidden',
                transition: theme.transitions.create('width', {
                    easing: theme.transitions.easing.sharp,
                    duration: theme.transitions.duration.leavingScreen,
                }),
                width: theme.spacing(7),
                [theme.breakpoints.up('sm')]: {
                    width: theme.spacing(9),
                },
            }),
        },
    }),
);

export interface MainAppSkeletonProps {
    title: string;
    processing: boolean;
    navigation: React.ReactNode;
    disallowClose?: boolean;
    children?: React.ReactNode;
    acceptButton?: IndividualButtonProps;
    cancelButton?: IndividualButtonProps;
    displayState?: DisplayState;
}

interface MainAppSkeletonState {
    open: boolean;
    slideAnimation: boolean;
    showNewVersion: boolean;
}

function defaultConfirmationDialog(): ConfirmationDialogProps {
    return {
        open: false,
        close: () => {
        },
        action: () => {
        },
        label: "",
        dialogText: [] as string[]
    };
}

export function MainAppSkeleton(props: MainAppSkeletonProps) {
    const appContext = useApplication();
    const activityContext = useActivity();
    const theme = useTheme();

    const [showSupportBundle, setShowSupportBundle] = React.useState(false);

    const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);

    const [repairDialogOpen, setRepairDialogOpen] = React.useState(false);

    const [confirmationDialog, setConfirmationDialog] = React.useState(() => defaultConfirmationDialog());

    const open = Boolean(anchorEl);
    const [state, setState] = React.useState<MainAppSkeletonState>({
        open: window.localStorage.getItem("open") !== "false",
        slideAnimation: false,
        showNewVersion: false
    });


    async function repairRepositoryNow(password: string) {
        setRepairDialogOpen(false);
        await appContext.busyOperation(async () => {
            if (await repairRepository(password)) {
                await appContext.updateBackendState();
            }
            await activityContext.update();
        });
    }

    function handleClick(event: React.MouseEvent<HTMLElement, MouseEvent>) {
        setAnchorEl(event.currentTarget);
        event.preventDefault();
    }

    function handleClose() {
        setAnchorEl(null);
    }

    function toggleDrawer() {
        setState((oldState) => ({...oldState, open: !state.open}));
        window.localStorage.setItem("open", (!state.open).toString());
    }

    const busyState = !props.displayState ||
        (!props.displayState.backupCanStart && !props.displayState?.backupInProgress);

    async function executeTrimRepository() {
        await appContext.busyOperation(async () => {
            if (await trimRepository()) {
                await appContext.updateBackendState();
            }
            await activityContext.update();
        });
    }

    async function executeOptimizeLogs() {
        await appContext.busyOperation(async () => {
            if (await optimizeRepository()) {
                await appContext.updateBackendState();
            }
            await activityContext.update();
        });
    }

    async function executeValidateBlocks() {
        await appContext.busyOperation(async () => {
            if (await validateBlocks()) {
                await appContext.updateBackendState();
            }
            await activityContext.update();
        });
    }

    async function executeDefragRepository() {
        await appContext.busyOperation(async () => {
            if (await defragRepository()) {
                await appContext.updateBackendState();
            }
            await activityContext.update();
        });
    }

    function openConfirmationDialog(label: string, description: string[], action: () => void) {
        setConfirmationDialog({
            open: true,
            close: () => setConfirmationDialog({...confirmationDialog, open: false}),
            action: action,
            label: label,
            dialogText: description
        });
    }

    return (
        <Box sx={{display: 'flex'}}>
            <AppBar position="absolute" open={state.open || props.disallowClose}>
                {props.processing &&
                    <LinearProgress style={{position: "fixed", width: "100%", top: "0"}}/>
                }
                <Toolbar
                    sx={{
                        pr: '24px', // keep right padding when drawer closed
                    }}
                    style={{paddingRight: "8px"}}
                >
                    <IconButton
                        edge="start"
                        color="inherit"
                        aria-label="open drawer"
                        onClick={toggleDrawer}
                        sx={{
                            marginRight: '36px',
                            ...((state.open || props.disallowClose) && {display: 'none'}),
                        }}
                    >
                        <MenuIcon/>
                    </IconButton>
                    <Box style={{...((state.open || props.disallowClose) && {display: 'none'})}} padding={0} margin={0}
                         marginRight={1}
                         component={"img"} src={theme.invertedLogo} maxHeight={40}/>
                    <Typography
                        id={"currentProgress"}
                        component="h1"
                        variant="h6"
                        color="inherit"
                        noWrap
                        sx={{
                            flexGrow: 1
                        }}
                    >
                        {props.title}
                    </Typography>
                    <Box sx={{flexGrow: 1, display: {xs: 'none', md: 'flex'}}}>
                    </Box>

                    {props.cancelButton &&
                        <CancelButton {...props.cancelButton}/>
                    }
                    {props.acceptButton &&
                        <AcceptButton {...props.acceptButton}/>
                    }
                    <IconButton
                        color="inherit"
                        onClick={(e) => handleClick(e)}
                        style={{opacity: 0.6, padding: "0"}}
                        size="large"
                    >
                        <MoreVert/>
                    </IconButton>
                </Toolbar>
                <Menu
                    anchorEl={anchorEl}
                    open={open}
                    onClick={handleClose}
                    onClose={handleClose}>
                    <MenuItem onClick={theme.toggle} sx={{justifyContent: "center"}}>
                        {theme.mode === "dark" && <NightsStay/>}
                        {theme.mode !== "dark" && <WbSunny/>}
                    </MenuItem>
                    <Divider>Maintenance</Divider>
                    <MenuItem title={"Apply retention policy right now on local repository"} disabled={busyState}
                              onClick={() => openConfirmationDialog("Trim Repository",
                                  [
                                      "Apply retention policy right now on local repository and remove any unused backup data?",
                                      "This operation can take a considerable amount of time to complete and usually runs at the end of each completed backup."
                                  ],
                                  () => executeTrimRepository())}>
                        Trim repository
                    </MenuItem>
                    <MenuItem title={"Rewrite backup logs based on current backup contents"}
                              disabled={busyState || !!appContext.selectedSource}
                              onClick={() => openConfirmationDialog("Optimize Logs",
                                  [
                                      "Rewrite backup logs based on current backup contents?",
                                      "Performing this operation on a regular basis ensures that you can do an efficient recovery in case you wish to restore a backup from scratch.",
                                      "This operation can take a considerable amount of time to complete and usually runs once a month based on default settings."
                                  ],
                                  () => executeOptimizeLogs())}>
                        Optimize backup logs
                    </MenuItem>
                    <MenuItem title={"Optimize storage for local repository"} disabled={busyState}
                              onClick={() => openConfirmationDialog("Defrag Local Repository",
                                  [
                                      "Optimize storage for local repository?",
                                      "Performing this operation will ensure that your local repository does not take up a lot of unused disk space.",
                                      "This operation can take a considerable amount of time and does never run automatically. It is only necessary if you have removed a substantial portion of your backup."
                                  ],
                                  () => executeDefragRepository())}>
                        Defrag local repository
                    </MenuItem>
                    <MenuItem title={"Rebuild local repository from backup logs"} disabled={busyState}
                              onClick={() => setRepairDialogOpen(true)}>
                        Repair local repository
                    </MenuItem>
                    <MenuItem title={"Validate that at your backed up files still have valid storage"}
                              disabled={busyState}
                              onClick={() => openConfirmationDialog("Validate Backup Blocks",
                                  [
                                      "Validate that at your backed up files still have valid storage?",
                                      "Performing this operation will ensure that all your backed up files still have at least one valid storage location and also verifies that the required files exist in the destination (The backup contents is not validated though).",
                                      "This operation can take a considerable amount of time and runs once a month based on default settings except for the destination validations which is only run when this is invoked manually.",
                                      "If you cancel the operation before it completes, it will resume from where it left off the next time it is invoked."
                                  ],
                                  () => executeValidateBlocks())}>
                        Validate storage
                    </MenuItem>
                    <Divider>Support</Divider>
                    <MenuItem title={"Generate support bundle"} onClick={() => setShowSupportBundle(true)}>
                        Support bundle
                    </MenuItem>
                    <MenuItem title={"Check new version"} onClick={() => makeApiCall("service/version")}>
                        Check new version
                    </MenuItem>
                    <MenuItem onClick={() => window.open("https://underscorebackup.com")}>
                        About Underscore Backup
                    </MenuItem>
                </Menu>
            </AppBar>
            <Drawer variant="permanent" open={state.open || props.disallowClose}>
                <Toolbar
                    sx={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'flex-end',
                        px: [1],
                    }}
                >
                    <Box component={"img"} src={theme.logo} maxHeight={40}/>
                    {
                        props.disallowClose ?
                            <IconButton>
                                <svg style={{width: "1em", height: "1em"}}>
                                </svg>
                            </IconButton>
                            :
                            <IconButton onClick={toggleDrawer}>
                                <ChevronLeftIcon/>
                            </IconButton>
                    }
                </Toolbar>
                <Divider/>

                {props.navigation}

                <Slide direction="right" in={(state.open || props.disallowClose) && !state.slideAnimation}>
                    <Typography
                        color="darkgray"
                        noWrap
                        component={"span"}
                        fontSize={14}
                        style={{
                            position: "absolute",
                            bottom: "8px",
                            right: "8px",
                            width: "220px",
                            textAlign: "right"
                        }}
                    >
                        {appContext.backendState && appContext.backendState.newVersion &&
                            <>
                                <Typography
                                    color={theme.theme.palette.text.primary}
                                    style={{fontWeight: "bold"}}
                                >
                                    New version available now!
                                </Typography>
                                <br/>
                                <Button variant={"contained"}
                                        onClick={() => setState({...state, showNewVersion: true})}>
                                    Get version {appContext.backendState.newVersion.version}
                                </Button>
                                <br/>
                                <br/>
                            </>
                        }
                        {appContext.backendState &&
                            <span>Version <span
                                style={{fontWeight: "bold"}}>{appContext.backendState.version}</span></span>
                        }
                    </Typography>
                </Slide>
            </Drawer>
            <Box
                component="main"
                sx={{
                    flexGrow: 1,
                    height: '100vh',
                    overflow: 'hidden',
                }}>
                <Toolbar/>
                <Box
                    component="main"
                    sx={{
                        backgroundColor: (theme) =>
                            theme.palette.mode === 'light'
                                ? theme.palette.grey[100]
                                : theme.palette.grey[900],
                        height: '100vh',
                        overflow: 'auto',
                    }}>
                    <form
                        onSubmit={(e) => {
                            e.preventDefault()
                        }}
                        noValidate
                        autoComplete="off"
                    >
                        <Box sx={{margin: 6}}>
                            {props.children}
                        </Box>
                    </form>
                    <div style={{height: "32px"}}/>
                </Box>
            </Box>
            {appContext.backendState && appContext.backendState.newVersion &&
                <Dialog open={state.showNewVersion} onClose={() => setState({...state, showNewVersion: false})}>
                    <DialogTitle>Version {appContext.backendState?.newVersion?.version}: {appContext.backendState?.newVersion?.name}</DialogTitle>
                    <DialogContent dividers>
                        <DialogContentText>
                            Released {new Date(appContext.backendState?.newVersion?.releaseDate * 1000 as number).toLocaleString()}

                            <div dangerouslySetInnerHTML={{
                                __html:
                                    marked(appContext.backendState?.newVersion?.body as string)
                            }}/>
                        </DialogContentText>
                    </DialogContent>
                    <DialogActions>
                        <Button autoFocus={true} onClick={() => {
                            window.open(appContext.backendState?.newVersion?.changeLog, '_blank')
                        }}>Changelog</Button>
                        <div style={{flex: '1 0 0'}}/>
                        <Button onClick={() => setState({...state, showNewVersion: false})}>Cancel</Button>
                        <Button variant={"contained"} autoFocus={true} onClick={() => {
                            window.open(appContext.backendState?.newVersion?.download?.url, '_blank')
                            setState({...state, showNewVersion: false});
                        }}>Download
                            ({Math.round((appContext.backendState?.newVersion?.download?.size as number) / 1024 / 1024)}mb)</Button>
                    </DialogActions>
                </Dialog>
            }
            <SupportBundleDialog open={showSupportBundle}
                                 onClose={() => setShowSupportBundle(false)}/>
            <PasswordDialog action={(password) => repairRepositoryNow(password)}
                            label={"Repair local repository"}
                            open={repairDialogOpen} close={() => setRepairDialogOpen(false)}
                            dialogText={"You need to enter your backup password to repair your local repository."}/>
            <ConfirmationDialog {...confirmationDialog}/>
        </Box>
    )
}