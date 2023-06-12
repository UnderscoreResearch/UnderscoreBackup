import {Divider, MenuItem, Select, SelectChangeEvent} from "@mui/material";
import * as React from "react";
import {useEffect} from "react";
import {getBestRegion} from "../api/service";

interface ServiceRegionProps {
    region: string,
    onChange: (region: string) => void
}

export default function ServiceRegion(props: ServiceRegionProps) {
    const [region, setRegion]
        = React.useState(props.region || props.region.length === 0 ? "-" : props.region);

    async function autodetectRegion() {
        const bestRegion = await getBestRegion();
        if (bestRegion) {
            setRegion((oldRegion) => (oldRegion === "-" ? bestRegion : oldRegion));
        }
    }

    useEffect(() => {
        if (region === "-") {
            autodetectRegion();
        }
    }, []);

    useEffect(() => {
        if (region !== "-") {
            props.onChange(region);
        }
    }, [region]);

    return <Select style={{marginLeft: "0px"}}
                   fullWidth={true}
                   value={region}
                   label="Region"
                   onChange={(event: SelectChangeEvent) => {
                       setRegion(event.target.value as string);
                   }}>
        <MenuItem value={"-"}>Select Region</MenuItem>
        <Divider/>
        <MenuItem value={"us-west"}>North America (Oregon)</MenuItem>
        <MenuItem value={"eu-central"}>Europe (Frankfurt)</MenuItem>
        <MenuItem value={"ap-southeast"}>Asia (Singapore)</MenuItem>
    </Select>
}