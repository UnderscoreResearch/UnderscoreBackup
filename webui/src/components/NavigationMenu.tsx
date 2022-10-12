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
import {BackupConfiguration} from "../api";

function MyListItemButton(props: {
    page: string,
    config: BackupConfiguration,
    children: React.ReactNode,
    id: string,
    disabled?: boolean
}) {
    var currentPage = location.href;
    if (currentPage.endsWith("/")) {
        if (props.config.sets.length > 0)
            currentPage += "status";
        else
            currentPage += "sets";
    }
    return <ListItemButton id={props.id} disabled={props.disabled} component={Link} to={props.page}
                           selected={currentPage.endsWith(props.page)}>
        {props.children}
    </ListItemButton>
}

export default function NavigationMenu(props: {
    config: BackupConfiguration,
    hasKey: boolean,
    allowRestore: boolean,
    allowBackup: boolean,
    unresponsive: boolean,
    hasSource: boolean
}) {
    var firstTime = Object.keys(props.config.destinations).length == 0 || !props.hasKey;

    if (firstTime && !props.unresponsive) {
        return <List component="nav">
            <ListItemButton selected={true}>
                <ListItemIcon>
                    <Settings/>
                </ListItemIcon>
                <ListItemText primary="Initial Setup"/>
            </ListItemButton>
        </List>
    }
    return <List component="nav">
        <MyListItemButton page="status" config={props.config} id="pageStatus">
            <ListItemIcon>
                <Dashboard/>
            </ListItemIcon>
            <ListItemText primary="Status"/>
        </MyListItemButton>
        <Divider sx={{my: 1}}/>
        <MyListItemButton page="sets" config={props.config} disabled={!props.allowBackup || props.hasSource}
                          id="pageSets">
            <ListItemIcon>
                <AccountTree/>
            </ListItemIcon>
            <ListItemText primary="Backup Sets"/>
        </MyListItemButton>
        <MyListItemButton page="destinations" config={props.config} disabled={!props.allowBackup}
                          id="pageDestinations">
            <ListItemIcon>
                <CloudUpload/>
            </ListItemIcon>
            <ListItemText primary="Destinations"/>
        </MyListItemButton>
        <MyListItemButton page="settings" config={props.config} disabled={!props.allowBackup || props.hasSource}
                          id="pageSettings">
            <ListItemIcon>
                <Settings/>
            </ListItemIcon>
            <ListItemText primary="Settings"/>
        </MyListItemButton>
        <Divider sx={{my: 1}}/>
        <MyListItemButton page="sources" config={props.config} disabled={!props.allowBackup || props.hasSource}
                          id="pageSources">
            <ListItemIcon>
                <AutoAwesomeMotion/>
            </ListItemIcon>
            <ListItemText primary="Other Sources"/>
        </MyListItemButton>
        <Divider sx={{my: 1}}/>
        <MyListItemButton page="restore" config={props.config} disabled={!props.allowRestore} id="pageRestore">
            <ListItemIcon>
                <CloudDownload/>
            </ListItemIcon>
            <ListItemText primary="Restore"/>
        </MyListItemButton>
    </List>;
}