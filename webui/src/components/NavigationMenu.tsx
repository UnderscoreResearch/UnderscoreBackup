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
    currentPage: string,
    children: JSX.Element[]
}) {
    return <ListItemButton id={props.id} disabled={props.disabled} component={Link} to={props.page}
                           selected={props.currentPage === props.page}>
        {props.children}
    </ListItemButton>
}

export interface NavigationProps {
    unresponsive: boolean,
    status: boolean,
    sets: boolean,
    destinations: boolean,
    settings: boolean,
    sources: boolean,
    share: boolean,
    restore: boolean,
    currentPage: string
}

export default function NavigationMenu(props: NavigationProps) {
    return <List component="nav">
        <MyListItemButton page="status" currentPage={props.currentPage} disabled={!props.status || props.unresponsive}
                          id="pageStatus">
            <ListItemIcon>
                <Dashboard/>
            </ListItemIcon>
            <ListItemText primary="Status"/>
        </MyListItemButton>
        <Divider sx={{my: 1}}/>
        <MyListItemButton page="sets" currentPage={props.currentPage} disabled={!props.sets || props.unresponsive}
                          id="pageSets">
            <ListItemIcon>
                <AccountTree/>
            </ListItemIcon>
            <ListItemText primary="Backup Sets"/>
        </MyListItemButton>
        <MyListItemButton page="destinations" currentPage={props.currentPage}
                          disabled={!props.destinations || props.unresponsive}
                          id="pageDestinations">
            <ListItemIcon>
                <CloudUpload/>
            </ListItemIcon>
            <ListItemText primary="Destinations"/>
        </MyListItemButton>
        <MyListItemButton page="settings" currentPage={props.currentPage}
                          disabled={!props.settings || props.unresponsive}
                          id="pageSettings">
            <ListItemIcon>
                <Settings/>
            </ListItemIcon>
            <ListItemText primary="Settings"/>
        </MyListItemButton>
        <Divider sx={{my: 1}}/>
        <MyListItemButton page="sources" currentPage={props.currentPage} disabled={!props.sources || props.unresponsive}
                          id="pageSources">
            <ListItemIcon>
                <AutoAwesomeMotion/>
            </ListItemIcon>
            <ListItemText primary="Other Sources"/>
        </MyListItemButton>
        <MyListItemButton page="share" currentPage={props.currentPage} disabled={!props.share || props.unresponsive}
                          id="pageShare">
            <ListItemIcon>
                <Share/>
            </ListItemIcon>
            <ListItemText primary="Shares"/>
        </MyListItemButton>
        <Divider sx={{my: 1}}/>
        <MyListItemButton page="restore" currentPage={props.currentPage} disabled={!props.restore || props.unresponsive}
                          id="pageRestore">
            <ListItemIcon>
                <CloudDownload/>
            </ListItemIcon>
            <ListItemText primary="Restore"/>
        </MyListItemButton>
    </List>;
}