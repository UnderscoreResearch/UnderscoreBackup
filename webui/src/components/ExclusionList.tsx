import * as React from "react";
import {TextField} from "@mui/material";
import {EditableList} from "./EditableList";

function createExclusionControl(item: string, itemUpdated: (item: string) => void): React.ReactElement {
    return <TextField variant="standard"
                      fullWidth={true}
                      defaultValue={item}
                      onBlur={(e) => itemUpdated(e.target.value)}
    />
}

export default function ExclusionList(props: { exclusions: string[] | undefined, exclusionsChanged: (items: string[]) => void }) {
    return EditableList<string>({
        createItem: createExclusionControl,
        items: (props.exclusions ? props.exclusions : []) as string[],
        onItemChanged: props.exclusionsChanged,
        createNewItem: () => ""
    })
}
