import zxcvpn, {ZXCVBNResult} from "zxcvbn";

export function zxcvbn(password: string, userInputs?: string[]): ZXCVBNResult {
    return zxcvpn(password, userInputs);
}