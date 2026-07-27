export const MIN_PASSWORD = 8;

export type Strength = 0 | 1 | 2 | 3;
export function strengthOf(password: string): Strength {
    if (password.length < MIN_PASSWORD) return 0;

    let classes = 0;
    if (/[a-z]/.test(password)) classes++;
    if (/[A-Z]/.test(password)) classes++;
    if (/\d/.test(password)) classes++;
    if (/[^A-Za-z0-9]/.test(password)) classes++;

    if (new Set(password).size < 5) return 1;

    if (password.length >= 16 && classes >= 2) return 3;
    if (password.length >= 12 || classes >= 3) return 2;
    return 1;
}
