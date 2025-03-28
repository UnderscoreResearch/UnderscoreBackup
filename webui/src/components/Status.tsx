import * as React from "react";
import {activateShares, repairRepository, StatusLine} from "../api";
import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Button,
    Link,
    Paper,
    Stack,
    Table,
    TableBody,
    TableContainer,
    Typography
} from "@mui/material";
import LogTable from "./LogTable";
import DividerWithText from "../3rdparty/react-js-cron-mui/components/DividerWithText";
import {StatusRow, StatusRowProps} from "./StatusRow";
import {ExpandMore} from "@mui/icons-material";
import {useActivity} from "../utils/ActivityContext";
import {useApplication} from "../utils/ApplicationContext";
import PasswordDialog from "./PasswordDialog";

export interface StatusProps {
    status: StatusLine[]
}

export interface StatusState {
    details: boolean
}

const LAST_STATS: string[] = [
    "REPOSITORY_INFO_CURRENT_FILES",
    "REPOSITORY_INFO_FILE_VERSIONS",
    "REPOSITORY_INFO_TOTAL_SIZE",
    "REPOSITORY_INFO_CURRENT_SIZE",
    "REPOSITORY_INFO_TIMESTAMP"
];

const IMPORTANT_CODES = [
    "MEMORY_HIGH",
    "REPOSITORY_ERROR_DETECTED",
    "SHARE_ACTIVATION_NEEDED",
    "VALIDATE_DESTINATION_BLOCKS",
    "VALIDATE_STEPS",
    "VALIDATE_MISSING_DESTINATION_BLOCKS",
    "VALIDATE_REFRESH",
    "BACKUP_DURATION",
    "RESTORE_DURATION",
    "TRIMMING_STEPS",
    "REPLAY_LOG_PROCESSED_FILES",
    "REPAIRING_REPOSITORY_PROCESSED_FILES",
    "DEACTIVATING_SHARES_PROCESSED_STEPS",
    "ACTIVATING_SHARES_PROCESSED_STEPS",
    "OPTIMIZING_LOG_PROCESSED_STEPS",
    "RE-KEYING_LOG_PROCESSED_STEPS",
    "UPGRADE_PROCESSED_STEPS",
    "CONTINUOUS_BACKUP_FILES",
    "CONTINUOUS_BACKUP_SIZE",
    "COMPLETED_OBJECTS",
    "COMPLETED_SIZE",
    "UPLOADED_THROUGHPUT",
    "RESTORED_OBJECTS",
    "RESTORED_SIZE",
    "RESTORED_THROUGHPUT",
    "PROCESSED_PATH"
];

interface ActionButtonProps {
    action: (password: string) => void,
    label: string,
    color?: 'inherit' | 'primary' | 'secondary' | 'success' | 'error' | 'info' | 'warning'
    dialogText: string
}

function ActionSharesButton(props: ActionButtonProps) {
    const [show, setShow] = React.useState<boolean>(false);

    return <>
        <Button variant="contained" color={props.color} onClick={() => {
            setShow(true)
        }}>{props.label}</Button>
        <PasswordDialog open={show} close={() => setShow(false)} label={props.label} action={props.action}
                        dialogText={props.dialogText}/>
    </>
}

function ActivateSharesButton() {
    const appContext = useApplication();
    const activityContext = useActivity();

    async function activateSharesNow(password: string) {
        await appContext.busyOperation(async () => {
            if (await activateShares(password)) {
                await appContext.updateBackendState();
            }
            await activityContext.update();
        });
    }

    return <ActionSharesButton action={(password) => activateSharesNow(password)} label={"Activate shares"}
                               dialogText={"You need to enter your backup password to activate shares."}/>
}

function RepairRepositoryButton() {
    const appContext = useApplication();
    const activityContext = useActivity();

    async function repairRepositoryNow(password: string) {
        await appContext.busyOperation(async () => {
            if (await repairRepository(password)) {
                await appContext.updateBackendState();
            }
            await activityContext.update();
        });
    }

    return <ActionSharesButton action={(password) => repairRepositoryNow(password)}
                               label={"Repair local repository"} color={"error"}
                               dialogText={"You need to enter your backup password to repair your local repository."}/>
}

function importantProperties(row: StatusLine, details: boolean): StatusRowProps {
    const props: StatusRowProps = {row: row};
    switch (row.code) {
        case "MEMORY_HIGH":
            props.dangerous = true;
            props.action = <Link rel="noreferrer" target="_blank" style={{color: "inherit"}}
                                 href={`https://underscorebackup.com/blog/2023/02/how-to-change-memory-configuration.html`}
                                 underline={"hover"}>How to fix</Link>
            break;
        case "SHARE_ACTIVATION_NEEDED":
            props.action = <ActivateSharesButton/>
            break;
        case "REPOSITORY_ERROR_DETECTED":
            props.action = <RepairRepositoryButton/>
            break;
        case "PROCESSED_PATH":
            props.doubleLine = true;
            break;
        case "TRIMMING_STEPS":
        case "VALIDATE_STEPS":
        case "UPGRADE_PROCESSED_STEPS":
        case "REPLAY_LOG_PROCESSED_FILES":
        case "REPAIRING_REPOSITORY_PROCESSED_FILES":
        case "DEACTIVATING_SHARES_PROCESSED_STEPS":
        case "ACTIVATING_SHARES_PROCESSED_STEPS":
        case "OPTIMIZING_LOG_PROCESSED_STEPS":
        case "RE-KEYING_LOG_PROCESSED_STEPS":
        case "VALIDATE_DESTINATION_BLOCKS":
            if (!details) {
                props.etaOnly = true;
            }
            break;
    }
    return props;
}

export default function Status(props: StatusProps) {
    const items = [...props.status];
    const [state, setState] = React.useState<StatusState>({details: window.localStorage.getItem("details") === "true"});

    const activeItems = items.filter(item => item.code.startsWith("DOWNLOADED_ACTIVE") || item.code.startsWith("UPLOADED_ACTIVE"));
    const importantItems: StatusLine[] = [];
    const statsItems: StatusLine[] = [];
    const scheduledItems: StatusLine[] = [];
    const statusItems = items.filter(item => {
        if (IMPORTANT_CODES.indexOf(item.code) >= 0) {
            importantItems.push(item);
            return false;
        }
        if (LAST_STATS.indexOf(item.code) >= 0) {
            statsItems.push(item);
            return false;
        }
        if (item.code.startsWith("SCHEDULED_BACKUP_")) {
            scheduledItems.push(item);
            return false;
        }
        if (item.code === "PAUSED") {
            return false;
        }
        return !(item.code.startsWith("DOWNLOADED_ACTIVE") || item.code.startsWith("UPLOADED_ACTIVE"))
    });
    importantItems.sort((a, b) => IMPORTANT_CODES.indexOf(a.code) - IMPORTANT_CODES.indexOf(b.code));
    scheduledItems.sort((a, b) => (a.value ?? 0) - (b.value ?? 0));
    statsItems.sort((a, b) => IMPORTANT_CODES.indexOf(a.code) - IMPORTANT_CODES.indexOf(b.code));
    statusItems.sort((a, b) => a.message.localeCompare(b.message));
    activeItems.sort((a, b) => a.message.localeCompare(b.message));

    return <Stack spacing={2}>

        {importantItems.length > 0 &&
            <>
                <TableContainer component={Paper}>
                    <Table sx={{minWidth: 650}} aria-label="simple table">
                        <TableBody>
                            {importantItems.map((row) => <StatusRow
                                key={row.code} {...importantProperties(row, state.details)}/>)}
                        </TableBody>
                    </Table>
                </TableContainer>
            </>
        }

        {statsItems.length > 0 &&
            <>
                <DividerWithText>Last completed backup stats</DividerWithText>
                <TableContainer component={Paper}>
                    <Table sx={{minWidth: 650}} aria-label="simple table">
                        <TableBody>
                            {statsItems.map((row) => <StatusRow
                                key={row.code} {...importantProperties(row, state.details)}/>)}
                        </TableBody>
                    </Table>
                </TableContainer>
            </>
        }

        {activeItems.length > 0 &&
            <>
                <DividerWithText>Active transfers</DividerWithText>
                <TableContainer component={Paper}>
                    <Table sx={{minWidth: 650}} aria-label="simple table">
                        <TableBody>
                            {activeItems.map((row) => <StatusRow key={row.code} row={row}/>)}
                        </TableBody>
                    </Table>
                </TableContainer>
            </>
        }

        {scheduledItems.length > 0 &&
            <>
                <DividerWithText>Scheduled sets</DividerWithText>
                <TableContainer component={Paper}>
                    <Table sx={{minWidth: 650}} aria-label="simple table">
                        <TableBody>
                            {scheduledItems.map((row) => <StatusRow key={row.code} row={row}/>)}
                        </TableBody>
                    </Table>
                </TableContainer>
            </>
        }

        <Paper>
            <Accordion
                expanded={state.details}
                onChange={(event, expanded) => {
                    setState({details: expanded});
                    window.localStorage.setItem("details", expanded.toString())
                }}
                sx={{
                    // Remove shadow
                    boxShadow: "none",
                    // Remove default divider
                    "&:before": {
                        display: "none",
                    }
                }}
            >
                <AccordionSummary expandIcon={<ExpandMore/>}>
                    <Typography>Details</Typography>
                </AccordionSummary>
                <AccordionDetails sx={{
                    margin: 0,
                    padding: 0
                }}>
                    {statusItems.length > 0 &&
                        <>
                            <div style={{paddingLeft: "16px", paddingRight: "16px"}}>
                                <DividerWithText>Additional stats</DividerWithText>
                            </div>
                            <TableContainer>
                                <Table sx={{minWidth: 650}} aria-label="simple table">
                                    <TableBody>
                                        {statusItems.map((row) => <StatusRow key={row.code} row={row}/>)}
                                    </TableBody>
                                </Table>
                            </TableContainer>
                        </>
                    }
                </AccordionDetails>
            </Accordion>
        </Paper>

        <LogTable onlyErrors={!state.details}/>
    </Stack>
}