import * as React from "react";
import {ReactElement} from "react";
import {Collapse, Fab} from "@mui/material";
import IconButton from "@mui/material/IconButton";
import DeleteIcon from '@mui/icons-material/Delete';
import UpIcon from '@mui/icons-material/KeyboardArrowUp';
import DownIcon from '@mui/icons-material/KeyboardArrowDown';
import AddIcon from '@mui/icons-material/Add';
import {TransitionGroup} from "react-transition-group";

export interface EditableListProps<Type> {
    createItem: (item: Type, itemUpdated: (item: Type) => void) => React.ReactElement,
    items: Type[],
    verticalSpacing?: string,
    deleteBelow?: boolean,
    allowDrop?: (item: Type) => boolean,
    allowReorder?: boolean,
    onItemChanged: (items: Type[]) => void,
    createNewItem: () => Type
}

interface InternalItem<Type> {
    key: string,
    item: Type
}

export interface EditableListState<Type> {
    items: InternalItem<Type>[],
}

let counter = 1;

export function EditableList<Type>(props: EditableListProps<Type>): React.ReactElement {
    const [state, setState] = React.useState(() => {
            return {
                items: props.items.map((item) => {
                    return {
                        key: (++counter) + "",
                        item: item
                    } as InternalItem<Type>
                })
            } as EditableListState<Type>;
        }
    );

    function deleteItem(key: string) {
        let newItems = state.items.filter(t => t.key !== key);
        setState({
            items: newItems
        });
        props.onItemChanged(newItems.map(item => item.item));
    }

    function createItem(item: InternalItem<Type>): React.ReactElement {
        const allowDrop = !props.allowDrop || props.allowDrop(item.item);
        let allowUp = false;
        let allowDown = false;
        let index = -1;

        if (props.allowReorder) {
            index = state.items.findIndex((t) => t.key == item.key);
            if (index >= 0) {
                allowDown = index < state.items.length - 1;
                allowUp = index > 0;
            }
        }

        function itemUpdated(newItem: Type) {
            let newItems = [...state.items];
            let ind = newItems.findIndex((t) => t.key == item.key);
            if (ind >= 0) {
                newItems.splice(ind, 1, {
                    ...item,
                    item: newItem
                });
                setState({
                    items: newItems
                });
                props.onItemChanged(newItems.map(item => item.item));
            }
        }

        function swapItem(index: number) {
            const newItems = [...state.items];
            const t = newItems[index];
            newItems[index] = newItems[index + 1];
            newItems[index + 1] = t;
            setState({
                    ...state,
                    items: newItems
                }
            );
            props.onItemChanged(newItems.map(item => item.item));
        }

        if (props.deleteBelow) {
            let dropElement: ReactElement | undefined;
            if (allowDrop || allowUp || allowDown) {
                dropElement =
                    <div style={{width: "100%"}}>
                        <IconButton aria-label="delete" style={{float: "right"}} onClick={() => deleteItem(item.key)}>
                            <DeleteIcon/>
                        </IconButton>
                        {allowUp &&
                            <IconButton aria-label="up" style={{float: "right"}}
                                        onClick={() => swapItem(index - 1)}>
                                <UpIcon/>
                            </IconButton>
                        }
                        {allowDown &&
                            <IconButton aria-label="down" style={{float: "right"}} onClick={() => swapItem(index)}>
                                <DownIcon/>
                            </IconButton>
                        }
                    </div>
            }
            return <Collapse key={item.key}>
                <div style={{width: "100%"}}>
                    {props.createItem(item.item, itemUpdated)}
                </div>
                {dropElement}
                <div style={{clear: "both", marginBottom: props.verticalSpacing ? props.verticalSpacing : "1em"}}/>
            </Collapse>
        }
        if (allowDrop || allowUp || allowDown) {
            return <Collapse key={item.key}>
                <div style={{
                    width: "100%",
                    display: "flex",
                    marginBottom: props.verticalSpacing ? props.verticalSpacing : "1em"
                }}>
                    <div style={{flexGrow: 1}}>
                        {props.createItem(item.item, itemUpdated)}
                    </div>
                    <div style={{flexGrow: 0}}>
                        {allowDown &&
                            <IconButton aria-label="down" onClick={() => swapItem(index)}>
                                <DownIcon/>
                            </IconButton>
                        }
                        {allowUp &&
                            <IconButton aria-label="up"
                                        onClick={() => swapItem(index - 1)}>
                                <UpIcon/>
                            </IconButton>
                        }
                        <IconButton aria-label="delete" onClick={() => deleteItem(item.key)}>
                            <DeleteIcon/>
                        </IconButton>
                    </div>
                </div>
            </Collapse>
        } else {
            return <Collapse key={item.key}>
                <div style={{width: "100%", marginBottom: props.verticalSpacing ? props.verticalSpacing : "1em"}}>
                    {props.createItem(item.item, itemUpdated)}
                </div>
            </Collapse>
        }
    }

    function addItem() {
        let newItems = [...state.items];
        newItems.push({
            key: (++counter) + "",
            item: props.createNewItem()
        });
        setState({
            items: newItems
        });
        props.onItemChanged(newItems.map(item => item.item));
    }

    return <div style={{width: "100%"}}>
        <TransitionGroup>
            {state.items.map((item) => createItem(item))}
        </TransitionGroup>
        <div style={{width: "100%", display: "flex"}}>
            <div style={{width: "100%"}}>
            </div>
            <div>
                <Fab id="new-item" size={"small"} color={"primary"} onClick={() => addItem()}>
                    <AddIcon/>
                </Fab>
            </div>
        </div>
    </div>
}
