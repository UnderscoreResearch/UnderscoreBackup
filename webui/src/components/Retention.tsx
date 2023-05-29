import * as React from "react";
import {Fragment} from "react";
import {BackupRetention, BackupRetentionAdditional,} from "../api";
import {EditableList} from "./EditableList";
import {Checkbox, FormControlLabel, TextField} from "@mui/material";
import Timespan from "./Timespan";
import Typography from "@mui/material/Typography";

interface RetentionState {
    retention?: BackupRetention
}

export interface RetentionProps {
    retention?: BackupRetention,
    retentionUpdated: (val?: BackupRetention) => void
}

function AdditionalTimeSpans(props: {
    items: BackupRetentionAdditional[],
    state: RetentionState,
    updateState: (newState: RetentionState) => void
}) {
    function createItem(item: BackupRetentionAdditional, onItemUpdate: (item: BackupRetentionAdditional) => void)
        : React.ReactElement {
        return <div style={{display: "flex"}}>
            <Timespan
                timespan={item.validAfter}
                requireTime={true}
                onChange={(newTimeSpan) => {
                    if (newTimeSpan) {
                        onItemUpdate({
                            validAfter: newTimeSpan,
                            frequency: item.frequency
                        });
                    }
                }}
                title={"After"}/>
            <Timespan timespan={item.frequency}
                      onChange={(newTimeSpan) => {
                          if (newTimeSpan) {
                              onItemUpdate({
                                  validAfter: item.validAfter,
                                  frequency: newTimeSpan
                              });
                          }
                      }}
                      title={"keep at most one version per "}/>
        </div>
    }

    function updateAdditionalState(items: BackupRetentionAdditional[]): void {
        props.updateState({
            ...props.state,
            retention: {
                ...props.state.retention,
                older: items
            }
        });
    }

    function createNewItem(): BackupRetentionAdditional {
        const olderState = props.state.retention ? props.state.retention.older : undefined;
        if (olderState && olderState.length > 0) {
            const lastItem = olderState[olderState.length - 1];
            return {
                frequency: lastItem.frequency,
                validAfter: {
                    unit: lastItem.validAfter.unit,
                    duration: lastItem.validAfter.duration * 2
                }
            }
        }
        return {
            frequency: {
                unit: "MONTHS",
                duration: 1
            },
            validAfter: {
                unit: "MONTHS",
                duration: 1
            }
        }
    }

    return EditableList<BackupRetentionAdditional>({
        createItem: createItem,
        items: props.items,
        onItemChanged: updateAdditionalState,
        createNewItem: createNewItem
    });
}

export default function Retention(props: RetentionProps) {
    const [state, setState] = React.useState({
        retention: props.retention
    } as RetentionState);

    function updateState(newState: RetentionState) {
        setState(newState);
        props.retentionUpdated(newState.retention);
    }

    return <Fragment>
        <Timespan timespan={state.retention ? state.retention.defaultFrequency : undefined}
                  onChange={(newTimeSpan) => updateState({
                      ...state,
                      retention: {
                          ...state.retention,
                          defaultFrequency: newTimeSpan
                      }
                  })}
                  title={"Initially keep at most one version per "}/>
        <AdditionalTimeSpans
            items={state.retention && state.retention.older ? state.retention.older : []}
            state={state}
            updateState={updateState}/>

        <Timespan timespan={state.retention ? state.retention.retainDeleted : undefined}
                  onChange={(newTimeSpan) => updateState({
                      ...state,
                      retention: {
                          ...state.retention,
                          retainDeleted: newTimeSpan
                      }
                  })}
                  title={"Remove deleted files after "}/>
        <div style={{display: "flex", alignItems: "center", marginTop: "8px"}}>
            <FormControlLabel style={{marginLeft: "0px"}} control={<Checkbox
                checked={state.retention ? !!state.retention.maximumVersions : false}
                onChange={(e) => updateState({
                    ...state,
                    retention: {
                        ...state.retention,
                        maximumVersions: e.target.checked ? 10 : undefined
                    }
                })}
            />} label={"Keep at most "}/>
            <TextField variant="standard"
                       disabled={!state.retention || !state.retention.maximumVersions}
                       defaultValue={state.retention && state.retention.maximumVersions ? state.retention.maximumVersions : 10}
                       inputProps={{min: 1, style: {textAlign: "right"}}}
                       style={{width: "80px"}}
                       type={"number"}
                       onBlur={(e) => updateState({
                           ...state,
                           retention: {
                               ...state.retention,
                               maximumVersions: parseInt(e.target.value)
                           }
                       })}/>
            <Typography>versions of every file</Typography>
        </div>
    </Fragment>
}