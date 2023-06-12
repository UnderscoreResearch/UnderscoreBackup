import React from "react";
import {BrowserRouter} from "react-router-dom";
import {useSnackbar, VariantType} from "notistack";
import {Backdrop, CircularProgress} from "@mui/material";
import {useApplication} from "./utils/ApplicationContext";
import {useActivity} from "./utils/ActivityContext";
import {MainAppSkeleton} from "./components/MainAppSkeleton";

let internalDisplayMessage: (message: string, variant: VariantType) => void;

export function DisplayMessage(message: string, variant: VariantType = "error") {
    internalDisplayMessage(message, variant);
}

const MainApp = React.lazy(() => import('./MainApp'));
const InitialSetup = React.lazy(() => import('./InitialSetup'));

function Loading(props: { open: boolean }) {
    return <Backdrop
        sx={{color: '#fff', zIndex: (theme) => theme.zIndex.drawer + 1}}
        id={"loading"}
        open={props.open}>
        <CircularProgress color="inherit" size={"10em"}/>
    </Backdrop>
}

const firstPath = `/${window.location.pathname.split('/')[1]}/`;
export default function App() {
    const snackbar = useSnackbar();
    const appConfig = useApplication();
    const activity = useActivity();

    internalDisplayMessage = (message: string, variant: VariantType) => {
        snackbar.enqueueSnackbar(message, {variant: variant});
    }

    return <>
        <BrowserRouter basename={firstPath}>
            {activity.unresponsive || activity.loading || appConfig.initialLoad ?
                <MainAppSkeleton title={activity.unresponsive ? "Unresponsive" : "Loading"}
                                 processing={false}
                                 navigation={<></>} disallowClose={false}/>
                :
                <React.Suspense fallback={<Loading open={true}/>}>
                    {appConfig.setupComplete ?
                        <MainApp/>
                        :
                        <InitialSetup/>
                    }
                </React.Suspense>
            }
            <Loading open={appConfig.isBusy() || activity.unresponsive || activity.loading}/>
        </BrowserRouter>
    </>
}