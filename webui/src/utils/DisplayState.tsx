import {NavigationProps} from "../components/NavigationMenu";
import {ApplicationContext} from "./ApplicationContext";
import {ActivityContext} from "./ActivityContext";
import {deepEqual} from "fast-equals";
import {Location} from "react-router-dom";

export interface DisplayState {
    navigation: NavigationProps,
    statusTitle: string,
    processing: boolean,
    backupInProgress: boolean,
    backupCanStart: boolean,
    restoreInProgress: boolean,
    rebuildInProgress: boolean,
    configChanged: boolean,
    invalidPage: boolean
}


function createDefaultDisplayState(): DisplayState {
    return {
        statusTitle: "Loading",
        backupCanStart: false,
        backupInProgress: false,
        restoreInProgress: false,
        rebuildInProgress: false,
        processing: false,
        configChanged: false,
        invalidPage: false,
        navigation: {
            currentPage: "",
            unresponsive: true,
            destinations: false,
            sets: false,
            restore: false,
            settings: false,
            share: false,
            sources: false,
            status: false
        }
    }
}

function busyStatus(ret: DisplayState, statusTitle: string) {
    ret.statusTitle = statusTitle;
    ret.navigation = {
        status: true,
        share: false,
        sets: false,
        currentPage: ret.navigation.currentPage,
        unresponsive: ret.navigation.unresponsive,
        destinations: false,
        sources: false,
        settings: false,
        restore: false
    };
}

export function calculateDisplayState(appContext: ApplicationContext,
                                      activityContext: ActivityContext,
                                      validConfig: boolean,
                                      location: Location): DisplayState {
    const ret = createDefaultDisplayState();
    if (activityContext.unresponsive) {
        ret.statusTitle = "Unresponsive";
        return ret;
    }

    let currentPage: string;
    if (location.pathname.endsWith("/")) {
        currentPage = "status";
    } else {
        currentPage = location.pathname.substring(1);
    }

    ret.configChanged = !deepEqual(appContext.originalConfiguration, appContext.currentConfiguration);

    const unresponsive = appContext.isBusy() || appContext.initialLoad || currentPage.startsWith("authorizeaccept");

    ret.navigation = {
        status: true,
        restore: !ret.configChanged && validConfig,
        currentPage: currentPage,
        sets: true,
        settings: true,
        sources: true,
        unresponsive: unresponsive,
        destinations: true,
        share: true,
    }

    ret.backupInProgress = true;
    ret.processing = true;

    if (activityContext.activity.some(item => item.code.startsWith("BACKUP_"))) {
        ret.statusTitle = "Backup In Progress";
    } else if (activityContext.activity.some(item => item.code.startsWith("TRIMMING_"))) {
        ret.statusTitle = "Trimming Repository";
    } else if (activityContext.activity.some(item => item.code.startsWith("VALIDATE_"))) {
        ret.statusTitle = "Validating Repository";
    } else if (activityContext.activity.some(item => item.code.startsWith("CONTINUOUS_"))) {
        ret.statusTitle = "Listening For Changes";
    } else {
        ret.backupInProgress = false;

        if (activityContext.activity.some(item => item.code.startsWith("UPLOAD_PENDING"))) {
            ret.statusTitle = "Initializing";
        } else if (activityContext.activity.some(item => item.code.startsWith("ACTIVATING_SHARES_"))) {
            busyStatus(ret, "Activating Shares");
        } else if (activityContext.activity.some(item => item.code.startsWith("DEACTIVATING_SHARES_"))) {
            busyStatus(ret, "Deactivating Shares");
        } else {
            if (appContext.selectedSource) {
                ret.navigation.sets =
                    ret.navigation.sources =
                        ret.navigation.share =
                            ret.navigation.settings = false;
                if (activityContext.activity.some(item => item.code.startsWith("REPLAY_"))) {
                    busyStatus(ret, `Syncing Contents From ${appContext.selectedSourceName}`);
                    ret.rebuildInProgress = true;
                } else {

                    if (activityContext.activity.some(item => item.code.startsWith("RESTORE_"))) {
                        busyStatus(ret, `Restore From ${appContext.selectedSourceName} In Progress`);
                        ret.restoreInProgress = true;
                    } else {
                        ret.statusTitle = `Browsing ${appContext.selectedSourceName} Contents`;
                        ret.processing = false;
                    }
                }
            } else {
                if (activityContext.activity.some(item => item.code.startsWith("RESTORE_"))) {
                    busyStatus(ret, "Restore In Progress");
                    ret.restoreInProgress = true;
                } else if (activityContext.activity.some(item => item.code.startsWith("REPLAY_"))) {
                    busyStatus(ret, "Replaying From Backup");
                    ret.rebuildInProgress = true;
                } else if (activityContext.activity.some(item => item.code.startsWith("OPTIMIZING_"))) {
                    busyStatus(ret, "Optimizing Log");
                } else {
                    ret.statusTitle = "Currently Inactive";

                    ret.backupCanStart = true;
                    ret.processing = false;
                }
            }
        }
    }

    switch (currentPage) {
        case "status":
            if (!ret.navigation.status)
                ret.invalidPage = ret.navigation.unresponsive = true;
            break;
        case "restore":
            if (!ret.navigation.restore)
                ret.invalidPage = ret.navigation.unresponsive = true;
            break;
        case "sets":
            if (!ret.navigation.sets)
                ret.invalidPage = ret.navigation.unresponsive = true;
            break;
        case "destinations":
            if (!ret.navigation.destinations)
                ret.invalidPage = ret.navigation.unresponsive = true;
            break;
        case "settings":
            if (!ret.navigation.settings)
                ret.invalidPage = ret.navigation.unresponsive = true;
            break;
        case "share":
            if (!ret.navigation.share)
                ret.invalidPage = ret.navigation.unresponsive = true;
            break;
        case "sources":
            if (!ret.navigation.sources)
                ret.invalidPage = ret.navigation.unresponsive = true;
            break;
        case "authorizeaccept":
            break;
        default:
            ret.invalidPage = ret.navigation.unresponsive = true;
            break;
    }

    if (appContext.isBusy() && !ret.processing) {
        ret.processing = true;
        ret.statusTitle = "Processing";
    }

    return ret;
}