import * as React from "react";
import {ReactElement, useState} from "react";
import {PropertyMap} from "../api";
import {EditableList} from "./EditableList";
import {TextField} from "@mui/material";

interface StateProperty {
    key: string,
    value: string
}

export interface PropertyMapEditorProps {
    properties?: PropertyMap,
    onChange: (properties : PropertyMap) => void
}

function decodeProperties(properties: PropertyMap | undefined) : StateProperty[] {
    let ret = [] as StateProperty[];
    if (properties) {
        Object.keys(properties).forEach(key => {
            ret.push({
                key: key,
                value: properties[key]
            });
        })
    }
    return ret;
}

function createItem(item: StateProperty, itemChanged: (item: StateProperty) => void): ReactElement {
    return <div style={{display: "flex"}}>
        <TextField variant="standard"
                   style={{
                       width: "50%",
                       marginRight: "1em"
                   }}
                   defaultValue={item.key}
                   onBlur={(e) => itemChanged({
                       ...item,
                       key: e.target.value
                   })}
        />
        <TextField variant="standard"
                   style={{
                       width: "50%",
                       marginLeft: "1em"
                   }}
                   defaultValue={item.value}
                   onBlur={(e) => itemChanged({
                       ...item,
                       value: e.target.value
                   })}
        />
    </div>
}

export default function PropertyMapEditor(props: PropertyMapEditorProps) {
    const [state, setState] = useState(decodeProperties(props.properties));

    function updateState(items: StateProperty[]) {
        setState(state);

        let properties: PropertyMap = {};
        items.forEach((item) => {
            if (item.key.length > 0 && item.value.length > 0) {
                properties[item.key] = item.value;
            }
        });

        props.onChange(properties);
    }

    function createNewItem(): StateProperty {
        return {
            key: "",
            value: ""
        }
    }

    return <div>
        {EditableList<StateProperty>({
            createItem: createItem,
            createNewItem: createNewItem,
            items: state,
            verticalSpacing: "1em",
            onItemChanged: items => updateState(items),
        })}
    </div>
}