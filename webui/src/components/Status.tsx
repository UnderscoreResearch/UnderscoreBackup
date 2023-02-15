import * as React from "react";
import {getActivity, StatusLine} from "../api";
import {LinearProgress, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableRow} from "@mui/material";
import LogTable from "./LogTable";
import DividerWithText from "../3rdparty/react-js-cron-mui/components/DividerWithText";
import './Status.css'
import {deepEqual} from "fast-equals";

export interface StatusProps {
    status: StatusLine[]
}

export interface StatusState {
    logs: StatusLine[]
}

function StatusRow(row: StatusLine) {
    let hasSecondRow;
    let progress;
    if (row.totalValue && row.value !== undefined) {
        hasSecondRow = true;
        progress = 100 * row.value / row.totalValue;
    } else {
        hasSecondRow = false;
        progress = 0;
    }
    const className = hasSecondRow ? "cell-with-progress" : "cell-without-progress";

    return <React.Fragment key={row.code}>
        <TableRow
            sx={{'&:last-child td, &:last-child th': {border: 0}}}
        >
            <TableCell className={className}>
                {row.message}
            </TableCell>
            <TableCell className={className} align="right"><b>{row.valueString}</b></TableCell>
        </TableRow>
        {
            hasSecondRow &&
            <TableRow>
                <TableCell component="th" scope="row" colSpan={2}>
                    <LinearProgress variant="determinate" value={progress}/>
                </TableCell>
            </TableRow>
        }
    </React.Fragment>;
}

var lastStatus: StatusLine[] = [];
var statusUpdated: ((newValue: StatusLine[]) => void) | undefined;


async function updateLogs() {
    const logs = await getActivity(true);

    if (logs && !deepEqual(lastStatus, logs)) {
        lastStatus = logs;
        if (statusUpdated)
            statusUpdated(lastStatus);
    }
}


export default function Status(props: StatusProps) {
    const [state, setState] = React.useState({
        logs: lastStatus
    } as StatusState);

    React.useEffect(() => {
        if (state.logs.length == 0) {
            updateLogs();
        }

        statusUpdated = (logs) => setState((oldState) => ({
            ...oldState,
            logs: logs
        }));
        const timer = setInterval(updateLogs, 5000);
        return () => {
            statusUpdated = undefined;
            clearInterval(timer);
        };
    }, []);

    const items = [...props.status];

    const activeItems = items.filter(item => item.code.startsWith("DOWNLOADED_ACTIVE") || item.code.startsWith("UPLOADED_ACTIVE"));
    const statusItems = items.filter(item => !(item.code.startsWith("DOWNLOADED_ACTIVE") || item.code.startsWith("UPLOADED_ACTIVE")));
    statusItems.sort((a, b) => a.message.localeCompare(b.message));
    activeItems.sort((a, b) => a.message.localeCompare(b.message));

    return <Stack spacing={2}>
        {statusItems.length > 0 &&
            <div>
                <DividerWithText>Overall stats</DividerWithText>
                <TableContainer component={Paper}>
                    <Table sx={{minWidth: 650}} aria-label="simple table">
                        <TableBody>
                            {statusItems.map((row) => StatusRow(row))}
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
                            {activeItems.map((row) => StatusRow(row))}
                        </TableBody>
                    </Table>
                </TableContainer>
            </div>
        }
        <div>
            <DividerWithText>Logs</DividerWithText>
            <LogTable logs={state.logs ? state.logs : []}/>
        </div>
    </Stack>
}