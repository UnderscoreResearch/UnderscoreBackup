import Paper from "@mui/material/Paper";
import Typography from "@mui/material/Typography";
import {Grid, List, ListItem, Stack} from "@mui/material";
import ListItemIcon from "@mui/material/ListItemIcon";
import {Check as CheckIcon} from "@mui/icons-material";
import ListItemText from "@mui/material/ListItemText";
import ServiceAuthentication from "../ServiceAuthentication";
import * as React from "react";
import {useApplication} from "../../utils/ApplicationContext";

const freeFeatures = [
    "Safe encryption key recovery",
    "Easily manage backup sources",
    "Create and accept sharing invites"
]

const premiumFeatures = [
    "Service provided backup storage",
    "Multiple region support to satisfy latency and data governance requirements",
    "Easy and secure zero trust sharing without additional credential setup"
]

export interface ServicePageProps {
    onPageChange: (page: string) => void,
    onDisconnect: () => void
}

export function ServicePage(props: ServicePageProps) {
    const appContext = useApplication();

    return <Stack spacing={2} style={{width: "100%"}}>
        <Paper sx={{
            p: 2,
        }}>
            <Typography variant="h3" component="div" marginBottom={"16px"}>
                Connect with online service?
            </Typography>
            <Typography variant="h6" component="div">
                Included for free
            </Typography>
            <List>
                {freeFeatures.map(item =>
                    <ListItem key={freeFeatures.indexOf(item)}>
                        <ListItemIcon
                            sx={{
                                minWidth: "34px",
                                color: "success.main",
                            }}
                        >
                            <CheckIcon/>
                        </ListItemIcon>
                        <ListItemText>
                            {item}
                        </ListItemText>
                    </ListItem>)
                }
            </List>
            <Typography variant="h6" component="div">
                Additional features with paid subscription
            </Typography>
            <List>
                {premiumFeatures.map(item =>
                    <ListItem key={premiumFeatures.indexOf(item)}>
                        <ListItemIcon
                            sx={{
                                minWidth: "34px",
                                color: "success.main",
                            }}
                        >
                            <CheckIcon/>
                        </ListItemIcon>
                        <ListItemText>
                            {item}
                        </ListItemText>
                    </ListItem>)
                }
            </List>
            <Grid container spacing={2} alignItems={"center"}>
                <ServiceAuthentication includeSkip={true} backendState={appContext.backendState}
                                       updatedToken={props.onDisconnect}
                                       needSubscription={false}
                                       authRedirectLocation={"/source"}
                                       onSkip={() =>
                                           appContext.backendState.serviceConnected ?
                                               props.onPageChange("source") :
                                               props.onPageChange("destination")}/>
            </Grid>
        </Paper>
    </Stack>;
}