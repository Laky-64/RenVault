package io.lauren.renvault.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import io.lauren.renvault.lib.RvRelative
import io.lauren.renvault.lib.RvRelativeUnit
import io.lauren.renvault.lib.RvStrings
import io.lauren.renvault.lib.unescape
import io.lauren.renvault.lib.RvZoneKind
import io.lauren.renvault.lib.RvZoneText
import io.lauren.renvault.resources.tray_open
import io.lauren.renvault.resources.tray_quit
import io.lauren.renvault.resources.photo_filter
import io.lauren.renvault.resources.settings_sync_title
import io.lauren.renvault.resources.auth_local_action
import io.lauren.renvault.resources.auth_local_title
import io.lauren.renvault.resources.auth_local_hint
import io.lauren.renvault.resources.auth_local_new
import io.lauren.renvault.resources.auth_local_new_desc
import io.lauren.renvault.resources.auth_local_join
import io.lauren.renvault.resources.auth_local_join_desc
import io.lauren.renvault.resources.auth_profile_title
import io.lauren.renvault.resources.auth_profile_hint
import io.lauren.renvault.resources.profile_name
import io.lauren.renvault.resources.profile_photo_pick
import io.lauren.renvault.resources.profile_photo_remove
import io.lauren.renvault.resources.profile_edit_title
import io.lauren.renvault.resources.auth_join_title
import io.lauren.renvault.resources.auth_join_skip
import io.lauren.renvault.resources.pair_join_hint
import io.lauren.renvault.resources.pair_join_manual_hint
import io.lauren.renvault.resources.pair_scan
import io.lauren.renvault.resources.pair_code_label
import io.lauren.renvault.resources.pair_join_action
import io.lauren.renvault.resources.pair_host_hint
import io.lauren.renvault.resources.pair_waiting
import io.lauren.renvault.resources.pair_done
import io.lauren.renvault.resources.pair_retry
import io.lauren.renvault.resources.pair_close
import io.lauren.renvault.resources.busy_joining_title
import io.lauren.renvault.resources.busy_joining
import io.lauren.renvault.resources.devices_title
import io.lauren.renvault.resources.devices_count
import io.lauren.renvault.resources.devices_intro
import io.lauren.renvault.resources.devices_this
import io.lauren.renvault.resources.devices_synced
import io.lauren.renvault.resources.devices_never
import io.lauren.renvault.resources.devices_add
import io.lauren.renvault.resources.devices_join
import io.lauren.renvault.resources.devices_sync_now
import io.lauren.renvault.resources.devices_sync_done
import io.lauren.renvault.resources.devices_sync_failed
import io.lauren.renvault.resources.devices_remove
import io.lauren.renvault.resources.devices_remove_title
import io.lauren.renvault.resources.devices_remove_desc
import io.lauren.renvault.resources.devices_footer
import io.lauren.renvault.resources.settings_local_vault
import io.lauren.renvault.resources.settings_delete_vault
import io.lauren.renvault.resources.settings_delete_vault_title
import io.lauren.renvault.resources.settings_delete_vault_desc
import io.lauren.renvault.resources.settings_delete_vault_note
import io.lauren.renvault.resources.failure_pair_refused
import io.lauren.renvault.resources.failure_pair_missing
import io.lauren.renvault.resources.failure_no_network
import io.lauren.renvault.resources.failure_bad_photo
import io.lauren.renvault.resources.Res
import io.lauren.renvault.resources.settings_advanced_title
import io.lauren.renvault.resources.anisette_title
import io.lauren.renvault.resources.anisette_desc
import io.lauren.renvault.resources.anisette_placeholder
import io.lauren.renvault.resources.anisette_default
import io.lauren.renvault.resources.anisette_invalid
import io.lauren.renvault.resources.anisette_note
import io.lauren.renvault.resources.totp_key_confirm
import io.lauren.renvault.resources.totp_key_placeholder
import io.lauren.renvault.resources.totp_setup_desc
import io.lauren.renvault.resources.websites_add
import io.lauren.renvault.resources.websites_placeholder
import io.lauren.renvault.resources.websites_desc
import io.lauren.renvault.resources.websites_title
import io.lauren.renvault.resources.settings_based_on
import io.lauren.renvault.resources.settings_channel_stable
import io.lauren.renvault.resources.anisette_insecure_warning
import io.lauren.renvault.resources.anisette_default_desc
import io.lauren.renvault.resources.anisette_custom
import io.lauren.renvault.resources.anisette_checking
import io.lauren.renvault.resources.anisette_unreachable
import io.lauren.renvault.resources.anisette_insecure
import io.lauren.renvault.resources.anisette_latency
import io.lauren.renvault.resources.reset_forgot
import io.lauren.renvault.resources.reset_title
import io.lauren.renvault.resources.reset_icloud
import io.lauren.renvault.resources.reset_local
import io.lauren.renvault.resources.reset_action
import io.lauren.renvault.resources.quick_biometrics
import io.lauren.renvault.resources.quick_unlock_with
import io.lauren.renvault.resources.quick_prompt_title
import io.lauren.renvault.resources.quick_enable_prompt
import io.lauren.renvault.resources.quick_note
import io.lauren.renvault.resources.quick_gone
import io.lauren.renvault.resources.quick_expired
import io.lauren.renvault.resources.quick_failed
import io.lauren.renvault.resources.settings_backup_title
import io.lauren.renvault.resources.settings_backup_note
import io.lauren.renvault.resources.backup_export
import io.lauren.renvault.resources.backup_export_desc
import io.lauren.renvault.resources.backup_restore
import io.lauren.renvault.resources.backup_restore_desc
import io.lauren.renvault.resources.backup_import_csv
import io.lauren.renvault.resources.backup_import_csv_desc
import io.lauren.renvault.resources.backup_password
import io.lauren.renvault.resources.backup_password_confirm
import io.lauren.renvault.resources.backup_password_mismatch
import io.lauren.renvault.resources.backup_password_short
import io.lauren.renvault.resources.backup_export_hint
import io.lauren.renvault.resources.backup_restore_hint
import io.lauren.renvault.resources.backup_saved
import io.lauren.renvault.resources.backup_wrong_password
import io.lauren.renvault.resources.backup_bad_file
import io.lauren.renvault.resources.backup_result
import io.lauren.renvault.resources.backup_nothing
import io.lauren.renvault.resources.backup_file_type
import io.lauren.renvault.resources.auth_restore
import io.lauren.renvault.resources.auth_2fa_hint
import io.lauren.renvault.resources.auth_2fa_resend_action
import io.lauren.renvault.resources.auth_2fa_title
import io.lauren.renvault.resources.auth_continue_action
import io.lauren.renvault.resources.auth_device_passcode_hint
import io.lauren.renvault.resources.auth_device_passcode_placeholder
import io.lauren.renvault.resources.auth_device_passcode_title
import io.lauren.renvault.resources.auth_device_selection_hint
import io.lauren.renvault.resources.auth_device_selection_title
import io.lauren.renvault.resources.auth_email_placeholder
import io.lauren.renvault.resources.auth_master_mismatch
import io.lauren.renvault.resources.auth_master_placeholder
import io.lauren.renvault.resources.auth_master_repeat_placeholder
import io.lauren.renvault.resources.auth_master_setup_hint
import io.lauren.renvault.resources.auth_master_setup_title
import io.lauren.renvault.resources.auth_master_too_short
import io.lauren.renvault.resources.auth_password_placeholder
import io.lauren.renvault.resources.auth_privacy_link
import io.lauren.renvault.resources.auth_privacy_notice
import io.lauren.renvault.resources.auth_sign_in_action
import io.lauren.renvault.resources.auth_sign_in_hint
import io.lauren.renvault.resources.auth_sign_in_title
import io.lauren.renvault.resources.auth_unlock_action
import io.lauren.renvault.resources.auth_unlock_hint
import io.lauren.renvault.resources.auth_unlock_title
import io.lauren.renvault.resources.busy_checking_credentials
import io.lauren.renvault.resources.busy_creating_vault
import io.lauren.renvault.resources.busy_creating_vault_title
import io.lauren.renvault.resources.busy_opening_vault
import io.lauren.renvault.resources.busy_opening_vault_title
import io.lauren.renvault.resources.busy_sending_code
import io.lauren.renvault.resources.busy_signing_in_title
import io.lauren.renvault.resources.busy_verifying_code
import io.lauren.renvault.resources.busy_verifying_passcode
import io.lauren.renvault.resources.cancel
import io.lauren.renvault.resources.strength_fair
import io.lauren.renvault.resources.strength_strong
import io.lauren.renvault.resources.strength_weak
import io.lauren.renvault.resources.failure_account_locked
import io.lauren.renvault.resources.failure_bad_credentials
import io.lauren.renvault.resources.failure_generic
import io.lauren.renvault.resources.failure_no_devices
import io.lauren.renvault.resources.failure_wrong_master_password
import io.lauren.renvault.resources.failure_wrong_passcode
import io.lauren.renvault.resources.delete_confirm
import io.lauren.renvault.resources.delete_hard_desc
import io.lauren.renvault.resources.delete_soft_desc
import io.lauren.renvault.resources.delete_title
import io.lauren.renvault.resources.deleted_countdown
import io.lauren.renvault.resources.deleted_delete
import io.lauren.renvault.resources.deleted_recover
import io.lauren.renvault.resources.edit_delete
import io.lauren.renvault.resources.edit_delete_code
import io.lauren.renvault.resources.edit_no_code
import io.lauren.renvault.resources.edit_no_website
import io.lauren.renvault.resources.edit_save_for_code
import io.lauren.renvault.resources.edit_set_up_code
import io.lauren.renvault.resources.editor_cancel
import io.lauren.renvault.resources.editor_new_password
import io.lauren.renvault.resources.editor_notes
import io.lauren.renvault.resources.editor_save
import io.lauren.renvault.resources.editor_title
import io.lauren.renvault.resources.editor_website_hint
import io.lauren.renvault.resources.editor_username_hint
import io.lauren.renvault.resources.field_copied
import io.lauren.renvault.resources.field_modified
import io.lauren.renvault.resources.field_network_name
import io.lauren.renvault.resources.field_notes
import io.lauren.renvault.resources.field_passkey
import io.lauren.renvault.resources.field_passkey_creation_date
import io.lauren.renvault.resources.field_password
import io.lauren.renvault.resources.field_username
import io.lauren.renvault.resources.field_verification_code
import io.lauren.renvault.resources.field_website
import io.lauren.renvault.resources.field_website_more
import io.lauren.renvault.resources.item_compromised
import io.lauren.renvault.resources.item_wifi_network
import io.lauren.renvault.resources.list_selected_count
import io.lauren.renvault.resources.nav_back
import io.lauren.renvault.resources.passkey_learn_more
import io.lauren.renvault.resources.passkey_note_desc
import io.lauren.renvault.resources.search_empty
import io.lauren.renvault.resources.search_empty_desc
import io.lauren.renvault.resources.search_placeholder
import io.lauren.renvault.resources.search_placeholder_in
import io.lauren.renvault.resources.security_change_action
import io.lauren.renvault.resources.security_change_desc
import io.lauren.renvault.resources.security_change_title
import io.lauren.renvault.resources.zone_all_desc
import io.lauren.renvault.resources.zone_all_empty
import io.lauren.renvault.resources.zone_all_name
import io.lauren.renvault.resources.zone_all_no_selection
import io.lauren.renvault.resources.zone_codes_desc
import io.lauren.renvault.resources.zone_codes_empty
import io.lauren.renvault.resources.zone_codes_name
import io.lauren.renvault.resources.zone_codes_no_selection
import io.lauren.renvault.resources.zone_deleted_desc
import io.lauren.renvault.resources.zone_deleted_empty
import io.lauren.renvault.resources.zone_deleted_name
import io.lauren.renvault.resources.zone_deleted_no_selection
import io.lauren.renvault.resources.zone_item_count
import io.lauren.renvault.resources.zone_networks_desc
import io.lauren.renvault.resources.zone_networks_empty
import io.lauren.renvault.resources.zone_networks_name
import io.lauren.renvault.resources.zone_networks_no_selection
import io.lauren.renvault.resources.zone_passkeys_desc
import io.lauren.renvault.resources.zone_passkeys_empty
import io.lauren.renvault.resources.zone_passkeys_name
import io.lauren.renvault.resources.zone_passkeys_no_selection
import io.lauren.renvault.resources.zone_security_desc
import io.lauren.renvault.resources.zone_security_empty
import io.lauren.renvault.resources.zone_security_name
import io.lauren.renvault.resources.zone_security_no_selection
import io.lauren.renvault.resources.settings_title
import io.lauren.renvault.resources.settings_close
import io.lauren.renvault.resources.settings_general_title
import io.lauren.renvault.resources.settings_language
import io.lauren.renvault.resources.settings_security_title
import io.lauren.renvault.resources.settings_auto_lock
import io.lauren.renvault.resources.settings_auto_lock_never
import io.lauren.renvault.resources.settings_auto_lock_minute
import io.lauren.renvault.resources.settings_auto_lock_hour
import io.lauren.renvault.resources.settings_auto_lock_note
import io.lauren.renvault.resources.settings_lock_now
import io.lauren.renvault.resources.settings_check_pwned
import io.lauren.renvault.resources.settings_about_title
import io.lauren.renvault.resources.settings_last_sync
import io.lauren.renvault.resources.settings_synced_never
import io.lauren.renvault.resources.settings_sign_out
import io.lauren.renvault.resources.settings_sign_out_note
import io.lauren.renvault.resources.settings_sign_out_title
import io.lauren.renvault.resources.settings_sign_out_desc
import io.lauren.renvault.resources.settings_open_label
import io.lauren.renvault.resources.relative_now
import io.lauren.renvault.resources.settings_identity_title
import io.lauren.renvault.resources.names_title
import io.lauren.renvault.resources.names_off
import io.lauren.renvault.resources.names_on
import io.lauren.renvault.resources.names_intro
import io.lauren.renvault.resources.names_add
import io.lauren.renvault.resources.names_notice
import io.lauren.renvault.resources.names_notice_desc
import io.lauren.renvault.resources.names_entry
import io.lauren.renvault.resources.names_dialog_title
import io.lauren.renvault.resources.names_hidden_label
import io.lauren.renvault.resources.names_shown_label
import io.lauren.renvault.resources.names_remove
import io.lauren.renvault.resources.names_where_title
import io.lauren.renvault.resources.names_where_empty
import io.lauren.renvault.resources.names_where_footer
import io.lauren.renvault.resources.names_note
import io.lauren.renvault.resources.item_name_hidden_title
import io.lauren.renvault.resources.item_name_hidden_desc
import io.lauren.renvault.resources.item_name_original_desc
import io.lauren.renvault.resources.item_name_show_original
import io.lauren.renvault.resources.item_name_hide_original
import io.lauren.renvault.resources.editor_name_hidden
import io.lauren.renvault.resources.field_copied_original
import io.lauren.renvault.resources.names_found
import io.lauren.renvault.resources.settings_theme
import io.lauren.renvault.resources.theme_system
import io.lauren.renvault.resources.theme_light
import io.lauren.renvault.resources.theme_dark
import io.lauren.renvault.resources.theme_choose
import io.lauren.renvault.resources.pwned_checking
import io.lauren.renvault.resources.pwned_none
import io.lauren.renvault.resources.pwned_failed
import io.lauren.renvault.resources.pwned_show
import io.lauren.renvault.resources.settings_pwned_never
import io.lauren.renvault.resources.settings_pwned_last
import io.lauren.renvault.resources.pwned_found
import io.lauren.renvault.resources.settings_auto_lock_minutes
import io.lauren.renvault.resources.settings_pending
import io.lauren.renvault.resources.relative_minutes
import io.lauren.renvault.resources.relative_hours
import io.lauren.renvault.resources.relative_days
import io.lauren.renvault.resources.relative_months
import io.lauren.renvault.resources.relative_years
import io.lauren.renvault.resources.list_deselect_all
import io.lauren.renvault.resources.list_select
import io.lauren.renvault.resources.list_select_all
import io.lauren.renvault.resources.item_edit
import io.lauren.renvault.resources.list_delete
import io.lauren.renvault.resources.list_order
import io.lauren.renvault.resources.list_add
import io.lauren.renvault.resources.sort_ascending
import io.lauren.renvault.resources.sort_descending
import io.lauren.renvault.resources.sort_modified
import io.lauren.renvault.resources.sort_created
import io.lauren.renvault.resources.sort_website
import io.lauren.renvault.resources.sort_title
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.rememberResourceEnvironment

val LocalStrings = staticCompositionLocalOf<RvStrings> {
    error("RvStrings not provided")
}

@Composable
fun rememberStrings(tag: String = LocalLocale.current.current): RvStrings {
    val environment = rememberResourceEnvironment()
    val strings by produceState<RvStrings>(EmptyStrings, environment, tag) {
        value = loadStrings(environment)
    }
    return strings
}

private val TextResources = listOf(
    Res.string.settings_advanced_title,
    Res.string.anisette_title,
    Res.string.anisette_desc,
    Res.string.anisette_placeholder,
    Res.string.anisette_default,
    Res.string.anisette_invalid,
    Res.string.anisette_note,
    Res.string.websites_title,
    Res.string.settings_based_on,
    Res.string.settings_channel_stable,
    Res.string.anisette_insecure_warning,
    Res.string.anisette_default_desc,
    Res.string.anisette_custom,
    Res.string.anisette_checking,
    Res.string.anisette_unreachable,
    Res.string.anisette_insecure,
    Res.string.anisette_latency,
    Res.string.reset_forgot,
    Res.string.reset_title,
    Res.string.reset_icloud,
    Res.string.reset_local,
    Res.string.reset_action,
    Res.string.quick_biometrics,
    Res.string.quick_unlock_with,
    Res.string.quick_prompt_title,
    Res.string.quick_enable_prompt,
    Res.string.quick_note,
    Res.string.quick_gone,
    Res.string.quick_expired,
    Res.string.quick_failed,
    Res.string.settings_backup_title,
    Res.string.settings_backup_note,
    Res.string.backup_export,
    Res.string.backup_export_desc,
    Res.string.backup_restore,
    Res.string.backup_restore_desc,
    Res.string.backup_import_csv,
    Res.string.backup_import_csv_desc,
    Res.string.backup_password,
    Res.string.backup_password_confirm,
    Res.string.backup_password_mismatch,
    Res.string.backup_password_short,
    Res.string.backup_export_hint,
    Res.string.backup_restore_hint,
    Res.string.backup_saved,
    Res.string.backup_wrong_password,
    Res.string.backup_bad_file,
    Res.string.backup_result,
    Res.string.backup_nothing,
    Res.string.backup_file_type,
    Res.string.auth_restore,
    Res.string.websites_desc,
    Res.string.websites_placeholder,
    Res.string.websites_add,
    Res.string.totp_setup_desc,
    Res.string.totp_key_placeholder,
    Res.string.totp_key_confirm,
    Res.string.auth_sign_in_title,
    Res.string.auth_sign_in_hint,
    Res.string.auth_email_placeholder,
    Res.string.auth_password_placeholder,
    Res.string.auth_sign_in_action,
    Res.string.auth_privacy_notice,
    Res.string.auth_privacy_link,
    Res.string.auth_2fa_title,
    Res.string.auth_2fa_hint,
    Res.string.auth_2fa_resend_action,
    Res.string.auth_device_selection_title,
    Res.string.auth_device_selection_hint,
    Res.string.auth_device_passcode_title,
    Res.string.auth_device_passcode_hint,
    Res.string.auth_device_passcode_placeholder,
    Res.string.auth_continue_action,
    Res.string.auth_master_setup_title,
    Res.string.auth_master_setup_hint,
    Res.string.auth_master_placeholder,
    Res.string.auth_master_repeat_placeholder,
    Res.string.auth_master_mismatch,
    Res.string.auth_unlock_title,
    Res.string.auth_unlock_hint,
    Res.string.auth_unlock_action,
    Res.string.busy_signing_in_title,
    Res.string.busy_checking_credentials,
    Res.string.busy_opening_vault_title,
    Res.string.busy_opening_vault,
    Res.string.busy_sending_code,
    Res.string.busy_verifying_code,
    Res.string.busy_verifying_passcode,
    Res.string.busy_creating_vault_title,
    Res.string.busy_creating_vault,
    Res.string.strength_weak,
    Res.string.strength_fair,
    Res.string.strength_strong,
    Res.string.failure_generic,
    Res.string.failure_bad_credentials,
    Res.string.failure_account_locked,
    Res.string.failure_wrong_passcode,
    Res.string.failure_wrong_master_password,
    Res.string.failure_no_devices,
    Res.string.cancel,
    Res.string.deleted_delete,
    Res.string.deleted_recover,
    Res.string.edit_delete,
    Res.string.edit_delete_code,
    Res.string.edit_no_code,
    Res.string.edit_no_website,
    Res.string.edit_save_for_code,
    Res.string.edit_set_up_code,
    Res.string.editor_notes,
    Res.string.editor_new_password,
    Res.string.editor_cancel,
    Res.string.editor_save,
    Res.string.editor_title,
    Res.string.editor_website_hint,
    Res.string.editor_username_hint,
    Res.string.field_copied,
    Res.string.field_modified,
    Res.string.field_network_name,
    Res.string.field_notes,
    Res.string.field_passkey,
    Res.string.field_passkey_creation_date,
    Res.string.field_password,
    Res.string.field_username,
    Res.string.field_verification_code,
    Res.string.field_website,
    Res.string.item_compromised,
    Res.string.item_wifi_network,
    Res.string.nav_back,
    Res.string.list_select,
    Res.string.list_select_all,
    Res.string.list_deselect_all,
    Res.string.item_edit,
    Res.string.list_delete,
    Res.string.list_order,
    Res.string.list_add,
    Res.string.sort_ascending,
    Res.string.sort_descending,
    Res.string.sort_modified,
    Res.string.sort_created,
    Res.string.sort_website,
    Res.string.sort_title,
    Res.string.search_empty,
    Res.string.search_empty_desc,
    Res.string.search_placeholder,
    Res.string.search_placeholder_in,
    Res.string.settings_title,
    Res.string.settings_close,
    Res.string.settings_general_title,
    Res.string.settings_language,
    Res.string.settings_security_title,
    Res.string.settings_auto_lock,
    Res.string.settings_auto_lock_never,
    Res.string.settings_auto_lock_minute,
    Res.string.settings_auto_lock_hour,
    Res.string.settings_auto_lock_note,
    Res.string.settings_lock_now,
    Res.string.settings_check_pwned,
    Res.string.settings_about_title,
    Res.string.settings_last_sync,
    Res.string.settings_synced_never,
    Res.string.settings_sign_out,
    Res.string.settings_sign_out_note,
    Res.string.settings_sign_out_title,
    Res.string.settings_sign_out_desc,
    Res.string.settings_open_label,
    Res.string.relative_now,
    Res.string.security_change_action,
    Res.string.security_change_desc,
    Res.string.security_change_title,
    Res.string.passkey_learn_more,
    Res.string.passkey_note_desc,
    Res.string.zone_all_desc,
    Res.string.zone_all_empty,
    Res.string.zone_all_name,
    Res.string.zone_all_no_selection,
    Res.string.zone_codes_desc,
    Res.string.zone_codes_empty,
    Res.string.zone_codes_name,
    Res.string.zone_codes_no_selection,
    Res.string.zone_deleted_desc,
    Res.string.zone_deleted_empty,
    Res.string.zone_deleted_name,
    Res.string.zone_deleted_no_selection,
    Res.string.zone_networks_desc,
    Res.string.zone_networks_empty,
    Res.string.zone_networks_name,
    Res.string.zone_networks_no_selection,
    Res.string.zone_passkeys_desc,
    Res.string.zone_passkeys_empty,
    Res.string.zone_passkeys_name,
    Res.string.zone_passkeys_no_selection,
    Res.string.zone_security_desc,
    Res.string.zone_security_empty,
    Res.string.zone_security_name,
    Res.string.zone_security_no_selection,
    Res.string.settings_identity_title,
    Res.string.names_title,
    Res.string.names_off,
    Res.string.names_on,
    Res.string.names_intro,
    Res.string.names_add,
    Res.string.names_notice,
    Res.string.names_notice_desc,
    Res.string.names_entry,
    Res.string.names_dialog_title,
    Res.string.names_hidden_label,
    Res.string.names_shown_label,
    Res.string.names_remove,
    Res.string.names_where_title,
    Res.string.names_where_empty,
    Res.string.names_where_footer,
    Res.string.names_note,
    Res.string.item_name_hidden_title,
    Res.string.item_name_hidden_desc,
    Res.string.item_name_original_desc,
    Res.string.item_name_show_original,
    Res.string.item_name_hide_original,
    Res.string.editor_name_hidden,
    Res.string.field_copied_original,
    Res.string.settings_theme,
    Res.string.theme_system,
    Res.string.theme_light,
    Res.string.theme_dark,
    Res.string.theme_choose,
    Res.string.pwned_checking,
    Res.string.pwned_none,
    Res.string.pwned_failed,
    Res.string.pwned_show,
    Res.string.settings_pwned_never,
    Res.string.settings_pwned_last,
    Res.string.auth_local_action,
    Res.string.auth_local_title,
    Res.string.auth_local_hint,
    Res.string.auth_local_new,
    Res.string.auth_local_new_desc,
    Res.string.auth_local_join,
    Res.string.auth_local_join_desc,
    Res.string.auth_profile_title,
    Res.string.auth_profile_hint,
    Res.string.profile_name,
    Res.string.profile_photo_pick,
    Res.string.profile_photo_remove,
    Res.string.profile_edit_title,
    Res.string.auth_join_title,
    Res.string.auth_join_skip,
    Res.string.pair_join_hint,
    Res.string.pair_join_manual_hint,
    Res.string.pair_scan,
    Res.string.pair_code_label,
    Res.string.pair_join_action,
    Res.string.pair_host_hint,
    Res.string.pair_waiting,
    Res.string.pair_done,
    Res.string.pair_retry,
    Res.string.pair_close,
    Res.string.busy_joining_title,
    Res.string.busy_joining,
    Res.string.devices_title,
    Res.string.devices_intro,
    Res.string.devices_this,
    Res.string.devices_synced,
    Res.string.devices_never,
    Res.string.devices_add,
    Res.string.devices_join,
    Res.string.devices_sync_now,
    Res.string.devices_sync_done,
    Res.string.devices_sync_failed,
    Res.string.devices_remove,
    Res.string.devices_remove_title,
    Res.string.devices_remove_desc,
    Res.string.devices_footer,
    Res.string.settings_local_vault,
    Res.string.settings_delete_vault,
    Res.string.settings_delete_vault_title,
    Res.string.settings_delete_vault_desc,
    Res.string.settings_delete_vault_note,
    Res.string.failure_pair_refused,
    Res.string.failure_pair_missing,
    Res.string.failure_no_network,
    Res.string.failure_bad_photo,
    Res.string.settings_sync_title,
    Res.string.photo_filter,
    Res.string.tray_open,
    Res.string.tray_quit,
)

private val PluralResources = listOf(
    Res.plurals.names_found,
    Res.plurals.pwned_found,
    Res.plurals.auth_master_too_short,
    Res.plurals.settings_auto_lock_minutes,
    Res.plurals.settings_pending,
    Res.plurals.relative_minutes,
    Res.plurals.relative_hours,
    Res.plurals.relative_days,
    Res.plurals.relative_months,
    Res.plurals.relative_years,
    Res.plurals.delete_confirm,
    Res.plurals.delete_hard_desc,
    Res.plurals.delete_soft_desc,
    Res.plurals.delete_title,
    Res.plurals.deleted_countdown,
    Res.plurals.field_website_more,
    Res.plurals.list_selected_count,
    Res.plurals.zone_item_count,
    Res.plurals.devices_count,
)

private suspend fun loadStrings(environment: ResourceEnvironment): RvStrings = ResourceStrings(
    texts = TextResources.associateWith { unescape(getString(environment, it)) },
    plurals = PluralResources.associateWith {
        unescape(getPluralString(environment, it, 1)) to unescape(getPluralString(environment, it, 2))
    },
)

private val ArgPattern = Regex("""%(\d+)\$[ds]""")

private fun String.withArgs(args: Array<out Any>): String = ArgPattern.replace(this) { match ->
    args.getOrNull(match.groupValues[1].toInt() - 1)?.toString() ?: match.value
}

private val EmptyStrings: RvStrings = ResourceStrings(emptyMap(), emptyMap())

private class ResourceStrings(
    private val texts: Map<StringResource, String>,
    private val plurals: Map<PluralStringResource, Pair<String, String>>,
) : RvStrings {
    private fun text(resource: StringResource): String = texts[resource].orEmpty()

    private fun text(resource: StringResource, vararg args: Any): String =
        texts[resource].orEmpty().withArgs(args)

    private fun many(resource: PluralStringResource, count: Int, vararg args: Any): String {
        val forms = plurals[resource] ?: return ""
        return (if (count == 1) forms.first else forms.second).withArgs(args)
    }

    override val fieldUsername: String get() = text(Res.string.field_username)
    override val fieldPassword: String get() = text(Res.string.field_password)
    override val fieldWebsite: String get() = text(Res.string.field_website)
    override val fieldVerificationCode: String get() = text(Res.string.field_verification_code)
    override val fieldNotes: String get() = text(Res.string.field_notes)
    override val fieldModified: String get() = text(Res.string.field_modified)
    override val fieldPasskey: String get() = text(Res.string.field_passkey)
    override val fieldNetworkName: String get() = text(Res.string.field_network_name)
    override val fieldCopied: String get() = text(Res.string.field_copied)
    override val itemWifiNetwork: String get() = text(Res.string.item_wifi_network)
    override val itemCompromised: String get() = text(Res.string.item_compromised)

    override val editNoWebsite: String get() = text(Res.string.edit_no_website)
    override val editSaveForCode: String get() = text(Res.string.edit_save_for_code)
    override val editNoCode: String get() = text(Res.string.edit_no_code)
    override val editDeleteCode: String get() = text(Res.string.edit_delete_code)
    override val editSetUpCode: String get() = text(Res.string.edit_set_up_code)
    override val editDelete: String get() = text(Res.string.edit_delete)
    override val editorUsernameHint: String get() = text(Res.string.editor_username_hint)
    override val editorNotes: String get() = text(Res.string.editor_notes)
    override val editorNewPassword: String get() = text(Res.string.editor_new_password)
    override val editorCancel: String get() = text(Res.string.editor_cancel)
    override val editorSave: String get() = text(Res.string.editor_save)
    override val editorTitle: String get() = text(Res.string.editor_title)
    override val editorWebsiteHint: String get() = text(Res.string.editor_website_hint)
    override val securityChangeAction: String get() = text(Res.string.security_change_action)
    override val securityChangeTitle: String get() = text(Res.string.security_change_title)
    override val passkeyNoteDesc: String get() = text(Res.string.passkey_note_desc)
    override val passkeyLearnMore: String get() = text(Res.string.passkey_learn_more)

    override val deletedRecover: String get() = text(Res.string.deleted_recover)
    override val deletedDelete: String get() = text(Res.string.deleted_delete)

    override val searchPlaceholder: String get() = text(Res.string.search_placeholder)
    override val searchEmptyDesc: String get() = text(Res.string.search_empty_desc)

    override val navBack: String get() = text(Res.string.nav_back)
    override val listSelect: String get() = text(Res.string.list_select)
    override val listSelectAll: String get() = text(Res.string.list_select_all)
    override val listDeselectAll: String get() = text(Res.string.list_deselect_all)
    override val itemEdit: String get() = text(Res.string.item_edit)
    override val listDelete: String get() = text(Res.string.list_delete)
    override val listOrder: String get() = text(Res.string.list_order)
    override val listAdd: String get() = text(Res.string.list_add)
    override val sortAscending: String get() = text(Res.string.sort_ascending)
    override val sortDescending: String get() = text(Res.string.sort_descending)
    override val sortModified: String get() = text(Res.string.sort_modified)
    override val sortCreated: String get() = text(Res.string.sort_created)
    override val sortWebsite: String get() = text(Res.string.sort_website)
    override val sortTitle: String get() = text(Res.string.sort_title)
    override val settingsTitle: String get() = text(Res.string.settings_title)
    override val settingsClose: String get() = text(Res.string.settings_close)
    override val settingsGeneralTitle: String get() = text(Res.string.settings_general_title)
    override val settingsLanguage: String get() = text(Res.string.settings_language)
    override val settingsSecurityTitle: String get() = text(Res.string.settings_security_title)
    override val settingsAutoLock: String get() = text(Res.string.settings_auto_lock)
    override val settingsAutoLockNever: String get() = text(Res.string.settings_auto_lock_never)
    override val settingsAutoLockMinute: String get() = text(Res.string.settings_auto_lock_minute)
    override val settingsAutoLockHour: String get() = text(Res.string.settings_auto_lock_hour)
    override val settingsAutoLockNote: String get() = text(Res.string.settings_auto_lock_note)
    override val settingsLockNow: String get() = text(Res.string.settings_lock_now)
    override val settingsCheckPwned: String get() = text(Res.string.settings_check_pwned)
    override val settingsIdentityTitle: String get() = text(Res.string.settings_identity_title)
    override val namesTitle: String get() = text(Res.string.names_title)
    override val namesOff: String get() = text(Res.string.names_off)
    override val namesOn: String get() = text(Res.string.names_on)
    override val namesIntro: String get() = text(Res.string.names_intro)
    override val namesAdd: String get() = text(Res.string.names_add)
    override val namesNotice: String get() = text(Res.string.names_notice)
    override val namesNoticeDesc: String get() = text(Res.string.names_notice_desc)
    override val namesEntry: String get() = text(Res.string.names_entry)
    override val namesDialogTitle: String get() = text(Res.string.names_dialog_title)
    override val namesHiddenLabel: String get() = text(Res.string.names_hidden_label)
    override val namesShownLabel: String get() = text(Res.string.names_shown_label)
    override val namesRemove: String get() = text(Res.string.names_remove)
    override val namesWhereTitle: String get() = text(Res.string.names_where_title)
    override val namesWhereEmpty: String get() = text(Res.string.names_where_empty)
    override val namesWhereFooter: String get() = text(Res.string.names_where_footer)
    override val namesNote: String get() = text(Res.string.names_note)
    override val itemNameHiddenTitle: String get() = text(Res.string.item_name_hidden_title)
    override val itemNameHiddenDesc: String get() = text(Res.string.item_name_hidden_desc)
    override val itemNameOriginalDesc: String get() = text(Res.string.item_name_original_desc)
    override val itemNameShowOriginal: String get() = text(Res.string.item_name_show_original)
    override val itemNameHideOriginal: String get() = text(Res.string.item_name_hide_original)
    override val editorNameHidden: String get() = text(Res.string.editor_name_hidden)
    override val fieldCopiedOriginal: String get() = text(Res.string.field_copied_original)
    override val settingsTheme: String get() = text(Res.string.settings_theme)
    override val themeSystem: String get() = text(Res.string.theme_system)
    override val themeLight: String get() = text(Res.string.theme_light)
    override val themeDark: String get() = text(Res.string.theme_dark)
    override val themeChoose: String get() = text(Res.string.theme_choose)
    override val pwnedChecking: String get() = text(Res.string.pwned_checking)
    override val pwnedNone: String get() = text(Res.string.pwned_none)
    override val pwnedFailed: String get() = text(Res.string.pwned_failed)
    override val pwnedShow: String get() = text(Res.string.pwned_show)
    override val settingsPwnedNever: String get() = text(Res.string.settings_pwned_never)
    override val settingsAboutTitle: String get() = text(Res.string.settings_about_title)
    override val settingsLastSync: String get() = text(Res.string.settings_last_sync)
    override val settingsSyncedNever: String get() = text(Res.string.settings_synced_never)
    override val settingsSignOut: String get() = text(Res.string.settings_sign_out)
    override val settingsSignOutNote: String get() = text(Res.string.settings_sign_out_note)
    override val settingsSignOutTitle: String get() = text(Res.string.settings_sign_out_title)
    override val settingsSignOutDesc: String get() = text(Res.string.settings_sign_out_desc)
    override val settingsOpenLabel: String get() = text(Res.string.settings_open_label)
    override val cancel: String get() = text(Res.string.cancel)
    override val settingsChannelStable: String get() = text(Res.string.settings_channel_stable)
    override val anisetteInsecureWarning: String get() = text(Res.string.anisette_insecure_warning)
    override val anisetteDefaultDesc: String get() = text(Res.string.anisette_default_desc)
    override val anisetteCustom: String get() = text(Res.string.anisette_custom)
    override val anisetteChecking: String get() = text(Res.string.anisette_checking)
    override val anisetteUnreachable: String get() = text(Res.string.anisette_unreachable)
    override val anisetteInsecure: String get() = text(Res.string.anisette_insecure)
    override fun anisetteLatency(ms: String): String = text(Res.string.anisette_latency, ms)
    override val resetForgot: String get() = text(Res.string.reset_forgot)
    override val resetTitle: String get() = text(Res.string.reset_title)
    override val resetIcloud: String get() = text(Res.string.reset_icloud)
    override val resetLocal: String get() = text(Res.string.reset_local)
    override val resetAction: String get() = text(Res.string.reset_action)
    override val quickBiometrics: String get() = text(Res.string.quick_biometrics)
    override fun quickUnlockWith(method: String): String = text(Res.string.quick_unlock_with, method)
    override val quickPromptTitle: String get() = text(Res.string.quick_prompt_title)
    override val quickEnablePrompt: String get() = text(Res.string.quick_enable_prompt)
    override val quickNote: String get() = text(Res.string.quick_note)
    override val quickGone: String get() = text(Res.string.quick_gone)
    override val quickExpired: String get() = text(Res.string.quick_expired)
    override val quickFailed: String get() = text(Res.string.quick_failed)
    override val settingsBackupTitle: String get() = text(Res.string.settings_backup_title)
    override val settingsBackupNote: String get() = text(Res.string.settings_backup_note)
    override val backupExport: String get() = text(Res.string.backup_export)
    override val backupExportDesc: String get() = text(Res.string.backup_export_desc)
    override val backupRestore: String get() = text(Res.string.backup_restore)
    override val backupRestoreDesc: String get() = text(Res.string.backup_restore_desc)
    override val backupImportCsv: String get() = text(Res.string.backup_import_csv)
    override val backupImportCsvDesc: String get() = text(Res.string.backup_import_csv_desc)
    override val backupPassword: String get() = text(Res.string.backup_password)
    override val backupPasswordConfirm: String get() = text(Res.string.backup_password_confirm)
    override val backupPasswordMismatch: String get() = text(Res.string.backup_password_mismatch)
    override val backupPasswordShort: String get() = text(Res.string.backup_password_short)
    override val backupExportHint: String get() = text(Res.string.backup_export_hint)
    override val backupRestoreHint: String get() = text(Res.string.backup_restore_hint)
    override val backupSaved: String get() = text(Res.string.backup_saved)
    override val backupWrongPassword: String get() = text(Res.string.backup_wrong_password)
    override val backupBadFile: String get() = text(Res.string.backup_bad_file)
    override fun backupResult(added: String, updated: String): String = text(Res.string.backup_result, added, updated)
    override val backupNothing: String get() = text(Res.string.backup_nothing)
    override val backupFileType: String get() = text(Res.string.backup_file_type)
    override val authRestore: String get() = text(Res.string.auth_restore)
    override fun settingsBasedOn(core: String): String = text(Res.string.settings_based_on, core)
    override val settingsAdvancedTitle: String get() = text(Res.string.settings_advanced_title)
    override val anisetteTitle: String get() = text(Res.string.anisette_title)
    override val anisetteDesc: String get() = text(Res.string.anisette_desc)
    override val anisettePlaceholder: String get() = text(Res.string.anisette_placeholder)
    override val anisetteDefault: String get() = text(Res.string.anisette_default)
    override val anisetteInvalid: String get() = text(Res.string.anisette_invalid)
    override val anisetteNote: String get() = text(Res.string.anisette_note)
    override val websitesTitle: String get() = text(Res.string.websites_title)
    override val websitesPlaceholder: String get() = text(Res.string.websites_placeholder)
    override val websitesAdd: String get() = text(Res.string.websites_add)
    override val totpKeyPlaceholder: String get() = text(Res.string.totp_key_placeholder)
    override val totpKeyConfirm: String get() = text(Res.string.totp_key_confirm)

    override val authSignInTitle: String get() = text(Res.string.auth_sign_in_title)
    override val authSignInHint: String get() = text(Res.string.auth_sign_in_hint)
    override val authEmailPlaceholder: String get() = text(Res.string.auth_email_placeholder)
    override val authPasswordPlaceholder: String get() = text(Res.string.auth_password_placeholder)
    override val authSignInAction: String get() = text(Res.string.auth_sign_in_action)
    override val authPrivacyNotice: String get() = text(Res.string.auth_privacy_notice)
    override val authPrivacyLink: String get() = text(Res.string.auth_privacy_link)
    override val authTwoFactorTitle: String get() = text(Res.string.auth_2fa_title)
    override val authTwoFactorHint: String get() = text(Res.string.auth_2fa_hint)
    override val authTwoFactorResend: String get() = text(Res.string.auth_2fa_resend_action)
    override val authDeviceSelectionTitle: String get() = text(Res.string.auth_device_selection_title)
    override val authDeviceSelectionHint: String get() = text(Res.string.auth_device_selection_hint)
    override val authDevicePasscodeTitle: String get() = text(Res.string.auth_device_passcode_title)
    override val authDevicePasscodePlaceholder: String
        get() = text(Res.string.auth_device_passcode_placeholder)
    override val authContinueAction: String get() = text(Res.string.auth_continue_action)
    override val authMasterSetupTitle: String get() = text(Res.string.auth_master_setup_title)
    override val authMasterSetupHint: String get() = text(Res.string.auth_master_setup_hint)
    override val authMasterPlaceholder: String get() = text(Res.string.auth_master_placeholder)
    override val authMasterRepeatPlaceholder: String
        get() = text(Res.string.auth_master_repeat_placeholder)
    override val authMasterMismatch: String get() = text(Res.string.auth_master_mismatch)
    override val authUnlockTitle: String get() = text(Res.string.auth_unlock_title)
    override val authUnlockHint: String get() = text(Res.string.auth_unlock_hint)
    override val authUnlockAction: String get() = text(Res.string.auth_unlock_action)

    override val busySigningInTitle: String get() = text(Res.string.busy_signing_in_title)
    override val busyCheckingCredentials: String get() = text(Res.string.busy_checking_credentials)
    override val busyOpeningVaultTitle: String get() = text(Res.string.busy_opening_vault_title)
    override val busyOpeningVault: String get() = text(Res.string.busy_opening_vault)
    override val busySendingCode: String get() = text(Res.string.busy_sending_code)
    override val busyVerifyingCode: String get() = text(Res.string.busy_verifying_code)
    override val busyVerifyingPasscode: String get() = text(Res.string.busy_verifying_passcode)
    override val busyCreatingVaultTitle: String get() = text(Res.string.busy_creating_vault_title)
    override val busyCreatingVault: String get() = text(Res.string.busy_creating_vault)

    override val strengthWeak: String get() = text(Res.string.strength_weak)
    override val strengthFair: String get() = text(Res.string.strength_fair)
    override val strengthStrong: String get() = text(Res.string.strength_strong)

    override val failureGeneric: String get() = text(Res.string.failure_generic)
    override val trayOpen: String get() = text(Res.string.tray_open)
    override val trayQuit: String get() = text(Res.string.tray_quit)
    override val photoFilter: String get() = text(Res.string.photo_filter)
    override val settingsSyncTitle: String get() = text(Res.string.settings_sync_title)
    override val authLocalAction: String get() = text(Res.string.auth_local_action)
    override val authLocalTitle: String get() = text(Res.string.auth_local_title)
    override val authLocalHint: String get() = text(Res.string.auth_local_hint)
    override val authLocalNew: String get() = text(Res.string.auth_local_new)
    override val authLocalNewDesc: String get() = text(Res.string.auth_local_new_desc)
    override val authLocalJoin: String get() = text(Res.string.auth_local_join)
    override val authLocalJoinDesc: String get() = text(Res.string.auth_local_join_desc)
    override val authProfileTitle: String get() = text(Res.string.auth_profile_title)
    override val authProfileHint: String get() = text(Res.string.auth_profile_hint)
    override val profileName: String get() = text(Res.string.profile_name)
    override val profilePhotoPick: String get() = text(Res.string.profile_photo_pick)
    override val profilePhotoRemove: String get() = text(Res.string.profile_photo_remove)
    override val profileEditTitle: String get() = text(Res.string.profile_edit_title)
    override val authJoinTitle: String get() = text(Res.string.auth_join_title)
    override val authJoinSkip: String get() = text(Res.string.auth_join_skip)
    override val pairJoinHint: String get() = text(Res.string.pair_join_hint)
    override val pairJoinManualHint: String get() = text(Res.string.pair_join_manual_hint)
    override val pairScan: String get() = text(Res.string.pair_scan)
    override val pairCodeLabel: String get() = text(Res.string.pair_code_label)
    override val pairJoinAction: String get() = text(Res.string.pair_join_action)
    override val pairHostHint: String get() = text(Res.string.pair_host_hint)
    override val pairWaiting: String get() = text(Res.string.pair_waiting)
    override val pairRetry: String get() = text(Res.string.pair_retry)
    override val pairClose: String get() = text(Res.string.pair_close)
    override val busyJoiningTitle: String get() = text(Res.string.busy_joining_title)
    override val busyJoining: String get() = text(Res.string.busy_joining)
    override val devicesTitle: String get() = text(Res.string.devices_title)
    override val devicesIntro: String get() = text(Res.string.devices_intro)
    override val devicesThis: String get() = text(Res.string.devices_this)
    override val devicesNever: String get() = text(Res.string.devices_never)
    override val devicesAdd: String get() = text(Res.string.devices_add)
    override val devicesJoin: String get() = text(Res.string.devices_join)
    override val devicesSyncNow: String get() = text(Res.string.devices_sync_now)
    override val devicesSyncDone: String get() = text(Res.string.devices_sync_done)
    override val devicesSyncFailed: String get() = text(Res.string.devices_sync_failed)
    override val devicesRemove: String get() = text(Res.string.devices_remove)
    override val devicesRemoveDesc: String get() = text(Res.string.devices_remove_desc)
    override val devicesFooter: String get() = text(Res.string.devices_footer)
    override val settingsLocalVault: String get() = text(Res.string.settings_local_vault)
    override val settingsDeleteVault: String get() = text(Res.string.settings_delete_vault)
    override val settingsDeleteVaultTitle: String get() = text(Res.string.settings_delete_vault_title)
    override val settingsDeleteVaultDesc: String get() = text(Res.string.settings_delete_vault_desc)
    override val settingsDeleteVaultNote: String get() = text(Res.string.settings_delete_vault_note)
    override val failurePairRefused: String get() = text(Res.string.failure_pair_refused)
    override val failurePairMissing: String get() = text(Res.string.failure_pair_missing)
    override val failureNoNetwork: String get() = text(Res.string.failure_no_network)
    override val failureBadPhoto: String get() = text(Res.string.failure_bad_photo)
    override val failureBadCredentials: String get() = text(Res.string.failure_bad_credentials)
    override val failureAccountLocked: String get() = text(Res.string.failure_account_locked)
    override val failureWrongPasscode: String get() = text(Res.string.failure_wrong_passcode)
    override val failureWrongMasterPassword: String
        get() = text(Res.string.failure_wrong_master_password)
    override val failureNoDevices: String get() = text(Res.string.failure_no_devices)

    override fun websitesDesc(title: String): String = text(Res.string.websites_desc, title)

    override fun totpSetupDesc(site: String): String = text(Res.string.totp_setup_desc, site)

    override fun authDevicePasscodeHint(device: String): String =
        text(Res.string.auth_device_passcode_hint, device)

    override fun authMasterTooShort(count: Int): String =
        many(Res.plurals.auth_master_too_short, count, count)

    override fun fieldWebsiteMore(domain: String, count: Int): String =
        many(Res.plurals.field_website_more, count, domain, count)

    override fun fieldPasskeyCreationDate(date: String): String =
        text(Res.string.field_passkey_creation_date, date)

    override fun deleteTitle(count: Int): String = many(Res.plurals.delete_title, count, count)

    override fun deleteSoftDesc(count: Int): String = many(Res.plurals.delete_soft_desc, count, count)

    override fun deleteHardDesc(count: Int): String = many(Res.plurals.delete_hard_desc, count, count)

    override fun deleteConfirm(count: Int): String = many(Res.plurals.delete_confirm, count, count)

    override fun deletedCountdown(count: Int): String = many(Res.plurals.deleted_countdown, count, count)

    override fun listSelectedCount(count: Int): String = many(Res.plurals.list_selected_count, count, count)

    override fun zoneItemCount(count: Int): String = many(Res.plurals.zone_item_count, count, count)

    override fun searchPlaceholderIn(zone: String): String =
        text(Res.string.search_placeholder_in, zone)

    override fun searchEmpty(query: String): String = text(Res.string.search_empty, query)

    override fun securityChangeDesc(domain: String): String =
        text(Res.string.security_change_desc, domain)

    override fun settingsAutoLockMinutes(count: Int): String =
        many(Res.plurals.settings_auto_lock_minutes, count, count)

    override fun settingsPending(count: Int): String = many(Res.plurals.settings_pending, count, count)
    override fun namesFound(count: Int): String = many(Res.plurals.names_found, count, count)
    override fun pairDone(name: String): String = text(Res.string.pair_done, name)
    override fun devicesCount(count: Int): String = many(Res.plurals.devices_count, count, count)
    override fun devicesSynced(time: String): String = text(Res.string.devices_synced, time)
    override fun devicesRemoveTitle(name: String): String = text(Res.string.devices_remove_title, name)
    override fun settingsPwnedLast(time: String): String = text(Res.string.settings_pwned_last, time)
    override fun pwnedFound(count: Int): String = many(Res.plurals.pwned_found, count, count)

    override fun relativeTime(value: RvRelative): String = when (value.unit) {
        RvRelativeUnit.Now -> text(Res.string.relative_now)
        RvRelativeUnit.Minute -> many(Res.plurals.relative_minutes, value.count, value.count)
        RvRelativeUnit.Hour -> many(Res.plurals.relative_hours, value.count, value.count)
        RvRelativeUnit.Day -> many(Res.plurals.relative_days, value.count, value.count)
        RvRelativeUnit.Month -> many(Res.plurals.relative_months, value.count, value.count)
        RvRelativeUnit.Year -> many(Res.plurals.relative_years, value.count, value.count)
    }

    override fun zoneText(kind: RvZoneKind, empty: Boolean): RvZoneText = when (kind) {
        RvZoneKind.All -> RvZoneText(
            name = text(Res.string.zone_all_name),
            emptyTitle = text(if (empty) Res.string.zone_all_empty else Res.string.zone_all_no_selection),
            emptyDescription = text(Res.string.zone_all_desc),
        )

        RvZoneKind.Passkeys -> RvZoneText(
            name = text(Res.string.zone_passkeys_name),
            emptyTitle = text(if (empty) Res.string.zone_passkeys_empty else Res.string.zone_passkeys_no_selection),
            emptyDescription = text(Res.string.zone_passkeys_desc),
        )

        RvZoneKind.Codes -> RvZoneText(
            name = text(Res.string.zone_codes_name),
            emptyTitle = text(if (empty) Res.string.zone_codes_empty else Res.string.zone_codes_no_selection),
            emptyDescription = text(Res.string.zone_codes_desc),
        )

        RvZoneKind.Networks -> RvZoneText(
            name = text(Res.string.zone_networks_name),
            emptyTitle = text(if (empty) Res.string.zone_networks_empty else Res.string.zone_networks_no_selection),
            emptyDescription = text(Res.string.zone_networks_desc),
        )

        RvZoneKind.Security -> RvZoneText(
            name = text(Res.string.zone_security_name),
            emptyTitle = text(if (empty) Res.string.zone_security_empty else Res.string.zone_security_no_selection),
            emptyDescription = text(Res.string.zone_security_desc),
        )

        RvZoneKind.Deleted -> RvZoneText(
            name = text(Res.string.zone_deleted_name),
            emptyTitle = text(if (empty) Res.string.zone_deleted_empty else Res.string.zone_deleted_no_selection),
            emptyDescription = text(Res.string.zone_deleted_desc),
        )
    }
}
