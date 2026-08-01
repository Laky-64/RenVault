const LETTERS = 'abcdefghijkmnopqrstuvwxyz';
const DIGITS = '23456789';
const GROUPS = 3;
const GROUP_LEN = 6;

function randomInt(max: number): number {
    const limit = Math.floor(0x100000000 / max) * max;
    const buffer = new Uint32Array(1);
    let value: number;
    do {
        crypto.getRandomValues(buffer);
        value = buffer[0];
    } while (value >= limit);
    return value % max;
}

function pick(set: string): string {
    return set[randomInt(set.length)];
}

export function generatePassword(): string {
    const total = GROUPS * GROUP_LEN;
    const chars = Array.from({length: total}, () => pick(LETTERS));

    const digitAt = randomInt(total);
    chars[digitAt] = pick(DIGITS);

    let upperAt = randomInt(total);
    while (upperAt === digitAt) upperAt = randomInt(total);
    chars[upperAt] = chars[upperAt].toUpperCase();

    const groups = [];
    for (let i = 0; i < total; i += GROUP_LEN) groups.push(chars.slice(i, i + GROUP_LEN).join(''));
    return groups.join('-');
}
