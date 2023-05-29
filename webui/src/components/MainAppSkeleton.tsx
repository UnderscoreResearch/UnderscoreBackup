import Box from "@mui/material/Box";
import {
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    LinearProgress
} from "@mui/material";
import Toolbar from "@mui/material/Toolbar";
import IconButton from "@mui/material/IconButton";
import MenuIcon from "@mui/icons-material/Menu";
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
}

interface MainAppSkeletonState {
    open: boolean;
    slideAnimation: boolean;
    showNewVersion: boolean;
}

export function MainAppSkeleton(props: MainAppSkeletonProps) {
    const appConfig = useApplication();
    const theme = useTheme();

    const [state, setState] = React.useState<MainAppSkeletonState>({
        open: window.localStorage.getItem("open") !== "false",
        slideAnimation: false,
        showNewVersion: false
    });

    function toggleDrawer() {
        setState((oldState) => ({...oldState, open: !state.open}));
        window.localStorage.setItem("open", (!state.open).toString());
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
                        onClick={theme.toggle}
                        style={{opacity: 0.6, padding: "0"}}
                        size="large"
                    >
                        {theme.mode === "dark" && <NightsStay/>}

                        {theme.mode !== "dark" && <WbSunny/>}
                    </IconButton>
                </Toolbar>
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
                        {appConfig.backendState && appConfig.backendState.newVersion &&
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
                                    Get version {appConfig.backendState.newVersion.version}
                                </Button>
                                <br/>
                                <br/>
                            </>
                        }
                        {appConfig.backendState &&
                            <span>Version <span
                                style={{fontWeight: "bold"}}>{appConfig.backendState.version}</span></span>
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
            {appConfig.backendState && appConfig.backendState.newVersion &&
                <Dialog open={state.showNewVersion} onClose={() => setState({...state, showNewVersion: false})}>
                    <DialogTitle>Version {appConfig.backendState?.newVersion?.version}: {appConfig.backendState?.newVersion?.name}</DialogTitle>
                    <DialogContent dividers>
                        <DialogContentText>
                            Released {new Date(appConfig.backendState?.newVersion?.releaseDate * 1000 as number).toLocaleString()}

                            <div dangerouslySetInnerHTML={{
                                __html:
                                    marked(appConfig.backendState?.newVersion?.body as string)
                            }}/>
                        </DialogContentText>
                    </DialogContent>
                    <DialogActions>
                        <Button autoFocus={true} onClick={() => {
                            window.open(appConfig.backendState?.newVersion?.changeLog, '_blank')
                        }}>Changelog</Button>
                        <div style={{flex: '1 0 0'}}/>
                        <Button onClick={() => setState({...state, showNewVersion: false})}>Cancel</Button>
                        <Button variant={"contained"} autoFocus={true} onClick={() => {
                            window.open(appConfig.backendState?.newVersion?.download?.url, '_blank')
                            setState({...state, showNewVersion: false});
                        }}>Download
                            ({Math.round((appConfig.backendState?.newVersion?.download?.size as number) / 1024 / 1024)}mb)</Button>
                    </DialogActions>
                </Dialog>
            }
        </Box>
    )
}