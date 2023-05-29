import {useLocation, useNavigate} from "react-router-dom";
import queryString from "query-string";
import React, {useEffect} from "react";
import base64url from "base64url";
import {DisplayMessage} from "../App";
import {generateToken, restoreSecret} from "../api/service";
import {useApplication} from "../utils/ApplicationContext";

export default function AuthorizeAccept(props: { updatedToken: () => Promise<void> }) {
    const location = useLocation();
    const navigate = useNavigate();
    const query = queryString.parse(location.search);
    const appContext = useApplication();

    function smartRedirect(newLocation: string) {
        if (!newLocation.startsWith("http")) {
            navigate(newLocation);
        } else
            window.location.href = newLocation;
    }

    useEffect(() => {
        appContext.busyOperation(async () => {
            const nonce = query.nonce as string | undefined;
            const code = query.code as string | undefined;
            const email = query.email as string | undefined;
            const sourceName = query.sourceName as string | undefined;
            const sourceId = query.sourceId as string | undefined;
            const redirect = window.localStorage.getItem("redirectSource");
            if (!redirect) {
                DisplayMessage("Missing redirect parameters, invalid authentication", "error");
                smartRedirect("/");
            } else if ((!email && !sourceId) || !code || !nonce) {
                DisplayMessage("Missing email, code or nonce", "error");
                smartRedirect(redirect);
            } else if (window.localStorage.getItem("redirectNonce") !== nonce) {
                DisplayMessage("Invalid nonce", "error");
                smartRedirect(redirect);
            } else {
                const codeVerifier = window.localStorage.getItem("redirectCodeVerifier") as string | undefined;
                if (!codeVerifier) {
                    DisplayMessage("Missing codeVerifier", "error");
                    smartRedirect(redirect);
                } else if (email) {
                    const token = await generateToken(code, codeVerifier, sourceName);

                    if (token) {
                        window.localStorage.setItem("email", base64url.encode(email));
                        await props.updatedToken();
                        smartRedirect(redirect);
                    }
                } else {
                    const newPassword = window.sessionStorage.getItem("newPassword");
                    const serviceEmail = base64url.decode(window.localStorage.getItem("email") as string);
                    const region = query.region as string | undefined;

                    window.localStorage.removeItem("redirectSource");
                    window.localStorage.removeItem("redirectCodeVerifier");
                    window.localStorage.removeItem("redirectNonce");

                    if (!newPassword || !region || !serviceEmail) {
                        DisplayMessage("Missing new password, region or email", "error");
                        smartRedirect(redirect);
                    } else {
                        if (await restoreSecret(region, sourceId as string, serviceEmail, code, codeVerifier, newPassword)) {
                            await appContext.update(newPassword);
                            smartRedirect(redirect);
                        }
                    }
                }
            }
        });
    }, []);

    return <React.Fragment/>;
}
