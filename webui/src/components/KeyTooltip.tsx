import * as React from "react";
import {Tooltip, tooltipClasses, TooltipProps} from "@mui/material";
import {styled} from "@mui/material/styles";

export function formatKey(encryptionKey: string) {
    try {
        const parsed = JSON.parse(encryptionKey);
        return JSON.stringify(parsed, undefined, 2);
    } catch (e) {
        return encryptionKey;
    }
}

export function truncateKey(encryptionKey: string) {
    if (encryptionKey.length > 53) {
        return encryptionKey.substring(0, 53) + "...";
    }
    return encryptionKey;
}

export default function KeyTooltip(props: { encryptionKey?: string, children: React.ReactElement<any, any> }) {
    if (props.encryptionKey && props.encryptionKey.startsWith("{")) {

        const MoreWidthTooltip = styled(({className, ...props}: TooltipProps) => (
            <Tooltip {...props} classes={{popper: className}}/>
        ))({
            [`& .${tooltipClasses.tooltip}`]: {
                maxWidth: 1000,
            },
        });

        return <MoreWidthTooltip title={<pre
            style={{whiteSpace: "pre-wrap"}}>{formatKey(props.encryptionKey)}</pre>}>{props.children}</MoreWidthTooltip>
    }
    return <>{props.children}</>;
}