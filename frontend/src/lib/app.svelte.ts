import {Service} from "../../bindings/github.com/Laky-64/RenVault/internal/appinfo";

let name = $state('');

if (typeof window !== 'undefined') {
    void Service.AppName()
        .then(value => (name = value))
        .catch(() => {});
}

export function appName(): string {
    return name;
}
