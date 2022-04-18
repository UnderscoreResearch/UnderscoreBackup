import * as React from "react";
import {GetActivity, StatusLine} from "../api";
import {Paper, Stack, Table, TableBody, TableCell, TableContainer, TableRow} from "@mui/material";
import LogTable from "./LogTable";
import DividerWithText from "../3rdparty/react-js-cron-mui/components/DividerWithText";

export interface StatusProps {
    status: StatusLine[]
}

export interface StatusState {
    logs: StatusLine[],
    page: number,
    itemsPerPage: number
}

export default function Status(props: StatusProps) {
    const [state, setState] = React.useState({
        logs: []
    } as StatusState);

    async function fetchLogs() {
        const logs = await GetActivity(true);
        setState((oldState) => {
            return {
                ...oldState,
                logs: logs
            } as StatusState
        });
    }

    React.useEffect(() => {
        const timer = setInterval(fetchLogs, 5000);
        fetchLogs();
        return () => clearInterval(timer);
    }, []);

    const items = [...props.status];

    const activeItems = items.filter(item => item.code.startsWith("DOWNLOADED_ACTIVE") || item.code.startsWith("UPLOADED_ACTIVE"));
    const statusItems = items.filter(item => !(item.code.startsWith("DOWNLOADED_ACTIVE") || item.code.startsWith("UPLOADED_ACTIVE")));
    statusItems.sort((a, b) => a.message.localeCompare(b.message));
    activeItems.sort((a, b) => a.message.localeCompare(b.message));

    return <Stack spacing={2}>
        {statusItems.length > 0 ?
            <div>
                <DividerWithText>Overall stats</DividerWithText>
                <TableContainer component={Paper}>
                    <Table sx={{minWidth: 650}} aria-label="simple table">
                        <TableBody>
                            {statusItems.map((row) => (
                                <TableRow
                                    key={row.code}
                                    sx={{'&:last-child td, &:last-child th': {border: 0}}}
                                >
                                    <TableCell component="th" scope="row">
                                        {row.message}
                                    </TableCell>
                                    <TableCell align="right"><b>{row.valueString}</b></TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </TableContainer>
            </div>
            : ""
        }
        {activeItems.length > 0 ?
            <div>
                <DividerWithText>Active transfers</DividerWithText>
                <TableContainer component={Paper}>
                    <Table sx={{minWidth: 650}} aria-label="simple table">
                        <TableBody>
                            {activeItems.map((row) => (
                                <TableRow
                                    key={row.code}
                                    sx={{'&:last-child td, &:last-child th': {border: 0}}}
                                >
                                    <TableCell component="th" scope="row">
                                        {row.message}
                                    </TableCell>
                                    <TableCell align="right"><b>{row.valueString}</b></TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </TableContainer>
            </div>
            : ""}
        <div>
            <DividerWithText>Logs</DividerWithText>
            <LogTable logs={state.logs ? state.logs : []}/>
        </div>
    </Stack>
}