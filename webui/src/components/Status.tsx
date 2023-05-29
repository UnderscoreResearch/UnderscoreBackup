import * as React from "react";
import {StatusLine} from "../api";
import {
    Accordion,
    AccordionDetails,
    AccordionSummary, Link,
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

export interface StatusProps {
    status: StatusLine[]
}

export interface StatusState {
    details: boolean
}

const IMPORTANT_CODES = [
    "MEMORY_HIGH",
    "BACKUP_DURATION",
    "RESTORE_DURATION",
    "TRIMMING_STEPS",
    "VALIDATE_STEPS",
    "REPLAY_LOG_PROCESSED_STEPS",
    "DEACTIVATING_SHARES_PROCESSED_STEPS",
    "ACTIVATING_SHARES_PROCESSED_STEPS",
    "OPTIMIZING_LOG_PROCESSED_STEPS",
    "CONTINUOUS_BACKUP_FILES",
    "CONTINUOUS_BACKUP_SIZE",
    "REPOSITORY_INFO_FILES",
    "REPOSITORY_INFO_FILE_VERSIONS",
    "REPOSITORY_INFO_TOTAL_SIZE",
    "COMPLETED_OBJECTS",
    "COMPLETED_SIZE",
    "UPLOADED_THROUGHPUT",
    "RESTORED_OBJECTS",
    "RESTORED_SIZE",
    "RESTORED_THROUGHPUT",
    "PROCESSED_PATH"
];

function importantProperties(row: StatusLine, details: boolean): StatusRowProps {
    const props: StatusRowProps = {row: row};
    switch (row.code) {
        case "MEMORY_HIGH":
            props.dangerous = true;
            props.link = <Link rel="noreferrer" target="_blank" style={{color: "inherit"}}
                               href={`https://underscorebackup.com/blog/2023/02/how-to-change-memory-configuration.html`} underline={"hover"}>How to fix</Link>
            break;
        case "PROCESSED_PATH":
            props.doubleLine = true;
            break;
        case "TRIMMING_STEPS":
        case "VALIDATE_STEPS":
        case "REPLAY_LOG_PROCESSED_STEPS":
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
    const scheduledItems: StatusLine[] = [];
    const statusItems = items.filter(item => {
        if (IMPORTANT_CODES.indexOf(item.code) >= 0) {
            importantItems.push(item);
            return false;
        }
        if (item.code.startsWith("SCHEDULED_BACKUP_")) {
            scheduledItems.push(item);
            return false;
        }
        return !(item.code.startsWith("DOWNLOADED_ACTIVE") || item.code.startsWith("UPLOADED_ACTIVE"))
    });
    importantItems.sort((a, b) => IMPORTANT_CODES.indexOf(a.code) - IMPORTANT_CODES.indexOf(b.code));
    statusItems.sort((a, b) => a.message.localeCompare(b.message));
    activeItems.sort((a, b) => a.message.localeCompare(b.message));

    return <Stack spacing={2}>

        {importantItems.length > 0 &&
            <div>
                <TableContainer component={Paper}>
                    <Table sx={{minWidth: 650}} aria-label="simple table">
                        <TableBody>
                            {importantItems.map((row) => StatusRow(importantProperties(row, state.details)))}
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
                            {activeItems.map((row) => StatusRow({row: row}))}
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
                            {scheduledItems.map((row) => StatusRow({row: row}))}
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
                                    {statusItems.map((row) => StatusRow({row: row}))}
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