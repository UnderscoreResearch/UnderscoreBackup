import React from "react";
import {MainAppSkeleton} from "./MainAppSkeleton";
import {submitPrivateKeyPassword} from "../api";
import {useApplication} from "../utils/ApplicationContext";
import {Loading} from "./Loading";
import PasswordDialog from "./PasswordDialog";

export default function UILogin() {
    const appContext = useApplication();

    return <>
        <MainAppSkeleton
            title={"Backup Password Required"}
            processing={false}
            navigation={<></>} disallowClose={false}>
        </MainAppSkeleton>
        <Loading open={appContext.isBusy()}/>
        <PasswordDialog label={"Enter password"}
                        dialogText={"Your backup password is required to access the configuration interface."}
                        open={true}
                        action={(password) => {
                            appContext.busyOperation(async () => {
                                await submitPrivateKeyPassword(password);
                                await appContext.update(password);
                            });
                        }
                        }/>
    </>
}