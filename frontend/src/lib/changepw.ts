import {Browser} from "@wailsio/runtime";
import {Service} from "../../bindings/github.com/Laky-64/RenVault/internal/vault";

export async function openChangePassword(domain: string): Promise<void> {
    if (!domain) return;
    const known = await Service.ChangePasswordURL(domain).catch(() => '');
    Browser.OpenURL(known || `https://${domain}/`);
}
