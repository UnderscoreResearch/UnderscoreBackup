import {StatusLine} from "../api";
import * as React from "react";
import {LinearProgress, TableCell, TableRow, Tooltip} from "@mui/material";
import './StatusRow.css'
import {useTheme} from "../utils/Theme";

export interface StatusRowProps {
    row: StatusLine,
    hideValueString?: boolean,
    doubleLine?: boolean,
    etaOnly?: boolean,
    dangerous?: boolean,
    action?: React.ReactNode
}

function trimValueIfNeeded(props: StatusRowProps) {
    if (props.row.valueString) {
        if (props.etaOnly) {
            const match = /, (ETA.*?)$/.exec(props.row.valueString);
            if (match) {
                return match[1];
            }
            return undefined;
        }
        if (props.row.valueString.length > 50) {
            const match = /((\/|\\)[^\\\/]*.{40})$/.exec(props.row.valueString);
            if (match && match[1].length !== props.row.valueString.length) {
                const shorthand = "..." + match[1];
                return <Tooltip title={props.row.valueString}><div>{shorthand}</div></Tooltip>;
            }
        }
    }
    return props.row.valueString;
}

function trimDescriptionIfNeeded(props: StatusRowProps) {
    if (props.row.message) {
        if (props.row.message.length > 50) {
            const match = /^(\S+).*?((\/|\\)[^\\\/]*.{40})$/.exec(props.row.message);
            if (match) {
                const shorthand = match[1] + " ..." + match[2];
                return <Tooltip title={props.row.message}><div>{shorthand}</div></Tooltip>;
            }
        }
    }
    return props.row.message;
}

export function StatusRow(props: StatusRowProps) {
    let hasSecondRow;
    let hasProgress
    let progress;
    if (props.row.totalValue && props.row.value !== undefined) {
        hasProgress = hasSecondRow = true;
        progress = 100 * props.row.value / props.row.totalValue;
    } else {
        hasProgress = hasSecondRow = false;
        progress = 0;
    }
    if (props.doubleLine && !props.hideValueString) {
        hasSecondRow = true;
    } else if (props.action && props.row.valueString) {
        hasSecondRow = true;
    }
    const className = hasSecondRow ? "cell-with-second-line" : "cell-without-progress";
    const theme = useTheme();

    const background = props.dangerous ? theme.theme.palette.error.main : theme.theme.palette.background.default;

    return <React.Fragment key={props.row.code}>
        {props.doubleLine ?
            <>
                <TableRow
                    sx={{'&:last-child td, &:last-child th': {border: 0}}}
                >
                    <TableCell className={className} colSpan={2}
                               style={{paddingBottom: 0, backgroundColor: background}}>
                        {trimDescriptionIfNeeded(props)}
                    </TableCell>
                </TableRow>
                {!props.hideValueString &&
                    <TableRow>
                        <TableCell style={{paddingTop: 0, backgroundColor: background}} className={className}
                                   align="right"
                                   colSpan={2}><b>{props.row.valueString}</b></TableCell>
                    </TableRow>
                }
            </>
            :
            <TableRow
                sx={{'&:last-child td, &:last-child th': {border: 0}}}
            >
                <TableCell className={className} style={{backgroundColor: background}}>
                    {trimDescriptionIfNeeded(props)}
                </TableCell>
                {props.action && !props.row.valueString ?
                    <TableCell className={className} align="right"
                               style={{backgroundColor: background}}>{props.action}</TableCell>
                    :
                    <>
                        {!props.hideValueString &&
                            <TableCell className={className} align="right"
                                       style={{backgroundColor: background}}><b>{trimValueIfNeeded(props)}</b></TableCell>
                        }
                    </>
                }
            </TableRow>
        }
        {
            hasProgress &&
            <TableRow>
                <TableCell component="th" scope="row" colSpan={2}
                           style={{backgroundColor: background}}>
                    <LinearProgress variant="determinate" value={progress}/>
                </TableCell>
            </TableRow>
        }
        {
            props.action && props.row.valueString &&
            <TableRow>
                <TableCell style={{paddingTop: "4px", backgroundColor: background}} component="th" scope="row"
                           colSpan={2} align={"right"}>
                    {props.action}
                </TableCell>
            </TableRow>
        }
    </React.Fragment>;
}