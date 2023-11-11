export default function resolvePassword(password: string, minLength: number | undefined, userInputs: string[] | undefined) {
    const zxcvbn = require('zxcvbn');

    let result = null;
    let score = 0;
    let feedback: any = {};
    if (password.length >= (minLength as number)) {
        result = zxcvbn(password, userInputs);
        ({score, feedback} = result);
    }
    return {score, feedback};
}