import * as React from "react";
import {useState} from "react";
import {Button, TextField} from "@mui/material";
import {EditableList} from "./EditableList";
import {useApplication} from "../utils/ApplicationContext";

function createExclusionControl(item: string, itemUpdated: (item: string) => void): React.ReactElement {
    return <TextField variant="standard"
                      fullWidth={true}
                      defaultValue={item}
                      onBlur={(e) => itemUpdated(e.target.value)}
    />
}

export default function ExclusionList(props: {
    exclusions: string[] | undefined,
    exclusionsChanged: (items: string[]) => void
}) {
    const appContext = useApplication();
    const [resetCount, setResetCount] = useState(1);

    function resetList() {
        props.exclusionsChanged(appContext.backendState.defaultSet.exclusions as string[]);
        setResetCount(resetCount + 1);
    }

    return <>
        <EditableList createItem={createExclusionControl}
                      items={(props.exclusions ? props.exclusions : []) as string[]}
                      onItemChanged={props.exclusionsChanged} createNewItem={() => ""}
                      stateReset={resetCount}
                      additionalElement={<Button variant="contained" id="showConfiguration" onClick={resetList}>
                          Revert to Default
                      </Button>
                      }
        />
    </>
}
