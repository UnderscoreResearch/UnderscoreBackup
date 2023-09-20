import * as React from "react";
import {activateShares, repairRepository, StatusLine} from "../api";
import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    DialogTitle,
    Link,
    Paper,
    Stack,
    Table,
    TableBody,
    TableContainer,
    TextField,
    Typography
} from "@mui/material";
import LogTable from "./LogTable";
import DividerWithText from "../3rdparty/react-js-cron-mui/components/DividerWithText";
import {StatusRow, StatusRowProps} from "./StatusRow";
import {ExpandMore} from "@mui/icons-material";
import {useActivity} from "../utils/ActivityContext";
import Box from "@mui/material/Box";
import {useApplication} from "../utils/ApplicationContext";

export interface StatusProps {
    status: StatusLine[]
}

export interface StatusState {
    details: boolean
}

const LAST_STATS: string[] = [
    "REPOSITORY_INFO_FILES",
    "REPOSITORY_INFO_FILE_VERSIONS",
    "REPOSITORY_INFO_TOTAL_SIZE"
];

const IMPORTANT_CODES = [
    "MEMORY_HIGH",
    "REPOSITORY_ERROR_DETECTED",
    "SHARE_ACTIVATION_NEEDED",
    "BACKUP_DURATION",
    "RESTORE_DURATION",
    "TRIMMING_STEPS",
    "VALIDATE_STEPS",
    "REPLAY_LOG_PROCESSED_FILES",
    "REPAIRING_REPOSITORY_PROCESSED_FILES",
    "DEACTIVATING_SHARES_PROCESSED_STEPS",
    "ACTIVATING_SHARES_PROCESSED_STEPS",
    "OPTIMIZING_LOG_PROCESSED_STEPS",
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
    const [password, setPassword] = React.useState<string>("");

    return <>
        <Button variant="contained" color={props.color} onClick={() => {
            setShow(true)
        }}>{props.label}</Button>
        <Dialog open={show} onClose={() => setShow(false)}>
            <DialogTitle>Enter Password</DialogTitle>
            <DialogContent>
                <DialogContentText>
                    {props.dialogText}
                </DialogContentText>

                <Box
                    component="div"
                    sx={{
                        '& .MuiTextField-root': {m: 1},
                    }}
                    style={{marginTop: 4, marginLeft: "-8px", marginRight: "8px"}}
                >
                    <TextField label="Password" variant="outlined"
                               fullWidth={true}
                               required={true}
                               value={password}
                               error={!password}
                               id={"password"}
                               type="password"
                               onKeyDown={(e) => {
                                   if (e.key === "Enter" && password.length) {
                                       setShow(false);
                                       props.action(password);
                                   }
                               }}
                               onChange={(e) => setPassword(e.target.value)}/>
                </Box>
            </DialogContent>
            <DialogActions>
                <Button onClick={() => setShow(false)}>Cancel</Button>
                <Button disabled={!password}
                        onClick={() => {
                            setShow(false);
                            props.action(password);
                        }} id={"actionButton"}>OK</Button>
            </DialogActions>
        </Dialog>
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
            <div>
                <TableContainer component={Paper}>
                    <Table sx={{minWidth: 650}} aria-label="simple table">
                        <TableBody>
                            {importantItems.map((row) => <StatusRow
                                key={row.code} {...importantProperties(row, state.details)}/>)}
                        </TableBody>
                    </Table>
                </TableContainer>
            </div>
        }

        {statsItems.length > 0 &&
            <div>
                <DividerWithText>Last completed backup stats</DividerWithText>
                <TableContainer component={Paper}>
                    <Table sx={{minWidth: 650}} aria-label="simple table">
                        <TableBody>
                            {statsItems.map((row) => <StatusRow
                                key={row.code} {...importantProperties(row, state.details)}/>)}
                        </TableBody>
                    </Table>
                </TableContainer>
            </div>
        }

        {activeItems.length > 0 &&
            <div>
                <DividerWithText>Active transfers</DividerWithText>
                <TableContainer component={Paper}>
                    <Table sx={{minWidth: 650}} aria-label="simple table">
                        <TableBody>
                            {activeItems.map((row) => <StatusRow key={row.code} row={row}/>)}
                        </TableBody>
                    </Table>
                </TableContainer>
            </div>
        }

        {scheduledItems.length > 0 &&
            <div>
                <DividerWithText>Scheduled sets</DividerWithText>
                <TableContainer component={Paper}>
                    <Table sx={{minWidth: 650}} aria-label="simple table">
                        <TableBody>
                            {scheduledItems.map((row) => <StatusRow key={row.code} row={row}/>)}
                        </TableBody>
                    </Table>
                </TableContainer>
            </div>
        }

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
                    <div>
                        <DividerWithText>Additional stats</DividerWithText>
                        <TableContainer component={Paper}>
                            <Table sx={{minWidth: 650}} aria-label="simple table">
                                <TableBody>
                                    {statusItems.map((row) => <StatusRow key={row.code} row={row}/>)}
                                </TableBody>
                            </Table>
                        </TableContainer>
                    </div>
                }
            </AccordionDetails>
        </Accordion>
        <LogTable onlyErrors={!state.details}/>
    </Stack>
}