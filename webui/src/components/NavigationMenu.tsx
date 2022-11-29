import List from "@mui/material/List";
import Divider from "@mui/material/Divider";
import * as React from "react";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemIcon from "@mui/material/ListItemIcon";
import CloudUpload from "@mui/icons-material/CloudUpload";
import ListItemText from "@mui/material/ListItemText";
import AccountTree from "@mui/icons-material/AccountTree";
import Settings from "@mui/icons-material/Settings";
import CloudDownload from "@mui/icons-material/CloudDownload";
import AutoAwesomeMotion from "@mui/icons-material/AutoAwesomeMotion";
import Dashboard from "@mui/icons-material/Dashboard";
import {Link} from "react-router-dom";
import {Share} from "@mui/icons-material";

function MyListItemButton(props: {
    page: string,
    id: string,
    disabled: boolean,
    children: JSX.Element[]
}) {
    var currentPage = location.href;
    if (currentPage.endsWith("/")) {
        currentPage += "status";
    }
    return <ListItemButton id={props.id} disabled={props.disabled} component={Link} to={props.page}
                           selected={currentPage.endsWith(props.page)}>
        {props.children}
    </ListItemButton>
}

export interface NavigationProps {
    unresponsive: boolean,
    loading: boolean,
    firstTime: boolean,
    status: boolean,
    sets: boolean,
    destinations: boolean,
    settings: boolean,
    sources: boolean,
    share: boolean,
    restore: boolean
}

export default function NavigationMenu(props: NavigationProps) {
    if (props.firstTime) {
        return <List component="nav">
            <ListItemButton selected={true} disabled={props.unresponsive}>
                <ListItemIcon>
                    <Settings/>
                </ListItemIcon>
                <ListItemText primary="Initial Setup"/>
            </ListItemButton>
        </List>
    }
    return <List component="nav">
        <MyListItemButton page="status" disabled={!props.status || props.unresponsive || props.loading} id="pageStatus">
            <ListItemIcon>
                <Dashboard/>
            </ListItemIcon>
            <ListItemText primary="Status"/>
        </MyListItemButton>
        <Divider sx={{my: 1}}/>
        <MyListItemButton page="sets" disabled={!props.sets || props.unresponsive || props.loading} id="pageSets">
            <ListItemIcon>
                <AccountTree/>
            </ListItemIcon>
            <ListItemText primary="Backup Sets"/>
        </MyListItemButton>
        <MyListItemButton page="destinations" disabled={!props.destinations || props.unresponsive || props.loading}
                          id="pageDestinations">
            <ListItemIcon>
                <CloudUpload/>
            </ListItemIcon>
            <ListItemText primary="Destinations"/>
        </MyListItemButton>
        <MyListItemButton page="settings" disabled={!props.settings || props.unresponsive || props.loading}
                          id="pageSettings">
            <ListItemIcon>
                <Settings/>
            </ListItemIcon>
            <ListItemText primary="Settings"/>
        </MyListItemButton>
        <Divider sx={{my: 1}}/>
        <MyListItemButton page="sources" disabled={!props.sources || props.unresponsive || props.loading}
                          id="pageSources">
            <ListItemIcon>
                <AutoAwesomeMotion/>
            </ListItemIcon>
            <ListItemText primary="Other Sources"/>
        </MyListItemButton>
        <MyListItemButton page="share" disabled={!props.share || props.unresponsive || props.loading} id="pageShare">
            <ListItemIcon>
                <Share/>
            </ListItemIcon>
            <ListItemText primary="Shares"/>
        </MyListItemButton>
        <Divider sx={{my: 1}}/>
        <MyListItemButton page="restore" disabled={!props.restore || props.unresponsive || props.loading}
                          id="pageRestore">
            <ListItemIcon>
                <CloudDownload/>
            </ListItemIcon>
            <ListItemText primary="Restore"/>
        </MyListItemButton>
    </List>;
}